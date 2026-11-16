import { API_BASE_URL } from './config'
import { request } from './request'

const API_BASE = API_BASE_URL

export class TaskApiError extends Error {
  constructor(code, message) {
    super(message || '请求失败')
    this.name = 'TaskApiError'
    this.code = code || 'TASK_ERROR'
  }
}

function unwrap(res) {
  if (!res) {
    throw new TaskApiError('NETWORK_ERROR', '网络异常，请稍后重试')
  }
  if (String(res.code) !== '200') {
    throw new TaskApiError(res.code, res.msg)
  }
  return res.data
}

export async function createTask(data) {
  return unwrap(
    await request(`${API_BASE}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
  )
}

export async function listTasks({ page = 1, size = 20, status = 'ACTIVE' } = {}) {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
    status
  })
  return unwrap(await request(`${API_BASE}/tasks?${query.toString()}`, { method: 'GET' }))
}

export async function getTask(id) {
  return unwrap(await request(`${API_BASE}/tasks/${id}`, { method: 'GET' }))
}

export async function updateTask(id, data) {
  return unwrap(
    await request(`${API_BASE}/tasks/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
  )
}

export async function archiveTask(id) {
  return unwrap(await request(`${API_BASE}/tasks/${id}`, { method: 'DELETE' }))
}

export async function listOccurrences({ from, to, status = '', limit = 50 } = {}) {
  const query = new URLSearchParams({ limit: String(limit) })
  if (from) query.set('from', from)
  if (to) query.set('to', to)
  if (status) query.set('status', status)
  return unwrap(
    await request(`${API_BASE}/task-occurrences?${query.toString()}`, { method: 'GET' })
  )
}

export async function respondOccurrence(occurrenceId, data) {
  return unwrap(
    await request(`${API_BASE}/task-occurrences/${occurrenceId}/response`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
  )
}

export function createIdempotencyKey() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return `r_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 12)}`
}
