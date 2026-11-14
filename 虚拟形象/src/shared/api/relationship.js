import { BASE_URL } from './user'
import { request } from './request'

const API_BASE = `${BASE_URL}/api/relationship`

export class RelationshipApiError extends Error {
  constructor(code, message) {
    super(message || '请求失败')
    this.name = 'RelationshipApiError'
    this.code = code || 'RELATIONSHIP_ERROR'
  }
}

function unwrap(res) {
  if (!res) {
    throw new RelationshipApiError('NETWORK_ERROR', '网络异常，请稍后重试')
  }
  if (String(res.code) !== '200') {
    throw new RelationshipApiError(res.code, res.msg)
  }
  return res.data
}

export async function getRelationship() {
  return unwrap(await request(API_BASE, { method: 'GET' }))
}

export async function getRelationshipLogs({ page = 1, size = 20 } = {}) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return unwrap(await request(`${API_BASE}/logs?${query.toString()}`, { method: 'GET' }))
}
