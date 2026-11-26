import { API_BASE_URL } from '@/shared/api/config'

export const APP_VERSION =
  typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.1.0'

/** Electron 安装后的版本优先；网页端使用构建时的 package.json 版本 */
export async function resolveCurrentVersion() {
  if (typeof window !== 'undefined' && window.desktopBridge?.appVersion) {
    try {
      const value = await window.desktopBridge.appVersion()
      if (value) return value
    } catch {
      // 回退到构建版本
    }
  }
  return APP_VERSION
}

/** 后端返回 /api/files/...，Electron 下补全服务器 origin */
export function resolveDownloadUrl(url) {
  const value = url || ''
  if (!value) return ''
  if (/^https?:\/\//i.test(value)) return value
  if (/^https?:\/\//i.test(API_BASE_URL)) {
    try {
      return new URL(API_BASE_URL).origin + value
    } catch {
      return value
    }
  }
  return value
}

export function compareVersions(left, right) {
  const toParts = (value) =>
    String(value || '0')
      .split('.')
      .map((part) => {
        const number = Number.parseInt(part, 10)
        return Number.isFinite(number) ? number : 0
      })
  const a = toParts(left)
  const b = toParts(right)
  const length = Math.max(a.length, b.length)
  for (let i = 0; i < length; i++) {
    const x = a[i] || 0
    const y = b[i] || 0
    if (x !== y) return x > y ? 1 : -1
  }
  return 0
}

/** Electron 用系统浏览器打开安装包下载；网页端新窗口打开 */
export async function openInstaller(url) {
  if (!url) return false
  if (typeof window !== 'undefined' && window.desktopBridge?.openExternal) {
    return window.desktopBridge.openExternal(url)
  }
  window.open(url, '_blank', 'noopener,noreferrer')
  return true
}
