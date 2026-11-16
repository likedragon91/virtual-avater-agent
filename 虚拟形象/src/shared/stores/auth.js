import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getToken,
  getUserInfo,
  getRefreshToken,
  setAuth,
  setUserInfo,
  clearAuth,
  isTokenExpired,
  loadRefreshTokenFromStore
} from '@/shared/utils/auth'
import { refreshAccessToken } from '@/shared/utils/session'
import { logout as logoutApi } from '@/shared/api/user'

/**
 * 登录态 store
 *
 * localStorage 是浏览器端的持久化来源，Electron 的 Refresh Token 由主进程
 * safeStorage 持久化；store 只做响应式镜像。request / 路由守卫仍直接读取
 * 工具函数，避免守卫执行时 Pinia 尚未激活。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken() || '')
  const refreshToken = ref(getRefreshToken() || '')
  const userInfo = ref(getUserInfo() || {})

  // Access 过期但还有 Refresh 时仍视为已登录，由 request 层静默续期
  const isLoggedIn = computed(
    () =>
      Boolean(token.value) &&
      (!isTokenExpired(token.value) || Boolean(refreshToken.value))
  )
  // 后端用户类型：1-普通用户，2-管理员，3-VIP
  const isAdmin = computed(() => Number(userInfo.value.userType) === 2)
  const displayName = computed(
    () => userInfo.value.nickname || userInfo.value.username || '用户'
  )

  function setSession(nextToken, nextUserInfo, nextRefreshToken) {
    setAuth(nextToken, nextUserInfo, nextRefreshToken)
    token.value = nextToken || ''
    userInfo.value = nextUserInfo || {}
    if (nextRefreshToken !== undefined) {
      refreshToken.value = nextRefreshToken || ''
    }
  }

  function updateUserInfo(info) {
    setUserInfo(info || {})
    userInfo.value = info || {}
  }

  function clear() {
    clearAuth()
    token.value = ''
    refreshToken.value = ''
    userInfo.value = {}
  }

  function syncFromStorage() {
    token.value = getToken() || ''
    refreshToken.value = getRefreshToken() || ''
    userInfo.value = getUserInfo() || {}
  }

  /**
   * 应用启动时恢复会话：
   * 1. 从安全存储 / localStorage 读取 Refresh Token；
   * 2. Access 仍有效则直接使用；
   * 3. Access 已过期则静默刷新，刷新失败再清理登录态。
   */
  async function bootstrap() {
    await loadRefreshTokenFromStore()
    syncFromStorage()

    const accessToken = token.value
    if (accessToken && !isTokenExpired(accessToken)) {
      return true
    }
    if (!refreshToken.value) {
      if (accessToken) clear()
      return false
    }

    try {
      await refreshAccessToken()
      syncFromStorage()
      return true
    } catch (error) {
      if (error && error.authFailed) {
        clear()
      }
      return false
    }
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 网络异常不影响本地退出
    }
    clear()
  }

  return {
    token,
    refreshToken,
    userInfo,
    isLoggedIn,
    isAdmin,
    displayName,
    setSession,
    updateUserInfo,
    clear,
    bootstrap,
    logout
  }
})
