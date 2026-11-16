/* 虚拟形象 Web Push Service Worker */

self.addEventListener('install', () => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

// 只允许跳转到同源站内路径，避免推送内容把用户带到外部站点
const safeTarget = (value) => {
  try {
    const url = new URL(value || '/task', self.location.origin)
    if (url.origin !== self.location.origin) return '/task'
    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return '/task'
  }
}

self.addEventListener('push', (event) => {
  let payload = {}
  try {
    payload = event.data ? event.data.json() : {}
  } catch {
    payload = {}
  }

  const title = payload.title || '虚拟形象'
  const options = {
    body: payload.body || '你有一条新提醒',
    tag: payload.tag || 'virtual-agent',
    data: {
      url: safeTarget(payload.url),
      ...(payload.data || {})
    }
  }

  event.waitUntil(self.registration.showNotification(title, options))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const data = event.notification.data || {}
  const target = safeTarget(data.url)

  event.waitUntil(
    (async () => {
      const all = await self.clients.matchAll({
        type: 'window',
        includeUncontrolled: true
      })
      for (const client of all) {
        if (client.url.startsWith(self.location.origin)) {
          if ('navigate' in client) {
            try {
              await client.navigate(target)
            } catch {
              // 某些浏览器不允许 navigate，退回 focus
            }
          }
          return client.focus()
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(target)
      }
      return null
    })()
  )
})
