import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken, getUserInfo, clearAuth, isTokenExpired } from '@/shared/utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/features/auth/views/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/features/auth/views/RegisterView.vue'),
    meta: { title: '注册', public: true }
  },
  {
    path: '/',
    component: () => import('@/app/layouts/MainLayout.vue'),
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/features/chat/views/ChatView.vue'),
        meta: { title: '聊天' }
      },
      {
        path: 'wardrobe',
        name: 'Wardrobe',
        component: () => import('@/features/wardrobe/views/WardrobeView.vue'),
        meta: { title: '衣橱' }
      },
      {
        path: 'task',
        name: 'Task',
        component: () => import('@/features/task/views/TaskView.vue'),
        meta: { title: '任务' }
      },
      {
        path: 'memory',
        name: 'Memory',
        component: () => import('@/features/memory/views/MemoryView.vue'),
        meta: { title: '记忆' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/features/settings/views/SettingsView.vue'),
        meta: { title: '设置' }
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('@/features/admin/views/UserManageView.vue'),
        meta: { title: '用户管理', requiresAdmin: true }
      },
      {
        path: 'admin/online',
        name: 'AdminOnline',
        component: () => import('@/features/admin/views/OnlineUsersView.vue'),
        meta: { title: '在线用户', requiresAdmin: true }
      }
    ]
  },
  // 兼容旧入口：形象已合并到聊天页
  { path: '/avatar', redirect: '/chat' },
  { path: '/home', redirect: '/chat' },
  { path: '/:pathMatch(.*)*', redirect: '/chat' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由前置守卫
router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 虚拟形象` : '虚拟形象'

  const token = getToken()
  const isPublic = Boolean(to.meta.public)

  // 1. 未登录或 token 已过期：只允许访问登录/注册页
  //    token 是否被服务端撤销由接口返回 401 时统一处理
  if (!token || isTokenExpired(token)) {
    if (token) clearAuth()
    if (isPublic) return true
    const query = to.path !== '/chat' ? { redirect: to.fullPath } : undefined
    return { path: '/login', query }
  }

  // 2. 已登录：校验 userInfo 是否有效
  const userInfo = getUserInfo()
  const userType = userInfo ? Number(userInfo.userType) : null

  // 数据异常：清除失效数据，回到登录页
  if (!userInfo || (userType !== 1 && userType !== 2 && userType !== 3)) {
    clearAuth()
    return '/login'
  }

  // 已登录还想去登录/注册页 -> 去聊天页
  if (isPublic) return '/chat'

  // 3. 管理员页面权限校验
  if (to.meta.requiresAdmin && userType !== 2) {
    ElMessage.warning('没有访问权限，仅管理员可访问')
    return '/chat'
  }

  return true
})

export default router
