/**
 * Refresh Token 存储适配。
 * - 浏览器 / PWA：localStorage（本轮接受 XSS 风险，后续可换 httpOnly Cookie）
 * - Electron：通过 preload 暴露的 secureStore 写入主进程 safeStorage
 */
const bridge = typeof window !== 'undefined' ? window.desktopBridge : null

export const isElectron = Boolean(bridge?.isElectron)

export async function readElectronRefreshToken() {
  const store = bridge?.secureStore
  if (!store || typeof store.get !== 'function') return ''
  try {
    const value = await store.get('refreshToken')
    return typeof value === 'string' ? value : ''
  } catch {
    return ''
  }
}

export async function writeElectronRefreshToken(token) {
  const store = bridge?.secureStore
  if (!store || typeof store.set !== 'function') return
  try {
    if (token) {
      await store.set('refreshToken', token)
    } else if (typeof store.remove === 'function') {
      await store.remove('refreshToken')
    }
  } catch {
    // 安全存储异常不影响当前会话，下次登录会重试
  }
}
