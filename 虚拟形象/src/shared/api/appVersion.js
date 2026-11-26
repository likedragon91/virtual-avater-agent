import { API_BASE_URL } from './config'
import { request } from './request'
import { authorizedFetch } from './authFetch'

const VERSION_BASE = `${API_BASE_URL}/app/version`
const ADMIN_BASE = `${API_BASE_URL}/admin/app-versions`

function unwrap(res) {
  if (!res || String(res.code) !== '200') {
    throw new Error(res?.msg || '请求失败')
  }
  return res.data
}

async function parseJsonResponse(response) {
  let payload = null
  try {
    payload = await response.json()
  } catch {
    payload = null
  }
  if (response.ok && payload && String(payload.code) === '200') {
    return payload.data
  }
  const message = payload?.msg || payload?.message || payload?.error
    || `请求失败（HTTP ${response.status}）`
  throw new Error(message)
}

export async function getLatestAppVersion() {
  return unwrap(await request(`${VERSION_BASE}/latest`, { method: 'GET' }))
}

export async function listAppVersions() {
  return unwrap(await request(ADMIN_BASE, { method: 'GET' }))
}

export async function publishAppVersion({ version, notes, file, notify = true }) {
  const form = new FormData()
  form.append('version', version)
  form.append('notes', notes || '')
  form.append('file', file)
  form.append('notify', String(Boolean(notify)))
  const response = await authorizedFetch(ADMIN_BASE, {
    method: 'POST',
    body: form
  })
  return parseJsonResponse(response)
}

export async function deleteAppVersion(id) {
  return unwrap(await request(`${ADMIN_BASE}/${id}`, { method: 'DELETE' }))
}
