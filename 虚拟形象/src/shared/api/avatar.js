import { API_BASE_URL } from './config'
import { request } from './request'
import { authorizedFetch } from './authFetch'

const AVATAR_BASE = `${API_BASE_URL}/avatar`

function unwrap(res) {
  if (!res || String(res.code) !== '200') {
    throw new Error((res && res.msg) || '形象配置请求失败')
  }
  return res.data
}

export async function getAvatarCatalog() {
  return unwrap(await request(`${AVATAR_BASE}/catalog`, { method: 'GET' }))
}

export async function getAvatarConfig(options = {}) {
  return unwrap(await request(`${AVATAR_BASE}/config`, { method: 'GET', ...options }))
}

export async function updateAvatarConfig({ driver, presetId, modelId, palette, revision }) {
  return unwrap(
    await request(`${AVATAR_BASE}/config`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ driver, presetId, modelId, palette, revision })
    })
  )
}

export async function listLive2dModels() {
  return unwrap(await request(`${AVATAR_BASE}/live2d`, { method: 'GET' }))
}

async function uploadFile(url, file) {
  const form = new FormData()
  form.append('file', file)
  const response = await authorizedFetch(url, { method: 'POST', body: form })
  const contentType = response.headers.get('content-type') || ''

  if (contentType.includes('application/json')) {
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
      || `上传失败（HTTP ${response.status}）`
    throw new Error(message)
  }

  if (!response.ok) {
    // 404 通常说明后端没有重启或没有编译到最新代码
    throw new Error(`上传失败（HTTP ${response.status}），请确认后端已重启到最新版本`)
  }
  throw new Error('上传响应格式异常，请检查后端接口')
}

export function uploadLive2dModel(file) {
  return uploadFile(`${AVATAR_BASE}/live2d`, file)
}

/** 单张图片作为静态立绘（driver=image），不是 Live2D 模型 */
export function uploadStaticImage(file) {
  return uploadFile(`${AVATAR_BASE}/image`, file)
}

export async function deleteLive2dModel(id) {
  return unwrap(
    await request(`${AVATAR_BASE}/live2d/${id}`, { method: 'DELETE' })
  )
}

// 模型 zip 动辄十几 MB，缓存最近一份，切换聊天/衣橱时避免重复下载
let lastLive2dModel = { id: null, buffer: null }

/** 下载 zip 为 ArrayBuffer，交给 live2d-renderer 的 load() */
export async function fetchLive2dModelFile(id, signal, options = {}) {
  if (lastLive2dModel.id === id && lastLive2dModel.buffer) {
    return lastLive2dModel.buffer.slice(0)
  }

  const response = await authorizedFetch(
    `${AVATAR_BASE}/live2d/${id}/file`,
    { method: 'GET', headers: { Accept: 'application/zip' } },
    signal,
    options
  )
  if (!response.ok) {
    let message = '模型下载失败'
    try {
      const payload = await response.json()
      message = payload?.msg || message
    } catch {
      // 非 JSON 错误体，保留默认文案
    }
    throw new Error(message)
  }
  const arrayBuffer = await response.arrayBuffer()
  lastLive2dModel = { id, buffer: arrayBuffer }
  return arrayBuffer.slice(0)
}
