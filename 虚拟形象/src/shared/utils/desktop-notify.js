/**
 * Electron 原生通知适配层。
 * 浏览器 / PWA 仍由各自的通知逻辑处理，这里只负责桌面壳。
 */
export function isDesktopApp() {
  return typeof window !== 'undefined' && Boolean(window.desktopBridge?.isElectron)
}

export function notifySystem(payload = {}) {
  const bridge = window.desktopBridge
  if (!isDesktopApp() || typeof bridge.notify !== 'function') {
    return false
  }
  bridge.notify({
    id: payload.id,
    type: payload.type,
    title: payload.title || '虚拟形象',
    body: payload.body || ''
  })
  return true
}

export function onSystemNotificationClick(handler) {
  const bridge = window.desktopBridge
  if (!isDesktopApp() || typeof bridge.onNotificationClick !== 'function') {
    return () => {}
  }
  return bridge.onNotificationClick(handler)
}
