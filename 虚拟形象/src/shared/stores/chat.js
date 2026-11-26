import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { useAvatarStore } from '@/shared/stores/avatar'
import { useVoiceStore } from '@/shared/stores/voice'
import { useUiStore } from '@/shared/stores/ui'
import { enqueueSentence, stopSpeech } from '@/features/voice/speechQueue'
import {
  createClientMessageId,
  createSession as createSessionApi,
  deleteSession as deleteSessionApi,
  listMessages,
  listSessions,
  streamMessage,
  updateSession,
  cancelRun
} from '@/shared/api/agent'

function nowText() {
  const date = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate())
  ].join('-') + ` ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function isAbortError(error) {
  return Boolean(error && (error.name === 'AbortError' || error.code === 20))
}

function parseEmotionPayload(raw) {
  if (!raw) return null
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    return typeof parsed?.emotion === 'string' ? parsed.emotion : null
  } catch {
    return null
  }
}

function toUiMessage(dto) {
  const message = {
    ...dto,
    emotion: parseEmotionPayload(dto.emotion),
    clientKey: `srv-${dto.id}`,
    streaming: false,
    tools: [],
    speechSegments: [],
    error: ''
  }
  // 中断/失败时后端可能只存了空内容，补一句可读占位，避免空气泡
  if (message.senderRole === 'assistant' && !message.content) {
    if (message.status === 3) message.content = '（已停止生成）'
    else if (message.status === 2) message.content = '（生成失败，请重新发送）'
  }
  return message
}

/**
 * Agent 会话 store
 *
 * 约定：
 * - messages 只保存当前会话；切换会话时重新拉取，避免把 A 会话的消息渲染到 B；
 * - 流式事件带序号（streamSeq），取消/切换后到达的旧事件直接丢弃；
 * - runId 即 assistant 消息 id，用于后端中断接口。
 */
export const useChatStore = defineStore('chat', () => {
  const avatarStore = useAvatarStore()
  const voiceStore = useVoiceStore()
  const uiStore = useUiStore()
  const ownerKey = ref('')
  const sessions = ref([])
  const sessionsTotal = ref(0)
  const sessionsLoading = ref(false)
  const sessionsLoaded = ref(false)

  const currentSessionId = ref(null)
  const messages = ref([])
  const messagesLoading = ref(false)
  // 输入草稿放在 store 里，面板与聊天页切换时不会丢失
  const draft = ref('')

  function setDraft(value) {
    draft.value = String(value ?? '')
  }

  // 左侧面板只负责输入；角色上方只显示最新一条助手回复气泡
  const REPLY_TEXT_LIMIT = 2000
  let replySyncTimer = null

  function latestAssistantMessage() {
    for (let index = messages.value.length - 1; index >= 0; index -= 1) {
      const message = messages.value[index]
      if (message.senderRole === 'assistant') return message
    }
    return null
  }

  function syncReplyBubble() {
    if (replySyncTimer) {
      clearTimeout(replySyncTimer)
      replySyncTimer = null
    }
    if (!uiStore.panelMode) return
    const message = latestAssistantMessage()
    window.desktopBridge?.sendChatReply?.({
      text: message ? String(message.content || '').slice(0, REPLY_TEXT_LIMIT) : '',
      pending: Boolean(message && message.streaming),
      error: message ? String(message.error || '') : '',
      status: message ? Number(message.status) || 0 : 0
    })
  }

  function scheduleReplySync() {
    if (!uiStore.panelMode) return
    if (replySyncTimer) clearTimeout(replySyncTimer)
    replySyncTimer = setTimeout(syncReplyBubble, 150)
  }

  // 只关心最新助手消息；msg 变成响应式代理后，delta 也会触发这里
  watch(
    () => {
      const message = latestAssistantMessage()
      if (!message) return 'none'
      const contentLength = String(message.content || '').length
      return `${message.clientKey}:${contentLength}:${message.streaming ? 1 : 0}:${message.status}:${message.error || ''}`
    },
    () => scheduleReplySync()
  )

  watch(
    () => uiStore.panelMode,
    (value) => {
      if (value) syncReplyBubble()
      else window.desktopBridge?.hideReplyBubble?.()
    }
  )
  const loadingMore = ref(false)
  const nextCursor = ref(null)
  const hasMore = ref(false)

  const streaming = ref(false)
  const runId = ref(null)
  const streamError = ref('')

  const currentSession = computed(
    () => sessions.value.find((item) => item.id === currentSessionId.value) || null
  )
  const isEmpty = computed(
    () => !messagesLoading.value && messages.value.length === 0
  )

  let abortController = null
  let streamSeq = 0
  let messageSeq = 0

  function reset() {
    streamSeq++
    messageSeq++
    stopSpeech()
    if (abortController) {
      try {
        abortController.abort()
      } catch {
        // 忽略
      }
      abortController = null
    }
    ownerKey.value = ''
    sessions.value = []
    sessionsTotal.value = 0
    sessionsLoaded.value = false
    sessionsLoading.value = false
    currentSessionId.value = null
    messages.value = []
    messagesLoading.value = false
    loadingMore.value = false
    nextCursor.value = null
    hasMore.value = false
    streaming.value = false
    runId.value = null
    streamError.value = ''
    draft.value = ''
    if (replySyncTimer) {
      clearTimeout(replySyncTimer)
      replySyncTimer = null
    }
    avatarStore.reset()
  }

  /**
   * 登录用户切换时清空旧数据，避免串号。
   * @param {string} key 当前用户唯一标识（username）
   */
  async function initialize(key) {
    if (key !== ownerKey.value) {
      reset()
      ownerKey.value = key || ''
    }
    if (!sessionsLoaded.value) {
      await loadSessions()
      return
    }
    if (!currentSessionId.value && sessions.value.length) {
      await selectSession(sessions.value[0].id)
    }
  }

  async function loadSessions({ silent = false } = {}) {
    if (!silent) sessionsLoading.value = true
    try {
      const data = await listSessions({ page: 1, size: 50 })
      sessions.value = (data && data.records) || []
      sessionsTotal.value = Number(data && data.total) || 0
      sessionsLoaded.value = true

      const exists = sessions.value.some((item) => item.id === currentSessionId.value)
      if (!exists) {
        currentSessionId.value = null
        messages.value = []
        nextCursor.value = null
        hasMore.value = false
        if (sessions.value.length) {
          await selectSession(sessions.value[0].id)
        }
      }
    } catch (error) {
      if (!error.isAuthError) {
        ElMessage.error(error.message || '加载会话失败')
      }
    } finally {
      sessionsLoading.value = false
    }
  }

  async function createSession(payload = {}) {
    try {
      // 兼容旧调用 createSession(title) 和新的 createSession({ title, personaCode })
      const { title, personaCode } =
        typeof payload === 'string' ? { title: payload } : payload
      const body = {}
      if (title) body.title = title
      if (personaCode) body.personaCode = personaCode
      const data = await createSessionApi(body)
      if (!data) return null
      sessions.value = [data, ...sessions.value]
      sessionsTotal.value += 1
      sessionsLoaded.value = true
      await selectSession(data.id)
      return data
    } catch (error) {
      if (!error.isAuthError) {
        ElMessage.error(error.message || '新建会话失败')
      }
      return null
    }
  }

  async function selectSession(sessionId) {
    if (!sessionId) {
      currentSessionId.value = null
      messages.value = []
      nextCursor.value = null
      hasMore.value = false
      return
    }
    if (sessionId === currentSessionId.value && messages.value.length) return

    // 切换会话前停止当前生成/朗读，避免旧会话的声音串到新会话
    if (streaming.value) stopStreaming()
    stopSpeech()

    currentSessionId.value = sessionId
    messages.value = []
    nextCursor.value = null
    hasMore.value = false
    streamError.value = ''
    await loadMessages()
  }

  async function loadMessages() {
    const sessionId = currentSessionId.value
    if (!sessionId) return
    const seq = ++messageSeq
    messagesLoading.value = true
    try {
      const data = await listMessages(sessionId, { size: 30 })
      if (seq !== messageSeq || sessionId !== currentSessionId.value) return
      const list = ((data && data.records) || []).map(toUiMessage)
      messages.value = list
      nextCursor.value = (data && data.nextCursor) || null
      hasMore.value = Boolean(data && data.hasMore)
      // 恢复最近一次助手情绪，避免刷新后形象回到默认脸
      const lastEmotion = [...list]
        .reverse()
        .find((item) => item.senderRole === 'assistant' && item.emotion)?.emotion
      if (lastEmotion) avatarStore.setEmotion(lastEmotion)
    } catch (error) {
      if (seq !== messageSeq) return
      if (!error.isAuthError) {
        ElMessage.error(error.message || '加载聊天记录失败')
      }
    } finally {
      if (seq === messageSeq) messagesLoading.value = false
    }
  }

  async function loadOlderMessages() {
    const sessionId = currentSessionId.value
    if (!sessionId || loadingMore.value || !hasMore.value || nextCursor.value == null) return
    const seq = messageSeq
    loadingMore.value = true
    try {
      const data = await listMessages(sessionId, {
        cursor: nextCursor.value,
        size: 30
      })
      if (seq !== messageSeq || sessionId !== currentSessionId.value) return
      const older = ((data && data.records) || []).map(toUiMessage)
      messages.value = [...older, ...messages.value]
      nextCursor.value = (data && data.nextCursor) || null
      hasMore.value = Boolean(data && data.hasMore)
    } catch (error) {
      if (!error.isAuthError) {
        ElMessage.error(error.message || '加载更早消息失败')
      }
    } finally {
      loadingMore.value = false
    }
  }

  function touchSession(sessionId, delta) {
    const index = sessions.value.findIndex((item) => item.id === sessionId)
    if (index === -1) return
    const session = sessions.value[index]
    const next = {
      ...session,
      lastActiveAt: nowText(),
      messageCount: Math.max(0, (Number(session.messageCount) || 0) + delta)
    }
    sessions.value.splice(index, 1)
    sessions.value.unshift(next)
  }

  function handleAct(message, payload) {
    if (!payload) return
    if (!Array.isArray(message.acts)) message.acts = []
    message.acts.push(payload)
    if (payload.emotion) {
      message.emotion = payload.emotion
    }

    // 语音开启时，动作交给播放队列在对应句子开始时执行；
    // 但如果这句话的 speech 已经先到过（尾随 act），队列不会再取它，立即执行兜底。
    const voiceWillSpeak = voiceStore.enabled && voiceStore.available
    const sentenceAlreadyQueued = (message.speechSegments || [])
      .some((segment) => segment.index === Number(payload.sentenceIndex))
    if (voiceWillSpeak && !sentenceAlreadyQueued) return

    if (payload.emotion) {
      avatarStore.playEmotion(payload.emotion, 2400)
    }
    if (payload.motion) {
      avatarStore.playMotion(payload.motion, payload.intensity ?? 0.8)
    }
  }

  function handleSpeech(message, payload) {
    if (!payload || !payload.text) return
    const index = Number(payload.index) || 0
    if (!Array.isArray(message.speechSegments)) message.speechSegments = []
    const segment = {
      index,
      text: payload.text,
      emotion: payload.emotion || null,
      acts: (message.acts || []).filter((act) => act.sentenceIndex === index)
    }
    message.speechSegments.push(segment)

    // 悬浮球底部歌词：只在悬浮球形态显示；面板模式由上方聊天记录窗口展示
    if (!uiStore.panelMode) {
      window.desktopBridge?.sendBallLyric?.({
        text: payload.text,
        index,
        emotion: segment.emotion || undefined
      })
    }
    message.lyricSent = true

    if (voiceStore.enabled && voiceStore.available) {
      enqueueSentence({
        index,
        text: payload.text,
        emotion: segment.emotion,
        acts: segment.acts,
        speaker: voiceStore.speaker || undefined
      })
    }
  }

  function handleStreamEvent(seq, assistantMessage, event, data) {
    if (seq !== streamSeq) return
    const payload = data || {}
    switch (event) {
      case 'meta':
        runId.value = payload.runId ?? null
        assistantMessage.lyricSent = false
        window.desktopBridge?.clearBallLyrics?.()
        if (payload.assistantMessageId != null) {
          assistantMessage.id = payload.assistantMessageId
        }
        break
      case 'delta':
        if (payload.text) assistantMessage.content += payload.text
        break
      case 'act':
        handleAct(assistantMessage, payload)
        break
      case 'speech':
        handleSpeech(assistantMessage, payload)
        break
      case 'tool_start':
        assistantMessage.tools.push({
          callId: payload.callId,
          tool: payload.tool,
          pending: true,
          ok: null,
          summary: ''
        })
        break
      case 'tool_end': {
        const tool = assistantMessage.tools.find((item) => item.callId === payload.callId)
        if (tool) {
          tool.pending = false
          tool.ok = Boolean(payload.ok)
          tool.summary = payload.summary || ''
        }
        break
      }
      case 'done': {
        assistantMessage.streaming = false
        assistantMessage.status = payload.finishReason === 'cancelled' ? 3 : 1
        if (payload.usage && payload.usage.completionTokens != null) {
          assistantMessage.tokens = payload.usage.completionTokens
        }
        const finalEmotion = parseEmotionPayload(payload.emotion)
        if (finalEmotion) {
          assistantMessage.emotion = finalEmotion
          avatarStore.setEmotion(finalEmotion)
        }
        streaming.value = false
        runId.value = null
        // 表演标签关闭时没有 speech 事件，用最终回答兜底一句字幕
        if (!uiStore.panelMode && !assistantMessage.lyricSent && assistantMessage.content) {
          const text = assistantMessage.content.replace(/\s+/g, ' ').trim()
          if (text) {
            window.desktopBridge?.sendBallLyric?.({
              text: text.slice(-120),
              index: 0,
              final: true
            })
          }
        }
        break
      }
      case 'error':
        assistantMessage.streaming = false
        assistantMessage.status = 2
        assistantMessage.error = payload.msg || '回答生成失败，请重试'
        streamError.value = assistantMessage.error
        streaming.value = false
        runId.value = null
        window.desktopBridge?.clearBallLyrics?.()
        if (uiStore.panelMode) {
          window.desktopBridge?.clearBallBubble?.()
        }
        stopSpeech()
        break
      default:
        break
    }
  }

  /**
   * 发送消息。没有当前会话时自动新建一个。
   * @param {string} rawContent
   */
  async function sendMessage(rawContent) {
    const content = String(rawContent || '').trim()
    if (!content || streaming.value) return false

    // 新消息视为打断上一轮朗读
    stopSpeech()

    if (!currentSessionId.value) {
      const created = await createSession()
      if (!created) return false
    }

    const sessionId = currentSessionId.value
    const clientMessageId = createClientMessageId()
    const userMessage = {
      clientKey: `local-user-${clientMessageId}`,
      senderRole: 'user',
      content,
      contentType: 'text',
      status: 1,
      createdAt: nowText(),
      streaming: false,
      tools: [],
      error: ''
    }
    const assistantDraft = {
      clientKey: `local-assistant-${clientMessageId}`,
      senderRole: 'assistant',
      content: '',
      contentType: 'text',
      status: 0,
      createdAt: nowText(),
      streaming: true,
      tools: [],
      speechSegments: [],
      acts: [],
      emotion: null,
      error: '',
      model: ''
    }

    messages.value.push(userMessage, assistantDraft)
    // 必须通过响应式代理更新，否则流式内容不会触发 watcher / 气泡同步
    const assistantMessage = messages.value[messages.value.length - 1]
    touchSession(sessionId, 2)

    const seq = ++streamSeq
    streaming.value = true
    streamError.value = ''
    runId.value = null
    abortController = new AbortController()

    try {
      await streamMessage({
        sessionId,
        content,
        clientMessageId,
        signal: abortController.signal,
        onEvent: (event, data) => handleStreamEvent(seq, assistantMessage, event, data)
      })

      // 流正常结束但没有收到 done/error：按已生成内容收尾
      if (seq === streamSeq && assistantMessage.streaming) {
        assistantMessage.streaming = false
        assistantMessage.status = assistantMessage.content ? 1 : 2
        if (!assistantMessage.content) {
          assistantMessage.error = '回答生成中断，请重试'
        }
        streaming.value = false
        runId.value = null
      }
    } catch (error) {
      if (isAbortError(error)) {
        if (seq === streamSeq) {
          assistantMessage.streaming = false
          if (assistantMessage.status === 0) assistantMessage.status = 3
          streaming.value = false
          runId.value = null
        }
      } else if (seq === streamSeq) {
        assistantMessage.streaming = false
        assistantMessage.status = 2
        assistantMessage.error = error.message || '回答生成失败，请重试'
        streamError.value = assistantMessage.error
        streaming.value = false
        runId.value = null
        if (!error.isAuthError) {
          ElMessage.error(assistantMessage.error)
        }
      }
    } finally {
      if (seq === streamSeq) {
        streaming.value = false
        abortController = null
        // 后台同步一次会话排序与消息数，失败不影响当前页面
        loadSessions({ silent: true })
      }
      // 兜底：done/error/中断/异常都完成后强制同步一次最终状态
      syncReplyBubble()
    }
    return true
  }

  /**
   * 停止生成：立即中断前端流，并通知后端取消本轮 run。
   */
  function stopStreaming() {
    if (!streaming.value) return
    const activeRunId = runId.value
    streamSeq++
    if (abortController) {
      try {
        abortController.abort()
      } catch {
        // 忽略
      }
      abortController = null
    }
    const activeMessage = messages.value.find((item) => item.streaming)
    if (activeMessage) {
      activeMessage.streaming = false
      if (activeMessage.status === 0) activeMessage.status = 3
      if (!activeMessage.content) {
        activeMessage.content = '（已停止生成）'
      }
    }
    streaming.value = false
    runId.value = null
    stopSpeech()
    syncReplyBubble()
    if (activeRunId != null) {
      cancelRun(activeRunId).catch(() => {
        // 后端可能已结束本轮，忽略
      })
    }
  }

  async function renameSession(sessionId, title) {
    const nextTitle = String(title || '').trim()
    if (!nextTitle) return false
    try {
      const data = await updateSession(sessionId, { title: nextTitle })
      const index = sessions.value.findIndex((item) => item.id === sessionId)
      if (index !== -1 && data) sessions.value[index] = { ...sessions.value[index], ...data }
      return true
    } catch (error) {
      if (!error.isAuthError) ElMessage.error(error.message || '重命名失败')
      return false
    }
  }

  async function archiveSession(sessionId) {
    try {
      await updateSession(sessionId, { status: 0 })
      removeLocalSession(sessionId)
      ElMessage.success('会话已归档')
      return true
    } catch (error) {
      if (!error.isAuthError) ElMessage.error(error.message || '归档失败')
      return false
    }
  }

  async function removeSession(sessionId) {
    try {
      await deleteSessionApi(sessionId)
      removeLocalSession(sessionId)
      ElMessage.success('会话已删除')
      return true
    } catch (error) {
      if (!error.isAuthError) ElMessage.error(error.message || '删除失败')
      return false
    }
  }

  function removeLocalSession(sessionId) {
    const index = sessions.value.findIndex((item) => item.id === sessionId)
    if (index !== -1) {
      sessions.value.splice(index, 1)
      sessionsTotal.value = Math.max(0, sessionsTotal.value - 1)
    }
    if (currentSessionId.value !== sessionId) return

    if (streaming.value) stopStreaming()
    currentSessionId.value = null
    messages.value = []
    nextCursor.value = null
    hasMore.value = false
    if (sessions.value.length) {
      selectSession(sessions.value[0].id)
    }
  }

  return {
    ownerKey,
    sessions,
    sessionsTotal,
    sessionsLoading,
    sessionsLoaded,
    currentSessionId,
    currentSession,
    messages,
    messagesLoading,
    draft,
    setDraft,
    loadingMore,
    nextCursor,
    hasMore,
    streaming,
    runId,
    streamError,
    isEmpty,
    initialize,
    reset,
    loadSessions,
    createSession,
    selectSession,
    loadMessages,
    loadOlderMessages,
    sendMessage,
    stopStreaming,
    renameSession,
    archiveSession,
    removeSession
  }
})
