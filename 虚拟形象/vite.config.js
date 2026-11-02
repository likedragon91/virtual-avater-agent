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
    open: true
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
