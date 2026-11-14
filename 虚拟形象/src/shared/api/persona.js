import { BASE_URL } from './user'
import { request } from './request'

const API_BASE = `${BASE_URL}/api/persona`

export class PersonaApiError extends Error {
  constructor(code, message) {
    super(message || '请求失败')
    this.name = 'PersonaApiError'
    this.code = code || 'PERSONA_ERROR'
  }
}

function unwrap(res) {
  if (!res) {
    throw new PersonaApiError('NETWORK_ERROR', '网络异常，请稍后重试')
  }
  if (String(res.code) !== '200') {
    throw new PersonaApiError(res.code, res.msg)
  }
  return res.data
}

export async function getPersonaOptions() {
  return unwrap(await request(`${API_BASE}/options`, { method: 'GET' }))
}

export async function getCurrentPersona() {
  return unwrap(await request(API_BASE, { method: 'GET' }))
}

export async function updatePersona(data) {
  return unwrap(
    await request(API_BASE, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
  )
}
