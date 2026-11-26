<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeftBold,
  ArrowRightBold,
  ChatDotRound,
  CircleCheck,
  Delete,
  EditPen,
  FolderRemove,
  Headset,
  Loading,
  MoreFilled,
  Mute,
  Plus,
  User,
  Warning
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import StageHost from '@/features/avatar/components/StageHost.vue'
import ChatComposer from '@/features/chat/components/ChatComposer.vue'
import { ASSET_BASE_URL } from '@/shared/api/config'
import { useMediaQuery } from '@/shared/composables/useMediaQuery'
import { useAuthStore } from '@/shared/stores/auth'
import { useAvatarStore } from '@/shared/stores/avatar'
import { useChatStore } from '@/shared/stores/chat'
import { useVoiceStore } from '@/shared/stores/voice'
import { getCurrentPersona, getPersonaOptions } from '@/shared/api/persona'

marked.setOptions({ gfm: true, breaks: true })

const authStore = useAuthStore()
const avatarStore = useAvatarStore()
const chat = useChatStore()
const voiceStore = useVoiceStore()

const stageTagText = computed(() => {
  const driver = avatarStore.avatarConfig?.driver || 'pixel'
  if (driver === 'live2d') return 'Live2D'
  if (driver === 'image') return '静态立绘'
  return '像素'
})
const stageTagType = computed(() => {
  const driver = avatarStore.avatarConfig?.driver || 'pixel'
  if (driver === 'live2d') return 'primary'
  if (driver === 'image') return 'success'
  return 'info'
})

function assetUrl(path) {
  if (!path) return ''
  return /^https?:\/\//.test(path) ? path : `${ASSET_BASE_URL}${path}`
}

const aiAvatarUrl = computed(() => assetUrl(authStore.userInfo.aiAvatar))
const userAvatarUrl = computed(() => assetUrl(authStore.userInfo.avatar))

const composerRef = ref(null)
const scrollRef = ref(null)
const stickToBottom = ref(true)

// 两侧面板的收起状态：记住上次选择，刷新后保持
const SESSIONS_COLLAPSED_KEY = 'ui:chat:sessionsCollapsed'
const STAGE_COLLAPSED_KEY = 'ui:chat:stageCollapsed'
const sessionsCollapsed = ref(localStorage.getItem(SESSIONS_COLLAPSED_KEY) === '1')
const stageCollapsed = ref(localStorage.getItem(STAGE_COLLAPSED_KEY) === '1')

// 窄屏：会话列表改为抽屉，形象舞台改为底部浮层
const { matches: isMobile } = useMediaQuery('(max-width: 900px)')
const mobileSessionsOpen = ref(false)
const mobileStageOpen = ref(false)

watch(isMobile, (mobile) => {
  if (!mobile) {
    mobileSessionsOpen.value = false
    mobileStageOpen.value = false
  }
})

function toggleSessions() {
  if (isMobile.value) {
    mobileSessionsOpen.value = !mobileSessionsOpen.value
    if (mobileSessionsOpen.value) mobileStageOpen.value = false
    return
  }
  sessionsCollapsed.value = !sessionsCollapsed.value
  localStorage.setItem(SESSIONS_COLLAPSED_KEY, sessionsCollapsed.value ? '1' : '0')
}

function toggleStage() {
  if (isMobile.value) {
    mobileStageOpen.value = !mobileStageOpen.value
    if (mobileStageOpen.value) mobileSessionsOpen.value = false
    return
  }
  stageCollapsed.value = !stageCollapsed.value
  localStorage.setItem(STAGE_COLLAPSED_KEY, stageCollapsed.value ? '1' : '0')
}

const personaOptions = ref([])
const personaDefaultName = ref('默认')
const newSessionDialogVisible = ref(false)
const newSessionTitle = ref('')
const newSessionPersonaCode = ref('default')
const newSessionCreating = ref(false)

const currentPersonaName = computed(() => {
  const code = chat.currentSession?.personaCode
  if (!code || code === 'default') return personaDefaultName.value
  return personaOptions.value.find((item) => item.code === code)?.name || ''
})

const TOOL_LABELS = {
  get_user_profile: '读取用户资料',
  query_tasks: '查询任务',
  query_task_stats: '统计任务',
  search_memories: '检索记忆',
  get_recent_messages: '读取最近消息',
  save_memory: '保存记忆',
  create_task: '创建任务',
  get_intimacy_history: '查询亲密度'
}

function toolLabel(name) {
  return TOOL_LABELS[name] || name || '调用工具'
}

/** 渲染 Markdown，模型输出一律视为不可信内容，先过滤再插入 DOM */
function renderMarkdown(content) {
  if (!content) return ''
  const html = marked.parse(content, { async: false })
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } })
}

/** 只允许 http(s) 链接在新窗口打开，拦截 javascript: 等协议 */
function handleMarkdownClick(event) {
  const anchor = event.target.closest && event.target.closest('a[href]')
  if (!anchor) return
  const href = anchor.getAttribute('href') || ''
  if (!/^https?:\/\//i.test(href)) return
  event.preventDefault()
  window.open(href, '_blank', 'noopener,noreferrer')
}

function toDate(text) {
  if (!text) return null
  const date = new Date(String(text).replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

const pad = (value) => String(value).padStart(2, '0')

function formatSessionTime(text) {
  const date = toDate(text)
  if (!date) return ''
  const now = new Date()
  const diff = (now.getTime() - date.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400 && now.toDateString() === date.toDateString()) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  if (diff < 172800) return '昨天'
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}-${date.getDate()}`
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatMessageTime(text) {
  const date = toDate(text)
  if (!date) return ''
  const now = new Date()
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  if (now.toDateString() === date.toDateString()) return time
  return `${date.getMonth() + 1}-${date.getDate()} ${time}`
}

function focusComposer() {
  nextTick(() => composerRef.value?.focus())
}

async function scrollToBottom(force = false) {
  await nextTick()
  const el = scrollRef.value
  if (!el) return
  if (force || stickToBottom.value) {
    el.scrollTop = el.scrollHeight
  }
}

function handleScroll() {
  const el = scrollRef.value
  if (!el) return
  stickToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function handleNewSession() {
  if (chat.streaming) chat.stopStreaming()
  newSessionTitle.value = ''
  newSessionPersonaCode.value = 'default'
  newSessionDialogVisible.value = true
}

async function confirmNewSession() {
  newSessionCreating.value = true
  try {
    const created = await chat.createSession({
      title: newSessionTitle.value.trim() || undefined,
      personaCode: newSessionPersonaCode.value
    })
    if (created) {
      newSessionDialogVisible.value = false
      focusComposer()
    }
  } finally {
    newSessionCreating.value = false
  }
}

async function loadPersonaOptions() {
  try {
    const [options, current] = await Promise.all([
      getPersonaOptions(),
      getCurrentPersona()
    ])
    personaOptions.value = options || []
    if (current) {
      personaDefaultName.value = current.name || '默认'
    }
  } catch {
    // 人格接口异常不阻塞聊天，回退到默认文案
  }
}

async function handleSelect(session) {
  if (session.id === chat.currentSessionId) {
    if (isMobile.value) mobileSessionsOpen.value = false
    return
  }
  await chat.selectSession(session.id)
  if (isMobile.value) mobileSessionsOpen.value = false
}

async function handleRename(session) {
  if (!session) return
  let value
  try {
    const result = await ElMessageBox.prompt('输入新的会话标题', '重命名会话', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: session.title || '',
      inputValidator: (input) => {
        const text = String(input || '').trim()
        if (!text) return '标题不能为空'
        if (text.length > 120) return '标题不能超过 120 字'
        return true
      }
    })
    value = result.value
  } catch {
    return
  }
  await chat.renameSession(session.id, value)
}

async function handleArchive(session) {
  try {
    await ElMessageBox.confirm(
      `归档「${session.title || '未命名会话'}」？归档后不再出现在列表中。`,
      '归档会话',
      { type: 'warning', confirmButtonText: '归档', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await chat.archiveSession(session.id)
}

async function handleDelete(session) {
  try {
    await ElMessageBox.confirm(
      `删除「${session.title || '未命名会话'}」？聊天记录和工具调用日志会一并删除，且无法恢复。`,
      '删除会话',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await chat.removeSession(session.id)
}

async function handleSessionCommand(command, session) {
  if (command === 'rename') await handleRename(session)
  if (command === 'archive') await handleArchive(session)
  if (command === 'delete') await handleDelete(session)
}

async function handleLoadOlder() {
  const el = scrollRef.value
  const previousHeight = el ? el.scrollHeight : 0
  await chat.loadOlderMessages()
  await nextTick()
  if (el) {
    // 保持视口停在原来的位置，避免加载更早消息后跳动
    el.scrollTop = el.scrollHeight - previousHeight
  }
}

async function toggleVoice() {
  const ok = await voiceStore.toggle()
  if (!ok) {
    ElMessage.warning(voiceStore.loadError || '服务器未配置语音合成')
  }
}

async function handleSent() {
  stickToBottom.value = true
  await scrollToBottom(true)
}

// 流式输出逐 token 变化，用 rAF 合并滚动，避免每个 token 都触发一次滚动
let scrollFrame = 0

function scheduleScrollToBottom(force = false) {
  if (scrollFrame) return
  scrollFrame = requestAnimationFrame(() => {
    scrollFrame = 0
    scrollToBottom(force)
  })
}

onBeforeUnmount(() => {
  if (scrollFrame) cancelAnimationFrame(scrollFrame)
})

watch(
  () => {
    const list = chat.messages
    const last = list[list.length - 1]
    return `${list.length}:${last ? last.content.length : 0}:${last ? last.tools.length : 0}`
  },
  () => scheduleScrollToBottom()
)

watch(
  () => chat.currentSessionId,
  () => scrollToBottom(true)
)

watch(
  () => chat.messagesLoading,
  (loading) => {
    if (!loading) scrollToBottom(true)
  }
)

onMounted(async () => {
  const owner = authStore.userInfo.username || authStore.userInfo.id || 'anonymous'
  await Promise.all([
    chat.initialize(owner),
    loadPersonaOptions(),
    voiceStore.loadConfig(true),
    avatarStore.loadAvatarConfig(true)
  ])
  await scrollToBottom(true)
})
</script>

<template>
  <div
    class="chat-view"
    :class="{
      'sessions-collapsed': sessionsCollapsed,
      'stage-collapsed': stageCollapsed,
      'mobile-sessions-open': mobileSessionsOpen,
      'mobile-stage-open': mobileStageOpen
    }"
  >
    <div
      v-if="isMobile && mobileSessionsOpen"
      class="session-backdrop"
      aria-hidden="true"
      @click="mobileSessionsOpen = false"
    />
    <div
      v-if="isMobile && mobileStageOpen"
      class="stage-backdrop"
      aria-hidden="true"
      @click="mobileStageOpen = false"
    />

    <!-- 左侧：会话列表 -->
    <aside
      class="session-panel"
      :inert="isMobile ? !mobileSessionsOpen : sessionsCollapsed"
      :aria-hidden="isMobile ? !mobileSessionsOpen : sessionsCollapsed"
    >
      <div class="panel-head">
        <span class="panel-title">对话</span>
        <div class="panel-actions">
          <el-tooltip content="新建会话" placement="bottom">
            <el-button
              circle
              size="small"
              :icon="Plus"
              :disabled="chat.streaming"
              @click="handleNewSession"
            />
          </el-tooltip>
          <el-tooltip content="收起会话列表" placement="bottom">
            <el-button text circle size="small" :icon="ArrowLeftBold" @click="toggleSessions" />
          </el-tooltip>
        </div>
      </div>

      <div v-loading="chat.sessionsLoading" class="session-list">
        <el-empty
          v-if="!chat.sessionsLoading && !chat.sessions.length"
          description="还没有会话"
          :image-size="56"
        >
          <el-button size="small" type="primary" @click="handleNewSession">新建会话</el-button>
        </el-empty>

        <div
          v-for="session in chat.sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === chat.currentSessionId }"
          @click="handleSelect(session)"
        >
          <div class="session-main">
            <div class="session-title">{{ session.title || '新的对话' }}</div>
            <div class="session-sub">
              <span>{{ formatSessionTime(session.lastActiveAt) }}</span>
              <span v-if="session.messageCount"> · {{ session.messageCount }} 条</span>
            </div>
          </div>
          <el-dropdown
            trigger="click"
            placement="bottom-end"
            @click.stop
            @command="(command) => handleSessionCommand(command, session)"
          >
            <el-button text circle size="small" :icon="MoreFilled" @click.stop />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename">
                  <el-icon><EditPen /></el-icon>重命名
                </el-dropdown-item>
                <el-dropdown-item command="archive">
                  <el-icon><FolderRemove /></el-icon>归档
                </el-dropdown-item>
                <el-dropdown-item divided command="delete">
                  <el-icon><Delete /></el-icon>删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <div v-if="chat.sessionsTotal > chat.sessions.length" class="session-more">
          仅显示最近 {{ chat.sessions.length }} 个会话，共 {{ chat.sessionsTotal }} 个
        </div>
      </div>
    </aside>

    <!-- 中间：消息流与输入框 -->
    <section class="chat-card">
      <header class="chat-head">
        <div class="chat-head-left">
          <el-button
            v-if="isMobile"
            text
            circle
            size="small"
            :icon="ChatDotRound"
            aria-label="打开会话列表"
            @click="toggleSessions"
          />
          <el-tooltip v-else-if="sessionsCollapsed" content="展开会话列表" placement="bottom">
            <el-button text circle size="small" :icon="ArrowRightBold" @click="toggleSessions" />
          </el-tooltip>
          <div class="chat-head-title">
            <el-icon><ChatDotRound /></el-icon>
            <span class="chat-title-text">{{ chat.currentSession?.title || '新的对话' }}</span>
            <el-tag v-if="chat.streaming" size="small" type="warning" effect="plain">
              生成中
            </el-tag>
            <el-tag v-if="currentPersonaName" size="small" type="info" effect="plain">
              {{ currentPersonaName }}
            </el-tag>
          </div>
        </div>
        <div class="chat-head-actions">
          <el-button
            v-if="isMobile"
            text
            circle
            size="small"
            :icon="User"
            aria-label="查看虚拟形象"
            @click="toggleStage"
          />
          <el-tooltip v-else-if="stageCollapsed" content="展开虚拟形象" placement="bottom">
            <el-button text circle size="small" :icon="ArrowLeftBold" @click="toggleStage" />
          </el-tooltip>
          <el-button
            v-if="chat.currentSession"
            text
            size="small"
            :icon="EditPen"
            class="hide-on-narrow"
            @click="handleRename(chat.currentSession)"
          >
            重命名
          </el-button>
        </div>
      </header>

      <div ref="scrollRef" class="message-area" @scroll="handleScroll">
        <div v-if="chat.hasMore && chat.messages.length" class="load-more">
          <el-button size="small" :loading="chat.loadingMore" @click="handleLoadOlder">
            加载更早消息
          </el-button>
        </div>

        <el-empty
          v-if="chat.isEmpty && !chat.messagesLoading"
          description="和角色聊聊今天的事吧"
        >
          <el-button type="primary" @click="focusComposer">开始输入</el-button>
        </el-empty>

        <div v-else v-loading="chat.messagesLoading" class="message-list">
          <div
            v-for="message in chat.messages"
            :key="message.clientKey"
            class="msg-row"
            :class="message.senderRole"
          >
            <el-avatar
              v-if="message.senderRole === 'assistant'"
              :size="34"
              class="msg-avatar"
              :src="aiAvatarUrl || undefined"
            >
              <span v-if="!aiAvatarUrl">角</span>
            </el-avatar>

            <div class="msg-main">
              <div
                class="msg-bubble"
                :class="{
                  'is-user': message.senderRole === 'user',
                  'is-error': message.senderRole === 'assistant' && message.status === 2
                }"
              >
                <template v-if="message.senderRole === 'user'">
                  <span class="user-text">{{ message.content }}</span>
                </template>

                <template v-else>
                  <div v-if="message.tools.length" class="tool-list">
                    <div
                      v-for="tool in message.tools"
                      :key="tool.callId"
                      class="tool-chip"
                      :class="{
                        pending: tool.pending,
                        failed: !tool.pending && !tool.ok
                      }"
                    >
                      <el-icon v-if="tool.pending" class="is-loading"><Loading /></el-icon>
                      <el-icon v-else-if="tool.ok"><CircleCheck /></el-icon>
                      <el-icon v-else><Warning /></el-icon>
                      <span class="tool-name">
                        {{ tool.pending ? `正在${toolLabel(tool.tool)}…` : `${toolLabel(tool.tool)}${tool.ok ? '完成' : '失败'}` }}
                      </span>
                      <span v-if="!tool.pending && tool.summary" class="tool-summary">
                        {{ tool.summary }}
                      </span>
                    </div>
                  </div>

                  <div
                    v-if="message.content"
                    class="md-body"
                    @click="handleMarkdownClick"
                    v-html="renderMarkdown(message.content)"
                  />
                  <div v-else-if="message.streaming" class="thinking">
                    正在思考<span class="thinking-dots">…</span>
                  </div>

                  <div v-if="message.error" class="msg-error">{{ message.error }}</div>
                </template>
              </div>

              <div class="msg-meta">
                <span>{{ formatMessageTime(message.createdAt) }}</span>
                <span v-if="message.senderRole === 'assistant' && message.status === 3">已停止</span>
                <span
                  v-if="message.senderRole === 'assistant' && message.status === 2"
                  class="meta-error"
                >
                  生成失败
                </span>
                <span v-if="message.tokens">{{ message.tokens }} tokens</span>
              </div>
            </div>

            <el-avatar
              v-if="message.senderRole === 'user'"
              :size="34"
              class="msg-avatar user"
              :src="userAvatarUrl || undefined"
            >
              <span v-if="!userAvatarUrl">{{ authStore.displayName.slice(0, 1) }}</span>
            </el-avatar>
          </div>
        </div>
      </div>

      <ChatComposer ref="composerRef" @sent="handleSent" />
    </section>

    <!-- 右侧：虚拟形象（窄屏下按需挂载为底部浮层） -->
    <section
      v-if="!isMobile || mobileStageOpen"
      class="stage-card"
      :inert="!isMobile && stageCollapsed"
      :aria-hidden="!isMobile && stageCollapsed"
    >
      <header class="stage-head">
        <span class="stage-title">虚拟形象</span>
        <div class="stage-head-actions">
          <span class="stage-tip">点击角色互动</span>
          <el-tag size="small" effect="plain" :type="stageTagType">
            {{ stageTagText }}
          </el-tag>
          <el-tooltip
            :content="voiceStore.available
              ? (voiceStore.enabled ? '关闭语音朗读' : '开启语音朗读')
              : '服务器未配置语音'"
            placement="bottom"
          >
            <el-button
              text
              circle
              size="small"
              :icon="voiceStore.enabled ? Headset : Mute"
              :disabled="!voiceStore.available"
              aria-label="语音朗读"
              @click="toggleVoice"
            />
          </el-tooltip>
          <el-tooltip content="收起虚拟形象" placement="bottom">
            <el-button text circle size="small" :icon="ArrowRightBold" @click="toggleStage" />
          </el-tooltip>
        </div>
      </header>
      <StageHost fill />
    </section>

    <el-dialog
      v-model="newSessionDialogVisible"
      title="新建会话"
      width="min(460px, 92vw)"
      append-to-body
    >
      <el-form label-width="72px">
        <el-form-item label="标题">
          <el-input
            v-model="newSessionTitle"
            maxlength="120"
            placeholder="可选，默认「新的对话」"
          />
        </el-form-item>
        <el-form-item label="人格">
          <el-select v-model="newSessionPersonaCode" style="width: 100%">
            <el-option value="default" label="跟随默认人格" />
            <el-option
              v-for="option in personaOptions"
              :key="option.code"
              :value="option.code"
              :label="option.name"
            />
          </el-select>
          <div class="persona-dialog-hint">
            默认人格在设置页调整；这里只影响本次会话。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newSessionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="newSessionCreating" @click="confirmNewSession">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-view {
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr) clamp(340px, 30vw, 520px);
  gap: 16px;
  height: calc(100vh - 100px);
  min-height: 560px;
  transition: grid-template-columns 0.22s ease;
}

/* 两侧面板收缩：只压缩对应网格列，中间聊天区自动变宽 */
.chat-view.sessions-collapsed {
  grid-template-columns: 0 minmax(0, 1fr) clamp(340px, 30vw, 520px);
}

.chat-view.stage-collapsed {
  grid-template-columns: 270px minmax(0, 1fr) 0;
}

.chat-view.sessions-collapsed.stage-collapsed {
  grid-template-columns: 0 minmax(0, 1fr) 0;
}

.chat-view.sessions-collapsed .session-panel,
.chat-view.stage-collapsed .stage-card {
  opacity: 0;
  pointer-events: none;
  border-color: transparent;
}

/* 左侧会话列表 */
.session-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
  opacity: 1;
  transition: opacity 0.16s ease;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel-title {
  font-weight: 600;
}

.session-list {
  flex: 1;
  min-height: 120px;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.session-item:hover {
  background: var(--el-fill-color-light);
}

.session-item.active {
  background: var(--el-color-primary-light-9);
}

.session-main {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-item.active .session-title {
  color: var(--el-color-primary);
}

.session-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.session-more {
  padding: 10px 12px 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

/* 中间聊天区 */
.chat-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.chat-head-left,
.chat-head-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.chat-head-actions {
  flex-shrink: 0;
}

.chat-head-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--el-text-color-primary);
}

.chat-title-text {
  max-width: 420px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.message-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 18px;
  background: #fafbfc;
}

.load-more {
  display: flex;
  justify-content: center;
  padding-bottom: 12px;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 120px;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.msg-row.user {
  justify-content: flex-end;
}

.msg-avatar {
  flex-shrink: 0;
  background: #4f86c6;
  font-size: 13px;
}

.msg-avatar.user {
  background: #f2a6b8;
}

.msg-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-width: min(78%, 720px);
}

.msg-row.user .msg-main {
  align-items: flex-end;
}

.msg-bubble {
  padding: 10px 14px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 1px 2px rgba(16, 24, 39, 0.04);
  word-break: break-word;
}

.msg-bubble.is-user {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}

.msg-bubble.is-error {
  border-color: var(--el-color-danger-light-5);
}

.user-text {
  white-space: pre-wrap;
}

.msg-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.meta-error {
  color: var(--el-color-danger);
}

.msg-error {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--el-color-danger-light-5);
  font-size: 13px;
  color: var(--el-color-danger);
}

/* 工具调用状态 */
.tool-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}

.tool-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  width: fit-content;
  max-width: 100%;
}

.tool-chip.pending {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
}

.tool-chip.failed {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.tool-name {
  flex-shrink: 0;
}

.tool-summary {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0.8;
}

.thinking {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.thinking-dots {
  display: inline-block;
  animation: blink 1.2s infinite steps(1);
}

@keyframes blink {
  0%,
  100% {
    opacity: 0.2;
  }
  50% {
    opacity: 1;
  }
}

/* Markdown 正文 */
.md-body {
  font-size: 14px;
  line-height: 1.7;
}

.md-body :deep(p) {
  margin: 0 0 8px;
}

.md-body :deep(p:last-child) {
  margin-bottom: 0;
}

.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 6px 0;
  padding-left: 20px;
}

.md-body :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}

.md-body :deep(pre) {
  margin: 8px 0;
  padding: 10px 12px;
  border-radius: 6px;
  background: #101827;
  color: #e5e7eb;
  overflow-x: auto;
}

.md-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.md-body :deep(blockquote) {
  margin: 8px 0;
  padding-left: 10px;
  border-left: 3px solid var(--el-border-color);
  color: var(--el-text-color-secondary);
}

.md-body :deep(a) {
  color: var(--el-color-primary);
}

.md-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}

.md-body :deep(th),
.md-body :deep(td) {
  padding: 4px 8px;
  border: 1px solid var(--el-border-color-lighter);
}

/* 右侧形象 */
.stage-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
  opacity: 1;
  transition: opacity 0.16s ease;
}

.stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.stage-title {
  font-weight: 600;
  white-space: nowrap;
}

.stage-head-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.stage-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.stage-card :deep(.pixel-stage) {
  flex: 1;
  min-height: 0;
  padding: 12px;
  overflow: hidden;
}

@media (max-width: 1280px) {
  .chat-view {
    grid-template-columns: 240px minmax(0, 1fr);
    height: auto;
  }

  .chat-view.sessions-collapsed {
    grid-template-columns: 0 minmax(0, 1fr);
  }

  .chat-view.stage-collapsed {
    grid-template-columns: 240px minmax(0, 1fr);
  }

  .chat-view.sessions-collapsed.stage-collapsed {
    grid-template-columns: 0 minmax(0, 1fr);
  }

  .stage-card {
    grid-column: 1 / -1;
    min-height: 340px;
  }

  /* 窄屏下舞台换到第二行，收起时直接移出布局，避免留下行间距 */
  .chat-view.stage-collapsed .stage-card {
    display: none;
  }
}

@media (max-width: 860px) {
  .chat-view,
  .chat-view.sessions-collapsed,
  .chat-view.stage-collapsed,
  .chat-view.sessions-collapsed.stage-collapsed {
    grid-template-columns: minmax(0, 1fr);
  }

  .session-panel {
    max-height: 220px;
  }

  .chat-view.sessions-collapsed .session-panel {
    display: none;
  }

  .message-area {
    min-height: 360px;
  }

  .msg-main {
    max-width: 88%;
  }
}

/* 窄屏：会话列表改为抽屉，形象舞台改为底部浮层 */
@media (max-width: 900px) {
  .chat-view,
  .chat-view.sessions-collapsed,
  .chat-view.stage-collapsed,
  .chat-view.sessions-collapsed.stage-collapsed {
    grid-template-columns: minmax(0, 1fr);
    height: calc(100vh - var(--app-header-height, 60px) - 40px);
    height: calc(100dvh - var(--app-header-height, 60px) - 40px);
    min-height: 0;
  }

  .session-panel {
    position: fixed;
    top: var(--app-header-height, 60px);
    bottom: 0;
    left: 0;
    z-index: 2200;
    width: min(280px, 80vw);
    max-height: none;
    border-radius: 0;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }

  /* 覆盖桌面端“收起”时的隐藏样式，移动端由抽屉状态控制 */
  .chat-view.sessions-collapsed .session-panel,
  .chat-view.stage-collapsed .stage-card {
    display: flex;
    opacity: 1;
    pointer-events: auto;
    border-color: var(--el-border-color-light);
  }

  .chat-view.mobile-sessions-open .session-panel {
    transform: translateX(0);
    box-shadow: 4px 0 16px rgba(15, 23, 42, 0.18);
  }

  .session-backdrop,
  .stage-backdrop {
    position: fixed;
    inset: var(--app-header-height, 60px) 0 0;
    z-index: 2100;
    background: rgba(15, 23, 42, 0.35);
  }

  .stage-card {
    position: fixed;
    right: 12px;
    bottom: 12px;
    left: 12px;
    z-index: 2200;
    height: min(60vh, 420px);
    min-height: 0;
    border-radius: 12px;
    box-shadow: 0 12px 32px rgba(15, 23, 42, 0.25);
  }

  .stage-tip {
    display: none;
  }

  .message-area {
    min-height: 0;
    padding: 12px;
  }

  .msg-main {
    max-width: 92%;
  }

  .chat-title-text {
    max-width: 36vw;
  }

  .chat-head {
    padding: 8px 10px;
  }
}

.persona-dialog-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
