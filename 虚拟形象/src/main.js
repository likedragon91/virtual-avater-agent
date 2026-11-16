import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '@/styles/index.css'
import '@/styles/pixel.css'
import App from '@/app/App.vue'
import router from '@/app/router'
import { useAuthStore } from '@/shared/stores/auth'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(ElementPlus, { locale: zhCn })

// 先恢复会话（读取 Refresh Token，必要时静默续期），再挂载路由，避免首屏闪登录页
async function start() {
  try {
    await useAuthStore(pinia).bootstrap()
  } catch {
    // 恢复失败不阻塞应用启动，路由守卫会按无会话处理
  }
  app.use(router)
  app.mount('#app')
}

start()
