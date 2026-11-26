<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import { UserFilled, SwitchButton, ChatDotRound, Calendar, Setting, User, Monitor, Fold, Expand, Star, Bell, Menu, Brush, Refresh, Upload } from '@element-plus/icons-vue'
import { getUserMessage } from '@/shared/api/user'
import { ASSET_BASE_URL } from '@/shared/api/config'
import { useMediaQuery } from '@/shared/composables/useMediaQuery'
import { isDesktopApp, notifySystem, onSystemNotificationClick } from '@/shared/utils/desktop-notify'
import { useAuthStore } from '@/shared/stores/auth'
import { useNotificationStore } from '@/shared/stores/notification'
import { useUiStore } from '@/shared/stores/ui'
import ChatComposer from '@/features/chat/components/ChatComposer.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const uiStore = useUiStore()
let stopPanelMode = null
const notificationPopoverRef = ref()

// 面板模式要在首帧就确定：新建面板窗口时先拿到同步值，避免闪一下当前路由页面
const initialPanelMode = window.desktopBridge?.getPanelModeSync?.()
if (typeof initialPanelMode === 'boolean') {
  uiStore.setPanelMode(initialPanelMode)
}

const activeMenu = computed(() => route.path)

// 侧栏可隐藏，选择结果保存在本地
const SIDEBAR_KEY = 'ui:sidebarCollapsed'
const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_KEY) === '1')

// 窄屏下侧栏改为抽屉，展开状态不持久化
const { matches: isMobile } = useMediaQuery('(max-width: 900px)')
const mobileSidebarOpen = ref(false)

watch(sidebarCollapsed, (value) => {
  localStorage.setItem(SIDEBAR_KEY, value ? '1' : '0')
})

// 切换路由或回到桌面尺寸时收起抽屉
watch(() => route.path, () => {
  mobileSidebarOpen.value = false
})

watch(isMobile, (mobile) => {
  if (!mobile) mobileSidebarOpen.value = false
})

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function toggleNav() {
  if (isMobile.value) {
    mobileSidebarOpen.value = !mobileSidebarOpen.value
    return
  }
  toggleSidebar()
}

// 后端返回的 avatar 可能是相对路径（如 /files/...），拼接后端地址
const avatarUrl = computed(() => {
  const avatar = authStore.userInfo.avatar
  if (!avatar) return ''
  return /^https?:\/\//.test(avatar) ? avatar : `${ASSET_BASE_URL}${avatar}`
})

// 获取当前登录用户信息，刷新本地缓存
async function loadCurrentUser() {
  try {
    const res = await getUserMessage()
    if (res && res.code === '200') {
      authStore.updateUserInfo(res.data || {})
    }
  } catch (e) {
    // 401 已由 request 统一处理并跳转；其它异常保留本地缓存，不阻塞页面
    if (e && e.isAuthError) return
  }
}

const pad = (value) => String(value).padStart(2, '0')

function formatNotificationTime(text) {
  if (!text) return ''
  const date = new Date(String(text).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const diff = (now.getTime() - date.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (date.toDateString() === now.toDateString()) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  return `${date.getMonth() + 1}-${date.getDate()} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function handleNotificationClick(item) {
  await notificationStore.markRead(item.id)
  notificationPopoverRef.value?.hide()
  if (item.type === 'TASK_REMINDER') {
    router.push('/task')
  }
  if (item.type === 'APP_UPDATE') {
    router.push('/update')
  }
}

function handleMarkAllRead() {
  notificationStore.markAllRead()
}

function handleCommand(command) {
  if (command === 'settings') router.push('/settings')
  if (command === 'logout') logout()
}

async function logout() {
  // 通知后端删除 Redis 白名单中的 token 并移除在线会话；网络异常不影响本地退出
  await authStore.logout()
  router.replace('/login')
}

// 主窗口聚焦时用站内弹窗；隐藏到悬浮球或失焦时走 Windows 原生通知
function windowIsActive() {
  return document.visibilityState === 'visible' && document.hasFocus()
}

let stopSystemNotificationClick = () => {}

onMounted(async () => {
  if (window.desktopBridge?.getPanelMode) {
    const panelMode = await window.desktopBridge.getPanelMode().catch(() => false)
    uiStore.setPanelMode(Boolean(panelMode))
  }
  stopPanelMode = window.desktopBridge?.onPanelMode?.((value) => {
    uiStore.setPanelMode(value)
  })
  await loadCurrentUser()
  await notificationStore.loadUnread()
  notificationStore.startStream((notification) => {
    if (isDesktopApp() && !windowIsActive()) {
      notifySystem(notification)
      return
    }
    ElNotification({
      title: notification.title,
      message: notification.body,
      type: 'warning',
      duration: 8000,
      onClick: () => {
        notificationStore.markRead(notification.id)
        router.push(notification.type === 'APP_UPDATE' ? '/update' : '/task')
      }
    })
  })

  // 点击系统通知后：主进程展开窗口并回传通知信息
  stopSystemNotificationClick = onSystemNotificationClick((payload) => {
    if (!payload) return
    if (payload.id) notificationStore.markRead(payload.id)
    if (payload.type === 'TASK_REMINDER') router.push('/task')
    if (payload.type === 'APP_UPDATE') router.push('/update')
  })
})

onBeforeUnmount(() => {
  stopSystemNotificationClick()
  stopPanelMode?.()
  notificationStore.stopStream()
})
</script>

<template>
  <div class="common-layout" :class="{ 'is-panel': uiStore.panelMode }">
    <!-- 左侧对话框：主窗口只渲染聊天输入框，内容与当前路由无关 -->
    <div v-if="uiStore.panelMode" class="panel-shell">
      <ChatComposer panel autofocus />
    </div>
    <el-container v-else class="layout-container">
      <el-header class="layout-header">
        <div class="header-left">
          <el-tooltip
            v-if="!isMobile"
            :content="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
            placement="bottom"
          >
            <el-button text class="sidebar-toggle touch-target" @click="toggleNav">
              <el-icon :size="18">
                <Expand v-if="sidebarCollapsed" />
                <Fold v-else />
              </el-icon>
            </el-button>
          </el-tooltip>
          <el-button
            v-else
            text
            class="sidebar-toggle touch-target"
            aria-label="打开导航"
            :aria-expanded="mobileSidebarOpen"
            @click="toggleNav"
          >
            <el-icon :size="20"><Menu /></el-icon>
          </el-button>
          <div class="header-brand">
            <span class="brand-dot" />
            虚拟形象
          </div>
        </div>
        <div class="header-right">
          <el-popover
            ref="notificationPopoverRef"
            placement="bottom-end"
            :width="isMobile ? 300 : 360"
            trigger="click"
            popper-class="notification-popper"
            @show="notificationStore.loadList()"
          >
            <template #reference>
              <el-badge
                :value="notificationStore.unreadCount"
                :max="99"
                :hidden="!notificationStore.unreadCount"
              >
                <el-button
                  text
                  circle
                  :icon="Bell"
                  class="notification-bell"
                  aria-label="通知"
                />
              </el-badge>
            </template>

            <div class="notification-panel">
              <div class="notification-head">
                <span class="notification-head-title">通知</span>
                <el-button
                  link
                  type="primary"
                  size="small"
                  :disabled="!notificationStore.unreadCount"
                  @click="handleMarkAllRead"
                >
                  全部已读
                </el-button>
              </div>
              <div v-if="notificationStore.loading" class="notification-empty">
                加载中…
              </div>
              <div v-else-if="!notificationStore.notifications.length" class="notification-empty">
                暂无通知
              </div>
              <div v-else class="notification-list">
                <div
                  v-for="item in notificationStore.notifications"
                  :key="item.id"
                  class="notification-item"
                  :class="{ unread: !item.read }"
                  @click="handleNotificationClick(item)"
                >
                  <div class="notification-item-title">{{ item.title }}</div>
                  <div class="notification-item-body">{{ item.body }}</div>
                  <div class="notification-item-time">
                    {{ formatNotificationTime(item.createdAt) }}
                  </div>
                </div>
              </div>
            </div>
          </el-popover>

          <span class="header-username hide-on-narrow">{{ authStore.displayName }}</span>
          <el-dropdown trigger="click" @command="handleCommand">
            <el-avatar
              :icon="!avatarUrl ? UserFilled : undefined"
              :src="avatarUrl || undefined"
              :size="36"
              class="header-avatar"
            />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="settings">
                  <el-icon><Setting /></el-icon>
                  个人设置
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-container class="layout-body">
        <div
          v-if="isMobile && mobileSidebarOpen"
          class="sidebar-backdrop"
          aria-hidden="true"
          @click="mobileSidebarOpen = false"
        />
        <el-aside
          :width="isMobile ? '240px' : (sidebarCollapsed ? '0px' : '220px')"
          class="layout-aside"
          :class="{
            'is-collapsed': !isMobile && sidebarCollapsed,
            'is-mobile': isMobile,
            'is-open': isMobile && mobileSidebarOpen
          }"
          :inert="isMobile ? !mobileSidebarOpen : sidebarCollapsed"
          :aria-hidden="isMobile ? !mobileSidebarOpen : sidebarCollapsed"
        >
          <el-menu :default-active="activeMenu" router class="aside-menu">
            <el-menu-item index="/chat">
              <el-icon><ChatDotRound /></el-icon>
              <span>聊天</span>
            </el-menu-item>
            <el-menu-item index="/task">
              <el-icon><Calendar /></el-icon>
              <span>任务</span>
            </el-menu-item>
            <el-menu-item index="/relationship">
              <el-icon><Star /></el-icon>
              <span>关系</span>
            </el-menu-item>
            <el-menu-item index="/wardrobe">
              <el-icon><Brush /></el-icon>
              <span>衣橱</span>
            </el-menu-item>
            <el-menu-item index="/settings">
              <el-icon><Setting /></el-icon>
              <span>设置</span>
            </el-menu-item>

            <template v-if="authStore.isAdmin">
              <div class="menu-divider">管理</div>
              <el-menu-item index="/admin/users">
                <el-icon><User /></el-icon>
                <span>用户管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/online">
                <el-icon><Monitor /></el-icon>
                <span>在线用户</span>
              </el-menu-item>
              <el-menu-item index="/admin/update">
                <el-icon><Upload /></el-icon>
                <span>应用更新</span>
              </el-menu-item>
            </template>
            <el-menu-item index="/update" class="menu-update">
              <el-icon><Refresh /></el-icon>
              <span>检查更新</span>
            </el-menu-item>
          </el-menu>
        </el-aside>

        <el-main class="layout-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<style scoped>
.common-layout {
  height: 100vh;
}

/* 左侧对话框模式：主窗口只保留聊天输入框 */
.common-layout.is-panel {
  height: 100vh;
  overflow: hidden;
  background: #fff;
}

.panel-shell {
  display: flex;
  width: 100%;
  height: 100%;
}

.panel-shell :deep(.composer) {
  flex: 1;
}

.layout-container {
  --app-header-height: 60px;
  height: 100%;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--app-header-height);
  background: #fff;
  border-bottom: 1px solid var(--el-border-color);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.sidebar-toggle {
  padding: 8px;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}

.brand-dot {
  width: 12px;
  height: 12px;
  background: #4f86c6;
  box-shadow: 8px 0 0 #f2a6b8, 0 8px 0 #f2c14e;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-username {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.header-avatar {
  cursor: pointer;
}

.notification-bell {
  padding: 6px;
}

.layout-body {
  height: calc(100% - var(--app-header-height));
}

.layout-aside {
  background: #fff;
  border-right: 1px solid var(--el-border-color);
  overflow: hidden;
  transition: width 0.2s ease;
}

.layout-aside.is-collapsed {
  border-right: none;
}

.aside-menu {
  display: flex;
  flex-direction: column;
  height: 100%;
  border-right: none;
}

.aside-menu :deep(.menu-update) {
  margin-top: auto;
}

.menu-divider {
  padding: 16px 20px 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.layout-main {
  background: #f5f7fa;
  overflow-y: auto;
}

/* 窄屏：侧栏改为覆盖式抽屉，桌面布局不受影响 */
@media (max-width: 900px) {
  .layout-aside.is-mobile {
    position: fixed;
    top: var(--app-header-height);
    bottom: 0;
    left: 0;
    z-index: 2100;
    width: 240px !important;
    border-right: 1px solid var(--el-border-color);
    overflow-y: auto;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }

  .layout-aside.is-mobile.is-open {
    transform: translateX(0);
    box-shadow: 4px 0 16px rgba(15, 23, 42, 0.18);
  }

  .sidebar-backdrop {
    position: fixed;
    top: var(--app-header-height);
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 2000;
    background: rgba(15, 23, 42, 0.35);
  }

  .aside-menu :deep(.el-menu-item) {
    min-height: 48px;
  }
}

/* 横屏矮视口：压缩头部高度，给内容让出空间 */
@media (orientation: landscape) and (max-height: 500px) {
  .layout-container {
    --app-header-height: 48px;
  }
}
</style>

<style>
.notification-popper {
  padding: 0 !important;
  max-width: calc(100vw - 24px);
}

.notification-panel {
  display: flex;
  flex-direction: column;
  max-height: 420px;
}

.notification-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.notification-head-title {
  font-weight: 600;
}

.notification-empty {
  padding: 28px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.notification-list {
  max-height: 360px;
  overflow-y: auto;
}

.notification-item {
  padding: 12px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}

.notification-item:hover {
  background: var(--el-fill-color-light);
}

.notification-item.unread {
  background: var(--el-color-primary-light-9);
}

.notification-item-title {
  font-size: 13px;
  font-weight: 600;
}

.notification-item-body {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.notification-item-time {
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
