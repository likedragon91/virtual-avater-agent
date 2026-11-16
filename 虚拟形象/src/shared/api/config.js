// API 与静态资源基础地址。
//
// 开发环境：默认 /api，由 vite.config.js 的 proxy 转发到 127.0.0.1:1107
// 生产环境：默认 /api，由 Nginx 同域反代到后端，无需写死域名
//
// 如需前后端分域部署，可在 .env.production 中设置：
//   VITE_API_BASE_URL=https://api.example.com/api
//   VITE_ASSET_BASE_URL=https://cdn.example.com

const trimTrailingSlash = (value) => String(value || '').replace(/\/+$/, '')

export const API_BASE_URL = trimTrailingSlash(import.meta.env.VITE_API_BASE_URL || '/api')

// 头像等静态资源地址；同域部署留空即可
export const ASSET_BASE_URL = trimTrailingSlash(import.meta.env.VITE_ASSET_BASE_URL || '')
