import { ElMessage } from 'element-plus'
import router from '@/app/router'
import { clearAuth } from '@/shared/utils/auth'
import {
  getValidAccessToken,
  refreshAccessToken,
  RefreshError
} from '@/shared/utils/session'

// 认证失效错误：调用方捕获后直接忽略即可（统一跳转已由 request 处理）
export class AuthError extends Error {
  constructor(message) {
    super(message)
    this.name = 'AuthError'
    this.isAuthError = true
  }
}

// 防止并发请求同时触发多次提示和跳转
let unauthorizedHandling = false

export function handleUnauthorized(message) {
  clearAuth()
  if (unauthorizedHandling) return
  unauthorizedHandling = true

  ElMessage.error(message || '登录已过期，请重新登录')

  const current = router.currentRoute.value
  const redirect =
    current && current.path !== '/login' ? current.fullPath : undefined

  router
    .replace({ path: '/login', query: redirect ? { redirect } : undefined })
    .finally(() => {
      setTimeout(() => {
        unauthorizedHandling = false
      }, 1000)
    })
}

async function send(url, rest, customHeaders, token) {
  const headers = { ...(customHeaders || {}) }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(url, { ...rest, headers })
  let data = null
  try {
    data = await response.json()
  } catch {
    data = null
  }
  return { response, data }
}

/**
 * 统一请求：
 * - 自动携带 Authorization；
 * - Access Token 临期时主动静默刷新；
 * - 收到 401 时刷新一次并重放原请求；
 * - 刷新失败才清理登录态并回到登录页。
 *
 * @param {string} url
 * @param {RequestInit & { auth?: boolean, skipAuthHandler?: boolean }} options
 * @returns {Promise<any>} 后端统一响应 { code, msg, data }
 */
export async function request(url, options = {}) {
  const {
    auth = true,
    skipAuthHandler = false,
    headers: customHeaders,
    ...rest
  } = options

  let token = ''
  if (auth) {
    token = await getValidAccessToken()
    if (!token && !skipAuthHandler) {
      handleUnauthorized('登录已过期，请重新登录')
      throw new AuthError('登录已过期')
    }
  }

  let { response, data } = await send(url, rest, customHeaders, token)

  const unauthorized =
    response.status === 401 || (data && String(data.code) === '401')

  // 401：尝试刷新并重放一次
  if (unauthorized && auth && !skipAuthHandler) {
    try {
      token = await refreshAccessToken()
      ;({ response, data } = await send(url, rest, customHeaders, token))
    } catch (error) {
      if (error instanceof RefreshError && error.authFailed) {
        handleUnauthorized((data && data.msg) || error.message)
        throw new AuthError((data && data.msg) || '登录已过期，请重新登录')
      }
      throw error
    }
  }

  // 刷新后仍然 401，或未走刷新逻辑的 401：统一清理登录态
  if (
    auth &&
    !skipAuthHandler &&
    (response.status === 401 || (data && String(data.code) === '401'))
  ) {
    handleUnauthorized(data && data.msg)
    throw new AuthError((data && data.msg) || '登录已过期，请重新登录')
  }

  return data
}
