import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import fs from 'node:fs'

const packageJson = JSON.parse(
  fs.readFileSync(new URL('./package.json', import.meta.url), 'utf8')
)

// live2d-renderer 源码里的 WebGL shader 是 .vert/.frag 文本，按字符串模块加载
function glslRawPlugin() {
  return {
    name: 'glsl-raw',
    enforce: 'pre',
    load(id) {
      if (id.endsWith('.vert') || id.endsWith('.frag')) {
        return `export default ${JSON.stringify(fs.readFileSync(id, 'utf-8'))}`
      }
    }
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [glslRawPlugin(), vue()],
  define: {
    __APP_VERSION__: JSON.stringify(packageJson.version)
  },
  resolve: {
    alias: [
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
      // live2d-renderer 的构建产物使用 Node path，浏览器端用 polyfill
      { find: /^path$/, replacement: 'path-browserify' },
      // 修正 CJS 动态 import 的 default 导出
      {
        find: /^jszip$/,
        replacement: fileURLToPath(new URL('./src/shims/jszip.js', import.meta.url))
      },
      {
        find: /^magic-bytes\.js$/,
        replacement: fileURLToPath(new URL('./src/shims/magic-bytes.js', import.meta.url))
      }
    ]
  },
  optimizeDeps: {
    include: ['path-browserify'],
    // live2d-renderer 以源码入口（renderer/*.ts）引入，预构建时按字符串处理 shader
    esbuildOptions: {
      loader: {
        '.vert': 'text',
        '.frag': 'text'
      }
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
      input: {
        main: fileURLToPath(new URL('./index.html', import.meta.url)),
        ball: fileURLToPath(new URL('./ball.html', import.meta.url))
      },
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
