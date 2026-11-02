# 虚拟形象 · 前端

基于 Vue 3 + Vite 的像素风虚拟形象前端。当前阶段完成了技术栈重构、应用壳与像素舞台：
聊天页左侧为聊天记录、右侧为形象舞台；衣橱 / 任务 / 记忆等模块预留为静态占位页，
等后端接口就绪后接入。

## 技术栈

| 领域 | 选型 |
| --- | --- |
| 框架 | Vue 3（`<script setup>`）+ Vue Router |
| 构建 | Vite 5，Pixi 单独拆 chunk |
| 状态 | Pinia 3（localStorage 仍是登录态的唯一持久化来源） |
| UI | Element Plus 2.x |
| 2D 渲染 | PixiJS 8（256×256 逻辑分辨率、最近邻采样、CSS 整数倍放大） |
| 请求 | 原生 fetch 封装（保留原有 401 统一处理与 JWT 逻辑） |

## 开发与构建

```bash
npm install
npm run dev
npm run build
npm run preview
```

后端默认地址为 `http://localhost:1107`，与 `虚拟形象spring` 项目保持一致。

## 目录结构

```
src/
├─ main.js                     # 入口：Pinia / Element Plus / Router
├─ app/
│  ├─ App.vue
│  ├─ router/index.js          # 路由表与登录/管理员守卫
│  └─ layouts/MainLayout.vue   # 应用壳：可隐藏侧栏 + 顶栏 + 内容区
├─ features/
│  ├─ auth/                    # 登录、注册
│  ├─ chat/                    # 聊天页（左侧聊天记录 + 右侧像素舞台）
│  ├─ avatar/                  # 像素舞台、分层、动画、情绪映射
│  ├─ wardrobe/ task/ memory/  # 占位页
│  ├─ settings/                # 形象档案、账号信息、退出登录
│  └─ admin/                   # 用户管理、在线用户（仅管理员）
├─ shared/
│  ├─ api/                     # request 封装与用户接口
│  ├─ stores/auth.js           # Pinia 登录态镜像
│  ├─ utils/                   # JWT 解析、SM3
│  └─ ui/PagePlaceholder.vue
└─ styles/                     # 全局样式与像素风约定
```

## 页面说明

- **聊天页（`/chat`）**：左侧聊天记录区域（接口未接入时展示占位状态），右侧为形象舞台。
- **侧栏**：顶栏左上角的按钮可以隐藏 / 展开侧栏，选择会保存在浏览器本地。
- **形象档案**：位于设置页（`/settings`），也可点击右上角用户头像 →「个人设置」进入。
- **管理页面**：仅 `userType === 2` 的管理员可见、可访问。

## 与后端的联通

- 接口地址、请求方法、SM3 密码摘要、JWT 白名单与 401 处理逻辑与原版本保持一致。
- localStorage 键仍为 `token` / `userInfo`，刷新页面后登录态不丢失。
- 旧的 `/avatar`、`/home` 地址都会重定向到 `/chat`。

## 像素舞台说明

- 逻辑分辨率固定 256×256，`resolution: 1`、关闭 `autoDensity`，仅通过 CSS 整数倍放大。
- 槽位层级：background / hairBack / body / eyes / bottom / dress / top / shoes / hairFront / effect。
- 当前使用 Pixi `Graphics` 程序化绘制占位角色；美术资源就绪后按槽位替换为 Sprite 即可。
- 点击角色会触发眨眼与开心表情，用于验证动画状态机。
