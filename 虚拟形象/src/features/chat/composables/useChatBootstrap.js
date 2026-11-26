import { useAuthStore } from '@/shared/stores/auth'
import { useChatStore } from '@/shared/stores/chat'
import { useVoiceStore } from '@/shared/stores/voice'

/**
 * 聊天页与左侧面板共用的初始化。
 * 面板模式下 ChatView 可能没有挂载，必须由面板自己初始化聊天与语音状态。
 */
export function useChatBootstrap() {
  const authStore = useAuthStore()
  const chatStore = useChatStore()
  const voiceStore = useVoiceStore()
  let pending = null

  function currentOwner() {
    const info = authStore.userInfo || {}
    return info.username || info.id || 'anonymous'
  }

  function ensure() {
    if (pending) return pending
    pending = (async () => {
      await chatStore.initialize(currentOwner())
      // 已加载时不会重复请求；面板是唯一入口时也能拿到语音配置
      await voiceStore.loadConfig().catch(() => {})
    })().catch((error) => {
      pending = null
      throw error
    })
    return pending
  }

  return { ensure }
}
