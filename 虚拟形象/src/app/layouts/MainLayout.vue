<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { UserFilled, SwitchButton, ChatDotRound, ShoppingBag, Calendar, Notebook, Setting, User, Monitor, Fold, Expand } from '@element-plus/icons-vue'
import { getUserMessage, BASE_URL } from '@/shared/api/user'
import { useAuthStore } from '@/shared/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)

// 侧栏可隐藏，选择结果保存在本地
const SIDEBAR_KEY = 'ui:sidebarCollapsed'
const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_KEY) === '1')

watch(sidebarCollapsed, (value) => {
  localStorage.setItem(SIDEBAR_KEY, value ? '1' : '0')
})

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// 后端返回的 avatar 可能是相对路径（如 /files/...），拼接后端地址
const avatarUrl = computed(() => {
  const avatar = authStore.userInfo.avatar
  if (!avatar) return ''
  return /^https?:\/\//.test(avatar) ? avatar : `${BASE_URL}${avatar}`
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

function handleCommand(command) {
  if (command === 'settings') router.push('/settings')
  if (command === 'logout') logout()
}

async function logout() {
  // 通知后端删除 Redis 白名单中的 token 并移除在线会话；网络异常不影响本地退出
  await authStore.logout()
  router.replace('/login')
}

onMounted(loadCurrentUser)
</script>

<template>
  <div class="common-layout">
    <el-container class="layout-container">
      <el-header class="layout-header">
        <div class="header-left">
          <el-tooltip
            :content="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
            placement="bottom"
          >
            <el-button text class="sidebar-toggle" @click="toggleSidebar">
              <el-icon :size="18">
                <Expand v-if="sidebarCollapsed" />
                <Fold v-else />
              </el-icon>
            </el-button>
          </el-tooltip>
          <div class="header-brand">
            <span class="brand-dot" />
            虚拟形象
          </div>
        </div>
        <div class="header-right">
          <span class="header-username">{{ authStore.displayName }}</span>
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
        <el-aside
          :width="sidebarCollapsed ? '0px' : '220px'"
          class="layout-aside"
          :class="{ 'is-collapsed': sidebarCollapsed }"
          :inert="sidebarCollapsed"
          :aria-hidden="sidebarCollapsed"
        >
          <el-menu :default-active="activeMenu" router class="aside-menu">
            <el-menu-item index="/chat">
              <el-icon><ChatDotRound /></el-icon>
              <span>聊天</span>
            </el-menu-item>
            <el-menu-item index="/wardrobe">
              <el-icon><ShoppingBag /></el-icon>
              <span>衣橱</span>
            </el-menu-item>
            <el-menu-item index="/task">
              <el-icon><Calendar /></el-icon>
              <span>任务</span>
            </el-menu-item>
            <el-menu-item index="/memory">
              <el-icon><Notebook /></el-icon>
              <span>记忆</span>
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
            </template>
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

.layout-container {
  height: 100%;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.layout-body {
  height: calc(100% - 60px);
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
  border-right: none;
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
</style>
