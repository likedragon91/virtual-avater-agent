import { request } from './request'

// 接口基础地址：Spring Boot 后端（后端已配置跨域，直接请求）
export const BASE_URL = 'http://localhost:1107'

/**
 * 获取图形验证码
 * 答案只保存在后端 Redis，前端拿到 captchaId 和 Base64 图片，登录时原样回传
 * @returns {Promise<{ code: string, msg: string, data: { captchaId: string, captchaImage: string } }>}
 */
export function getCaptcha() {
  return request(`${BASE_URL}/api/user/captcha`, {
    method: 'GET',
    auth: false,
    skipAuthHandler: true
  })
}

/**
 * 登录接口
 * 登录成功后后端会生成带 jti 的 token 并写入 Redis 白名单
 * @param {{ username: string, password: string, captchaId: string, captchaCode: string }} data
 * @returns {Promise<{ code: string, msg: string, data: { token: string, roleType: string, userInfo: object } }>}
 */
export function login(data) {
  return request(`${BASE_URL}/api/user/login`, {
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
  return request(`${BASE_URL}/api/user/register`, {
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
  return request(`${BASE_URL}/api/user/message${query}`, {
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
  return request(`${BASE_URL}/api/user/list?${query.toString()}`, {
    method: 'GET'
  })
}

/**
 * 在线用户列表（仅管理员）
 * 在线会话保存在后端 Redis 中，过期会话由 Redis TTL 自动清理
 * @returns {Promise<{ code: string, msg: string, data: object[] }>}
 */
export function getOnlineUsers() {
  return request(`${BASE_URL}/api/user/online`, {
    method: 'GET'
  })
}

/**
 * 退出登录
 * 后端会删除 Redis 白名单中的 jti 并移除在线会话，token 立即失效；
 * 即使请求失败，调用方也应清除本地登录态。
 * @returns {Promise<{ code: string, msg: string }>}
 */
export function logout() {
  return request(`${BASE_URL}/api/user/logout`, {
    method: 'POST',
    // token 已失效时不再触发全局跳转，由调用方统一清理本地状态
    skipAuthHandler: true
  })
}
