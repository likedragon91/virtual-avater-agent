// 登录态统一管理：token 与 userInfo 的读写、JWT 过期判断
const TOKEN_KEY = 'token'
const USER_INFO_KEY = 'userInfo'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUserInfo() {
  try {
    return JSON.parse(localStorage.getItem(USER_INFO_KEY)) || null
  } catch {
    return null
  }
}

export function setAuth(token, userInfo) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  setUserInfo(userInfo)
}

export function setUserInfo(userInfo) {
  if (userInfo) localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_INFO_KEY)
}

function decodeBase64Url(input) {
  const base64 = input.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder('utf-8').decode(bytes)
}

/**
 * 解析 JWT payload（只解码，不验签；验签由后端负责）
 * @param {string} token
 * @returns {object|null}
 */
export function parseJwtPayload(token) {
  if (!token || typeof token !== 'string') return null
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    return JSON.parse(decodeBase64Url(parts[1]))
  } catch {
    return null
  }
}

/**
 * 本地判断 token 是否已过期（提前 10 秒视为过期，避免临界请求）
 * 无法解析 exp 时交给后端白名单校验
 * @param {string} [token]
 * @returns {boolean}
 */
export function isTokenExpired(token = getToken()) {
  const payload = parseJwtPayload(token)
  if (!payload || !payload.exp) return false
  return Date.now() >= Number(payload.exp) * 1000 - 10000
}
