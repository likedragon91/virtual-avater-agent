import { API_BASE_URL } from '@/shared/api/config'
import {
  getToken,
  getRefreshToken,
  isTokenExpiringSoon,
  setTokens,
  setUserInfo
} from './auth'

/**
 * 刷新凭证失败。
 * authFailed=true 表示 Refresh Token 已失效，需要清理登录态并回登录页；
 * authFailed=false 表示网络等临时问题，保留本地状态等待重试。
 */
export class RefreshError extends Error {
  constructor(message, { authFailed = false, cause } = {}) {
    super(message)
    this.name = 'RefreshError'
    this.authFailed = authFailed
    this.isAuthError = authFailed
    this.cause = cause
  }
}

let refreshPromise = null

/**
 * 单飞刷新：并发请求共享同一个 Promise，避免同时打多次 refresh。
 * @returns {Promise<string>} 新的 Access Token
 */
export function refreshAccessToken() {
  if (refreshPromise) return refreshPromise
  refreshPromise = doRefresh().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

async function doRefresh() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new RefreshError('无刷新凭证', { authFailed: true })
  }

  let response
  try {
    response = await fetch(`${API_BASE_URL}/user/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken })
    })
  } catch (cause) {
    throw new RefreshError('网络异常，无法刷新登录状态', { authFailed: false, cause })
  }

  let payload = null
  try {
    payload = await response.json()
  } catch {
    payload = null
  }

  if (response.status === 401 || (payload && String(payload.code) === '401')) {
    throw new RefreshError((payload && payload.msg) || '登录已过期，请重新登录', {
      authFailed: true
    })
  }
  if (!response.ok || !payload || String(payload.code) !== '200' || !payload.data?.token) {
    throw new RefreshError((payload && payload.msg) || '刷新登录状态失败', {
      authFailed: false
    })
  }

  setTokens(payload.data.token, payload.data.refreshToken)
  if (payload.data.userInfo) setUserInfo(payload.data.userInfo)
  return payload.data.token
}

/**
 * 获取可用的 Access Token：
 * - 未临期：直接返回；
 * - 已临期：有 Refresh Token 时静默刷新；
 * - 刷新遇到网络错误：回退到旧 token，让请求按原逻辑失败。
 */
export async function getValidAccessToken() {
  const token = getToken()
  if (token && !isTokenExpiringSoon(token)) return token
  if (!getRefreshToken()) return token || ''
  try {
    return await refreshAccessToken()
  } catch (error) {
    if (error instanceof RefreshError && !error.authFailed) {
      return token || ''
    }
    return ''
  }
}
