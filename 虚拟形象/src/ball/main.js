import { createApp } from 'vue'
import { createPinia } from 'pinia'
import BallApp from './BallApp.vue'

// 独立入口：只加载形象渲染所需的最小依赖，不挂路由与主布局
createApp(BallApp).use(createPinia()).mount('#app')
