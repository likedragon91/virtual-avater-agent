import { ElMessage } from 'element-plus'
import router from '@/app/router'
import { getToken, clearAuth, isTokenExpired } from '@/shared/utils/auth'

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

/**
 * 统一请求：自动携带 Authorization，解析 JSON，集中处理 401
 * 后端 JWT 白名单在 Redis 中，token 被撤销/过期时任意接口都会返回 401，
 * 这里统一清除本地登录态并回到登录页。
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

  const headers = { ...(customHeaders || {}) }

  if (auth) {
    const token = getToken()
    if (token) {
      // 本地已过期：不必再请求后端
      if (!skipAuthHandler && isTokenExpired(token)) {
        handleUnauthorized('登录已过期，请重新登录')
        throw new AuthError('登录已过期')
      }
      headers.Authorization = `Bearer ${token}`
    }
  }

  const response = await fetch(url, { ...rest, headers })

  let data = null
  try {
    data = await response.json()
  } catch {
    data = null
  }

  // 未登录、token 过期或已被 Redis 白名单撤销
  if (!skipAuthHandler && (response.status === 401 || (data && String(data.code) === '401'))) {
    handleUnauthorized(data && data.msg)
    throw new AuthError((data && data.msg) || '登录已过期，请重新登录')
  }

  return data
}
