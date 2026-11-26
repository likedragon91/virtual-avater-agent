import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getUnreadCount,
  listNotifications,
  markAllRead as markAllReadApi,
  markRead as markReadApi,
  streamNotifications
} from '@/shared/api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref([])
  const unreadCount = ref(0)
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const size = ref(20)
  const streaming = ref(false)

  let stopRequested = false
  let abortController = null

  // 页面级订阅者：MainLayout 用弹窗，任务页用来在到点时刷新列表
  const listeners = new Set()

  function subscribe(listener) {
    if (typeof listener !== 'function') return () => {}
    listeners.add(listener)
    return () => listeners.delete(listener)
  }

  function notifyListeners(notification) {
    listeners.forEach((listener) => {
      try {
        listener(notification)
      } catch {
        // 单个订阅者异常不影响其它订阅者
      }
    })
  }

  async function loadUnread() {
    try {
      const count = await getUnreadCount()
      unreadCount.value = Number(count || 0)
    } catch {
      // 后端未加载通知接口时静默，避免设置页式的阻塞提示
    }
  }

  async function loadList(targetPage = 1) {
    loading.value = true
    try {
      const data = await listNotifications({ page: targetPage, size: size.value })
      notifications.value = data?.records || []
      total.value = data?.total || 0
      page.value = data?.page || targetPage
      unreadCount.value = Number(data?.unreadCount || 0)
    } catch {
      notifications.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  async function markRead(id) {
    try {
      await markReadApi(id)
      const item = notifications.value.find((entry) => entry.id === id)
      if (item && !item.read) {
        item.read = true
        unreadCount.value = Math.max(0, unreadCount.value - 1)
      }
    } catch {
      // 静默
    }
  }

  async function markAllRead() {
    try {
      await markAllReadApi()
      notifications.value.forEach((entry) => {
        entry.read = true
      })
      unreadCount.value = 0
    } catch {
      // 静默
    }
  }

  /**
   * 建立通知 SSE。回调用于弹 ElNotification。
   * 后端未加载接口（404）或登录失效时停止重连。
   */
  async function startStream(onNotification) {
    if (streaming.value) return
    streaming.value = true
    stopRequested = false

    while (!stopRequested) {
      abortController = new AbortController()
      try {
        await streamNotifications({
          signal: abortController.signal,
          onEvent: (event, data) => {
            if (event === 'notification' && data) {
              unreadCount.value += 1
              notifications.value = [data, ...notifications.value].slice(0, 50)
              notifyListeners(data)
              // 任务到点：让悬浮球角色头顶弹出气泡
              if (data.type === 'TASK_REMINDER') {
                window.desktopBridge?.sendBallBubble?.({
                  id: data.id,
                  title: data.title,
                  body: data.body,
                  taskTitle: data.payload?.taskTitle
                })
              }
              if (typeof onNotification === 'function') onNotification(data)
            }
          }
        })
      } catch (error) {
        if (stopRequested || error?.name === 'AbortError') break
        if (error?.isAuthError || error?.code === 'HTTP_404') break
      }

      if (!stopRequested) {
        await new Promise((resolve) => setTimeout(resolve, 5000))
      }
    }
    streaming.value = false
  }

  function stopStream() {
    stopRequested = true
    if (abortController) {
      abortController.abort()
      abortController = null
    }
  }

  return {
    notifications,
    unreadCount,
    loading,
    total,
    page,
    size,
    streaming,
    loadUnread,
    loadList,
    markRead,
    markAllRead,
    subscribe,
    startStream,
    stopStream
  }
})
