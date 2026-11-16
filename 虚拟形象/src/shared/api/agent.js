import { API_BASE_URL } from './config'
import { request, handleUnauthorized, AuthError } from './request'
import { getToken, isTokenExpired } from '@/shared/utils/auth'

const AGENT_BASE = `${API_BASE_URL}/agent`

/**
 * Agent 接口业务错误。
 * 后端流建立前的错误仍返回 JSON，这里把 code/msg 原样透出，
 * 由 store 决定如何提示，避免把后端堆栈直接展示给用户。
 */
export class AgentApiError extends Error {
  constructor(code, message) {
    super(message || '请求失败')
    this.name = 'AgentApiError'
    this.code = code || 'AGENT_ERROR'
  }
}

function unwrap(res) {
  if (!res) {
    throw new AgentApiError('NETWORK_ERROR', '网络异常，请稍后重试')
  }
  if (String(res.code) !== '200') {
    throw new AgentApiError(res.code, res.msg)
  }
  return res.data
}

/**
 * 新建会话
 * @param {{ title?: string, personaCode?: string }} [data]
 * @returns {Promise<object>} ChatSessionResponseDTO
 */
export async function createSession(data = {}) {
  const body = {}
  if (data.title) body.title = data.title
  if (data.personaCode) body.personaCode = data.personaCode
  return unwrap(
    await request(`${AGENT_BASE}/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
  )
}

/**
 * 会话列表（按最后活跃时间倒序）
 * @param {{ page?: number, size?: number }} [params]
 * @returns {Promise<{ records: object[], total: number, page: number, size: number }>}
 */
export async function listSessions(params = {}) {
  const { page = 1, size = 50 } = params
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return unwrap(await request(`${AGENT_BASE}/sessions?${query.toString()}`, { method: 'GET' }))
}

/**
 * 会话详情
 * @param {number|string} sessionId
 */
export async function getSession(sessionId) {
  return unwrap(await request(`${AGENT_BASE}/sessions/${sessionId}`, { method: 'GET' }))
}

/**
 * 重命名 / 归档会话
 * @param {number|string} sessionId
 * @param {{ title?: string, status?: number }} data
 */
export async function updateSession(sessionId, data) {
  const body = {}
  if (typeof data.title === 'string') body.title = data.title
  if (typeof data.status === 'number') body.status = data.status
  return unwrap(
    await request(`${AGENT_BASE}/sessions/${sessionId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
  )
}

/**
 * 删除会话
 * @param {number|string} sessionId
 */
export async function deleteSession(sessionId) {
  return unwrap(await request(`${AGENT_BASE}/sessions/${sessionId}`, { method: 'DELETE' }))
}

/**
 * 历史消息（按时间升序）
 * cursor 为空取最新一页；向前翻页时传上一页的 nextCursor。
 * @param {number|string} sessionId
 * @param {{ cursor?: number|null, size?: number }} [params]
 * @returns {Promise<{ records: object[], nextCursor: number|null, hasMore: boolean }>}
 */
export async function listMessages(sessionId, params = {}) {
  const { cursor = null, size = 30 } = params
  const query = new URLSearchParams({ size: String(size) })
  if (cursor != null) query.set('cursor', String(cursor))
  return unwrap(
    await request(`${AGENT_BASE}/sessions/${sessionId}/messages?${query.toString()}`, {
      method: 'GET'
    })
  )
}

/**
 * 中断生成。runId 即 assistant 消息 id。
 * @param {number|string} runId
 */
export async function cancelRun(runId) {
  return unwrap(await request(`${AGENT_BASE}/runs/${runId}/cancel`, { method: 'POST' }))
}

function isAbortError(error) {
  return error && (error.name === 'AbortError' || error.code === 20)
}

/**
 * 解析一条 SSE 事件块。
 * Spring SseEmitter 输出形如：
 *   event:delta\n
 *   data:{"text":"你"}\n
 *   \n
 */
function parseEventBlock(block) {
  let event = 'message'
  const dataLines = []
  for (const rawLine of block.split('\n')) {
    if (!rawLine || rawLine.startsWith(':')) continue
    const colon = rawLine.indexOf(':')
    const field = colon === -1 ? rawLine : rawLine.slice(0, colon)
    let value = colon === -1 ? '' : rawLine.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') dataLines.push(value)
  }
  if (!dataLines.length) return null
  const raw = dataLines.join('\n')
  if (raw === '[DONE]') return { event: 'done', data: { finishReason: 'stop' } }
  let data = raw
  try {
    data = JSON.parse(raw)
  } catch {
    // 非 JSON 的 data 原样透出，方便排查后端协议变化
  }
  return { event, data }
}

/**
 * 读取 SSE 流并逐事件回调。
 * @param {ReadableStream<Uint8Array>} body
 * @param {(event: string, data: any) => void} onEvent
 */
async function consumeStream(body, onEvent) {
  if (!body) return
  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n')

      let boundary = buffer.indexOf('\n\n')
      while (boundary !== -1) {
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        const parsed = parseEventBlock(block)
        if (parsed) onEvent(parsed.event, parsed.data)
        boundary = buffer.indexOf('\n\n')
      }
    }
    buffer += decoder.decode()
    if (buffer.trim()) {
      const parsed = parseEventBlock(buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n'))
      if (parsed) onEvent(parsed.event, parsed.data)
    }
  } finally {
    try {
      reader.releaseLock()
    } catch {
      // 忽略释放失败
    }
  }
}

/**
 * 发送消息并读取 SSE 流。
 * 浏览器 EventSource 不能带 Authorization，必须用 fetch + ReadableStream。
 *
 * @param {{
 *   sessionId: number|string,
 *   content: string,
 *   clientMessageId: string,
 *   signal?: AbortSignal,
 *   onEvent?: (event: string, data: any) => void
 * }} options
 */
export async function streamMessage({
  sessionId,
  content,
  clientMessageId,
  signal,
  onEvent = () => {}
}) {
  const token = getToken()
  if (token && isTokenExpired(token)) {
    handleUnauthorized('登录已过期，请重新登录')
    throw new AuthError('登录已过期')
  }

  let response
  try {
    response = await fetch(`${AGENT_BASE}/sessions/${sessionId}/messages/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ content, clientMessageId }),
      signal
    })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new AgentApiError('NETWORK_ERROR', '网络异常，请检查后端服务是否已启动')
  }

  // 流建立前的错误（越权、重复发送、已归档、token 失效）仍是 JSON
  const contentType = response.headers.get('content-type') || ''
  if (!response.ok || !contentType.includes('text/event-stream')) {
    let payload = null
    try {
      payload = await response.json()
    } catch {
      payload = null
    }
    if (response.status === 401 || (payload && String(payload.code) === '401')) {
      handleUnauthorized(payload && payload.msg)
      throw new AuthError((payload && payload.msg) || '登录已过期，请重新登录')
    }
    throw new AgentApiError(
      (payload && payload.code) || `HTTP_${response.status}`,
      (payload && payload.msg) || '发送失败，请稍后重试'
    )
  }

  try {
    await consumeStream(response.body, onEvent)
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new AgentApiError('STREAM_ERROR', '连接中断，请重试')
  }
}

/**
 * 生成本次发送的幂等键（≤64 字符）。
 * 后端以 (session_id, client_message_id) 唯一索引去重。
 */
export function createClientMessageId() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return `c_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 12)}`
}
