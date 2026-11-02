import { sm3 } from 'sm-crypto'

/**
 * 对文本做 SM3 摘要，返回 64 位十六进制字符串
 * 用于密码在传输前做摘要处理，服务端校验后再用 BCrypt 加盐存储
 * @param {string} text
 * @returns {string}
 */
export function sm3Encrypt(text) {
  return sm3(String(text ?? ''))
}
