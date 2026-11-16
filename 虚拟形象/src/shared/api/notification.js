import { API_BASE_URL } from './config'
import { request, handleUnauthorized, AuthError } from './request'
import { getToken, isTokenExpired } from '@/shared/utils/auth'

const API_BASE = `${API_BASE_URL}/notifications`

export class NotificationApiError extends Error {
  constructor(code, message) {
    super(message || '请求失败')
    this.name = 'NotificationApiError'
    this.code = code || 'NOTIFICATION_ERROR'
  }
}

function unwrap(res) {
  if (!res) {
    throw new NotificationApiError('NETWORK_ERROR', '网络异常，请稍后重试')
  }
  if (String(res.code) !== '200') {
    throw new NotificationApiError(res.code, res.msg)
  }
  return res.data
}

export async function listNotifications({ page = 1, size = 20 } = {}) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return unwrap(await request(`${API_BASE}?${query.toString()}`, { method: 'GET' }))
}

export async function getUnreadCount() {
  return unwrap(await request(`${API_BASE}/unread-count`, { method: 'GET' }))
}

export async function markRead(id) {
  return unwrap(await request(`${API_BASE}/${id}/read`, { method: 'POST' }))
}

export async function markAllRead() {
  return unwrap(await request(`${API_BASE}/read-all`, { method: 'POST' }))
}

export async function getNotificationSettings() {
  return unwrap(await request(`${API_BASE}/settings`, { method: 'GET' }))
}

export async function updateNotificationSettings(data) {
  return unwrap(
    await request(`${API_BASE}/settings`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
  )
}

export async function getPushPublicKey() {
  return unwrap(await request(`${API_BASE}/push/public-key`, { method: 'GET' }))
}

export async function subscribePush(data) {
  return unwrap(
    await request(`${API_BASE}/push/subscribe`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
  )
}

export async function unsubscribePush(endpoint) {
  return unwrap(
    await request(`${API_BASE}/push/unsubscribe`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ endpoint })
    })
  )
}

export async function sendTestPush() {
  return unwrap(await request(`${API_BASE}/push/test`, { method: 'POST' }))
}

function isAbortError(error) {
  return error && (error.name === 'AbortError' || error.code === 20)
}

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
  let data = raw
  try {
    data = JSON.parse(raw)
  } catch {
    // 非 JSON 数据原样保留
  }
  return { event, data }
}

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
  } finally {
    try {
      reader.releaseLock()
    } catch {
      // 忽略释放失败
    }
  }
}

/**
 * 订阅通知 SSE。浏览器 EventSource 不能带 Authorization，使用 fetch + ReadableStream。
 */
export async function streamNotifications({ signal, onEvent = () => {} }) {
  const token = getToken()
  if (token && isTokenExpired(token)) {
    handleUnauthorized('登录已过期，请重新登录')
    throw new AuthError('登录已过期')
  }

  let response
  try {
    response = await fetch(`${API_BASE}/stream`, {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      signal
    })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new NotificationApiError('NETWORK_ERROR', '通知连接失败')
  }

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
    throw new NotificationApiError(
      (payload && payload.code) || `HTTP_${response.status}`,
      (payload && payload.msg) || '通知连接失败'
    )
  }

  await consumeStream(response.body, onEvent)
}
