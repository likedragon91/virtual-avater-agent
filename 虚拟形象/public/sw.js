/* 虚拟形象 Service Worker：应用壳缓存 + Web Push */

const SHELL_CACHE = 'avatar-shell-v2'
const PRECACHE_URLS = [
  '/',
  '/index.html',
  '/manifest.webmanifest',
  '/icons/icon-192.png',
  '/icons/icon-512.png'
]

self.addEventListener('install', (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(SHELL_CACHE)
      // 单个资源失败不应阻塞整个 SW 安装
      await cache.addAll(PRECACHE_URLS).catch(() => {})
      await self.skipWaiting()
    })()
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys()
      await Promise.all(
        keys
          .filter((key) => key.startsWith('avatar-shell-') && key !== SHELL_CACHE)
          .map((key) => caches.delete(key))
      )
      await self.clients.claim()
    })()
  )
})

self.addEventListener('fetch', (event) => {
  const { request } = event
  if (request.method !== 'GET') return

  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return
  // 接口数据一律走网络，不缓存
  if (url.pathname.startsWith('/api/')) return

  // 导航请求：network-first，断网回退到应用壳
  if (request.mode === 'navigate') {
    event.respondWith(
      (async () => {
        try {
          const response = await fetch(request)
          if (response.ok) {
            const cache = await caches.open(SHELL_CACHE)
            cache.put('/index.html', response.clone())
          }
          return response
        } catch {
          const cached = await caches.match('/index.html')
          return cached || Response.error()
        }
      })()
    )
    return
  }

  // 带哈希的静态资源与图标：cache-first
  if (url.pathname.startsWith('/assets/') || url.pathname.startsWith('/icons/')) {
    event.respondWith(
      (async () => {
        const cached = await caches.match(request)
        if (cached) return cached
        const response = await fetch(request)
        if (response.ok) {
          const cache = await caches.open(SHELL_CACHE)
          cache.put(request, response.clone())
        }
        return response
      })()
    )
  }
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
    icon: '/icons/icon-192.png',
    badge: '/icons/icon-192.png',
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
