import { handleUnauthorized, AuthError } from './request'
import {
  getValidAccessToken,
  refreshAccessToken,
  RefreshError
} from '@/shared/utils/session'

/**
 * 带 JWT 的底层 fetch，用于非 JSON 响应（音频、zip、图片等）。
 * 401 时刷新一次并重放；刷新失败才清理登录态。
 */
export async function authorizedFetch(url, init = {}, signal, options = {}) {
  const { skipAuthHandler = false } = options
  let token = await getValidAccessToken()
  if (!token) {
    if (!skipAuthHandler) handleUnauthorized('登录已过期，请重新登录')
    throw new AuthError('登录已过期')
  }

  const build = (value) => ({
    ...init,
    headers: {
      ...(init.headers || {}),
      Authorization: `Bearer ${value}`
    },
    signal
  })

  let response = await fetch(url, build(token))
  if (response.status === 401) {
    try {
      token = await refreshAccessToken()
    } catch (error) {
      if (error instanceof RefreshError && error.authFailed) {
        if (!skipAuthHandler) handleUnauthorized(error.message)
        throw new AuthError(error.message || '登录已过期')
      }
      throw error
    }
    response = await fetch(url, build(token))
  }

  if (response.status === 401) {
    if (!skipAuthHandler) handleUnauthorized()
    throw new AuthError('登录已过期，请重新登录')
  }
  return response
}
