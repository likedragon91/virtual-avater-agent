import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: true,
    proxy: {
      // 后端控制器路径本身包含 /api，这里保持原样转发，不做 rewrite
      '/api': {
        target: 'http://127.0.0.1:1107',
        changeOrigin: true
      },
      '/files': {
        target: 'http://127.0.0.1:1107',
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          // 把 Pixi 拆成独立 chunk，避免主包过大
          if (id.includes('node_modules/pixi.js') || id.includes('node_modules/@pixi')) {
            return 'pixi'
          }
        }
      }
    }
  }
})
