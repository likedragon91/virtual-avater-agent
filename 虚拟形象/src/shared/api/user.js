import { request } from './request'
import { API_BASE_URL } from './config'
import { getRefreshToken } from '@/shared/utils/auth'

/**
 * 获取图形验证码
 * 答案只保存在后端 Redis，前端拿到 captchaId 和 Base64 图片，登录时原样回传
 * @returns {Promise<{ code: string, msg: string, data: { captchaId: string, captchaImage: string } }>}
 */
export function getCaptcha() {
  return request(`${API_BASE_URL}/user/captcha`, {
    method: 'GET',
    auth: false,
    skipAuthHandler: true
  })
}

/**
 * 登录接口
 * 登录成功后后端签发 Access + Refresh Token，并把会话写入 Redis 白名单。
 * @param {{ username: string, password: string, captchaId: string, captchaCode: string, clientType?: 'DESKTOP'|'WEB' }} data
 * @returns {Promise<{ code: string, msg: string, data: { token: string, refreshToken: string, expiresIn: number, clientType: string, roleType: string, userInfo: object } }>}
 */
export function login(data) {
  return request(`${API_BASE_URL}/user/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    auth: false,
    skipAuthHandler: true
  })
}

/**
 * 注册接口
 * @param {{ username: string, email: string, nickname?: string|null, phone?: string|null, password: string, confirmPassword: string, gender?: number, userType?: number }} data
 * @returns {Promise<{ code: string, msg: string, data: object }>}
 */
export function register(data) {
  return request(`${API_BASE_URL}/user/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    auth: false,
    skipAuthHandler: true
  })
}

/**
 * 获取用户信息
 * 传 username 时按账号查询；不传时查询当前登录用户（依赖请求头中的 token）
 * @param {string} [username]
 * @returns {Promise<{ code: string, msg: string, data: object }>}
 */
export function getUserMessage(username) {
  const query = username ? `?username=${encodeURIComponent(username)}` : ''
  return request(`${API_BASE_URL}/user/message${query}`, {
    method: 'GET'
  })
}

/**
 * 用户列表（仅管理员）
 * @param {{ page?: number, size?: number, keyword?: string }} params
 * @returns {Promise<{ code: string, msg: string, data: { records: object[], total: number, page: number, size: number } }>}
 */
export function getUserList(params = {}) {
  const { page = 1, size = 10, keyword = '' } = params
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (keyword) query.set('keyword', keyword)
  return request(`${API_BASE_URL}/user/list?${query.toString()}`, {
    method: 'GET'
  })
}

/**
 * 在线用户列表（仅管理员）
 * 在线会话保存在后端 Redis 中，过期会话由 Redis TTL 自动清理
 * @returns {Promise<{ code: string, msg: string, data: object[] }>}
 */
export function getOnlineUsers() {
  return request(`${API_BASE_URL}/user/online`, {
    method: 'GET'
  })
}

/**
 * 当前用户编辑自己的资料
 * 只允许昵称、头像、手机、性别、生日；权限字段由后端白名单限制。
 * @param {{ nickname?: string, avatar?: string, phone?: string, gender?: number, birthday?: string }} data
 */
export function updateProfile(data) {
  return request(`${API_BASE_URL}/user/profile`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
}

/**
 * 管理员修改用户状态
 * @param {number} id
 * @param {number} status 0-禁用，1-正常
 */
export function updateUserStatus(id, status) {
  return request(`${API_BASE_URL}/user/${id}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status })
  })
}

/**
 * 请求发送找回密码验证码
 * @param {string} email
 */
export function requestPasswordReset(email) {
  return request(`${API_BASE_URL}/user/password/forgot`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
    auth: false,
    skipAuthHandler: true
  })
}

/**
 * 使用邮箱验证码重置密码
 * newPassword / confirmPassword 均为前端 SM3 后的 64 位摘要
 * @param {{ email: string, code: string, newPassword: string, confirmPassword: string }} data
 */
export function resetPassword(data) {
  return request(`${API_BASE_URL}/user/password/reset`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    auth: false,
    skipAuthHandler: true
  })
}

/**
 * 退出登录
 * 后端会撤销整段会话（Access + Refresh）并移除在线会话；
 * 即使请求失败，调用方也应清除本地登录态。
 * @returns {Promise<{ code: string, msg: string }>}
 */
export function logout() {
  const refreshToken = getRefreshToken()
  return request(`${API_BASE_URL}/user/logout`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: refreshToken || '' }),
    // token 已失效时不再触发全局跳转，由调用方统一清理本地状态
    skipAuthHandler: true
  })
}
