import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getToken,
  getUserInfo,
  setAuth,
  setUserInfo,
  clearAuth,
  isTokenExpired
} from '@/shared/utils/auth'
import { logout as logoutApi } from '@/shared/api/user'

/**
 * 登录态 store
 *
 * localStorage 是唯一的持久化来源，store 只做响应式镜像；
 * request / 路由守卫仍然直接读取工具函数，避免守卫执行时 Pinia 尚未激活，
 * 也避免 request -> router -> store -> api -> request 的循环依赖。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken() || '')
  const userInfo = ref(getUserInfo() || {})

  const isLoggedIn = computed(() => Boolean(token.value) && !isTokenExpired(token.value))
  // 后端用户类型：1-普通用户，2-管理员，3-VIP
  const isAdmin = computed(() => Number(userInfo.value.userType) === 2)
  const displayName = computed(
    () => userInfo.value.nickname || userInfo.value.username || '用户'
  )

  function setSession(nextToken, nextUserInfo) {
    setAuth(nextToken, nextUserInfo)
    token.value = nextToken || ''
    userInfo.value = nextUserInfo || {}
  }

  function updateUserInfo(info) {
    setUserInfo(info || {})
    userInfo.value = info || {}
  }

  function clear() {
    clearAuth()
    token.value = ''
    userInfo.value = {}
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
    userInfo,
    isLoggedIn,
    isAdmin,
    displayName,
    setSession,
    updateUserInfo,
    clear,
    logout
  }
})
