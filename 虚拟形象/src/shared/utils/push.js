// Web Push 浏览器侧工具：Service Worker 注册、订阅、退订

export function isPushSupported() {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  )
}

export function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = window.atob(base64)
  const outputArray = new Uint8Array(rawData.length)
  for (let i = 0; i < rawData.length; i += 1) {
    outputArray[i] = rawData.charCodeAt(i)
  }
  return outputArray
}

export async function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) {
    throw new Error('当前浏览器不支持 Service Worker')
  }
  const registration = await navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`)
  await navigator.serviceWorker.ready
  return registration
}

export async function getExistingPushSubscription() {
  if (!isPushSupported()) return null
  const registration = await navigator.serviceWorker.getRegistration()
  if (!registration) return null
  return registration.pushManager.getSubscription()
}

export async function subscribeBrowserPush(publicKey) {
  if (!isPushSupported()) {
    throw new Error('当前浏览器不支持通知')
  }
  if (!publicKey) {
    throw new Error('服务端未返回 VAPID 公钥')
  }
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    throw new Error('通知权限未开启，请在浏览器设置中允许通知')
  }

  const registration = await registerServiceWorker()
  const existing = await registration.pushManager.getSubscription()
  if (existing) return existing

  return registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(publicKey)
  })
}

export async function unsubscribeBrowserPush() {
  if (!isPushSupported()) return null
  const registration = await navigator.serviceWorker.getRegistration()
  if (!registration) return null
  const subscription = await registration.pushManager.getSubscription()
  if (!subscription) return null
  const endpoint = subscription.endpoint
  await subscription.unsubscribe()
  return endpoint
}
