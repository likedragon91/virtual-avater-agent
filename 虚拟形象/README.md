# 虚拟形象 · 前端

基于 Vue 3 + Vite 的像素风虚拟陪伴前端。一套源码同时服务桌面浏览器与手机浏览器；Electron 与 PWA 为后续规划。

## 技术栈

| 领域 | 选型 |
| --- | --- |
| 框架 | Vue 3（`<script setup>`）+ Vue Router 4 |
| 构建 | Vite 5，Pixi 单独拆 chunk |
| 状态 | Pinia 3（localStorage 是登录态的持久化来源） |
| UI | Element Plus 2.x |
| 2D 渲染 | PixiJS 8（256×256 逻辑分辨率、最近邻采样、CSS 整数倍放大） |
| 请求 | 原生 fetch 封装（统一 401 处理、SSE 流解析） |

## 开发与构建

```bash
npm install
npm run dev            # http://localhost:5173
npm run build          # Web / PWA：dist/
npm run preview

# Electron
npm run electron:dev   # 开发：加载 5173
npm run build:desktop  # 桌面构建：dist-electron/
npm run electron:build # 打包 NSIS：release/虚拟形象 Setup <version>.exe
```

后端默认地址 `http://localhost:1107`，开发环境由 Vite 代理 `/api` 与 `/files`。
Electron 生产构建需要 `.env.desktop`（从 `.env.desktop.example` 复制）并填写后端绝对地址，详见仓库根目录 `docs/desktop.md`。

### 环境变量

复制 `.env.example` 为 `.env.local`（开发）或 `.env.production`（构建）：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/api` | 后端 API 基础路径；同域部署保持默认 |
| `VITE_ASSET_BASE_URL` | 空 | 头像等静态资源域名；同域留空 |

Electron 生产构建使用 `.env.desktop`，其中 `VITE_API_BASE_URL` **必须是绝对地址**（如 `https://avatar.example.com/api`），因为 `file://` 无法解析 `/api`。

## 响应式约定

- 主断点 `900px`，次断点 `768px`，横屏矮视口单独压缩留白。
- 桌面样式不使用 `max-width`，≥901px 时媒体查询不触发，桌面视感不变。
- 窄屏下：
  - 侧栏变为汉堡按钮 + 覆盖式抽屉；
  - 聊天会话列表变为抽屉，虚拟形象变为底部浮层；
  - 表格统一横向滚动；表单标签顶置；弹窗宽度不超过 `92vw`。
- 触控目标 ≥44×44px；输入框字号 ≥16px（避免 iOS 聚焦放大）。

公共规则在 `src/styles/responsive.css`，JS 断点判断用 `src/shared/composables/useMediaQuery.js`。

## 目录结构

```text
src/
├─ main.js                     # 入口：Pinia / Element Plus / Router
├─ app/
│  ├─ App.vue
│  ├─ router/index.js          # 路由表与登录/管理员守卫
│  └─ layouts/MainLayout.vue   # 应用壳：桌面侧栏 / 移动抽屉 + 顶栏
├─ features/
│  ├─ auth/                    # 登录、注册、找回密码
│  ├─ chat/                    # 聊天页（SSE 流、工具调用、会话抽屉）
│  ├─ avatar/                  # 像素舞台、分层、动画、情绪映射
│  ├─ task/                    # 任务与到点回应
│  ├─ relationship/            # 亲密度与成长记录
│  ├─ settings/                # 资料、人格、通知与 Web Push
│  ├─ wardrobe/ memory/        # 占位页（未在主导航展示）
│  └─ admin/                   # 用户管理、在线用户（仅管理员）
├─ shared/
│  ├─ api/                     # request 封装与各业务接口
│  ├─ stores/                  # auth / chat / notification
│  ├─ utils/                   # JWT 解析、SM3、Web Push
│  ├─ composables/             # useMediaQuery
│  └─ ui/PagePlaceholder.vue
└─ styles/                     # 全局样式、响应式、像素风约定
```

## 页面说明

- **聊天页（`/chat`）**：SSE 流式对话、工具调用状态、Markdown 安全渲染、会话管理；右侧为像素舞台。
- **任务页（`/task`）**：任务 CRUD、重复规则、到点回应与完成度修正、关系摘要。
- **关系页（`/relationship`）**：阶段进度、每日上限、成长记录。
- **设置页（`/settings`）**：个人资料、人格与特质、通知/免打扰、Web Push、退出登录。
- **管理页**：仅 `userType === 2` 的管理员可见、可访问。
- **侧栏**：桌面端可折叠并记住选择；窄屏端为抽屉，切换路由自动关闭。

## 与后端的联通

- 登录态保存在 localStorage 的 `token` / `userInfo`；请求统一携带 `Authorization: Bearer`。
- 401 由 `shared/api/request.js` 统一清理登录态并跳转登录页。
- 密码在前端做 SM3 摘要后传输（不是加密替代 TLS）。
- 旧的 `/avatar`、`/home` 地址会重定向到 `/chat`。

## 像素舞台说明

- 逻辑分辨率固定 256×256，`resolution: 1`、关闭 `autoDensity`，仅通过 CSS 整数倍放大。
- 槽位层级：background / hairBack / body / eyes / bottom / dress / top / shoes / hairFront / effect。
- 当前使用 Pixi `Graphics` 程序化绘制占位角色；美术资源就绪后按槽位替换为 Sprite 即可。
- 点击角色会触发眨眼与开心表情，用于验证动画状态机。

## Electron 桌面壳

- 生产环境启动为 72×72 透明置顶悬浮球；主窗口隐藏预加载，保持通知 SSE 在线。
- 点击球心展开 1200×800 主窗口，关闭主窗口回到悬浮球；右键悬浮球可展开或退出。
- 隐藏/失焦时任务提醒走 Windows 原生通知，点击通知展开窗口并进入任务页。
- `contextIsolation + sandbox`，Refresh Token 由主进程 `safeStorage` 加密存储。
- 构建与排错见仓库根目录 [`docs/desktop.md`](../docs/desktop.md)。

## Roadmap

- **P1**：分环境 Refresh Token 静默续期、Electron 悬浮球壳与原生通知（已完成）。
- **P2**：PWA（manifest / 图标 / Service Worker 缓存）、移动端 Web Push 真机验证。
- **后续**：衣橱与记忆簿、i18n、前端单元测试覆盖扩展。
