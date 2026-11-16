// 登录态统一管理：accessToken / refreshToken / userInfo 的读写与 JWT 过期判断
import {
  isElectron,
  readElectronRefreshToken,
  writeElectronRefreshToken
} from './tokenStore'

const TOKEN_KEY = 'token'
const REFRESH_TOKEN_KEY = 'refreshToken'
const USER_INFO_KEY = 'userInfo'

// Electron 下 Refresh Token 由安全存储持久化，这里保留内存副本供同步读取
let refreshTokenCache = ''

export function isElectronRuntime() {
  return isElectron
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken() {
  if (refreshTokenCache) return refreshTokenCache
  if (isElectron) return ''
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

/**
 * 启动时把 Electron 安全存储中的 Refresh Token 载入内存。
 * 浏览器环境直接读取 localStorage，保留该 API 以便统一调用。
 */
export async function loadRefreshTokenFromStore() {
  if (isElectron) {
    refreshTokenCache = (await readElectronRefreshToken()) || ''
  } else {
    refreshTokenCache = localStorage.getItem(REFRESH_TOKEN_KEY) || ''
  }
  return refreshTokenCache
}

function persistRefreshToken(token) {
  refreshTokenCache = token || ''
  if (isElectron) {
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    void writeElectronRefreshToken(refreshTokenCache)
    return
  }
  if (refreshTokenCache) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshTokenCache)
  } else {
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  }
}

export function getUserInfo() {
  try {
    return JSON.parse(localStorage.getItem(USER_INFO_KEY)) || null
  } catch {
    return null
  }
}

export function setAuth(token, userInfo, refreshToken) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  setUserInfo(userInfo)
  if (refreshToken !== undefined) persistRefreshToken(refreshToken)
}

/** 只更新凭证，保留现有 userInfo（刷新会话时使用）。 */
export function setTokens(token, refreshToken) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  if (refreshToken !== undefined) persistRefreshToken(refreshToken)
}

export function setUserInfo(userInfo) {
  if (userInfo) localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_INFO_KEY)
  refreshTokenCache = ''
  if (isElectron) {
    void writeElectronRefreshToken('')
  }
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

/** 是否即将过期（默认提前 60 秒刷新）。 */
export function isTokenExpiringSoon(token = getToken(), marginSeconds = 60) {
  const payload = parseJwtPayload(token)
  if (!payload || !payload.exp) return false
  return Date.now() >= Number(payload.exp) * 1000 - marginSeconds * 1000
}
