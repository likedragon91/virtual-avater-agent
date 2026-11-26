# Electron 桌面壳

Electron 只做启动器与外壳，业务 UI 仍是同一套 Vue 源码构建出的本地静态资源。

## 结构

```text
虚拟形象/
├── electron/
│   ├── main.cjs          # 悬浮球 + 主窗口、IPC、原生通知、safeStorage
│   ├── preload.cjs       # contextBridge 白名单 API
│   ├── ball.html/.css/.js# 72×72 透明悬浮球
│   └── assets/icon.ico   # 应用图标（scripts/generate-icon.mjs 生成）
├── electron-builder.yml  # NSIS x64 安装包配置
└── .env.desktop.example  # 生产构建必须复制的绝对 API 地址模板
```

## 开发

```bash
cd 虚拟形象
npm install
npm run electron:dev
```

- Vite 开发服务器保持在 `127.0.0.1:5173`，Electron 开发态直接加载该地址。
- 开发态会自动打开主窗口并隐藏悬浮球，方便调试。
- 国内网络下载 Electron 二进制失败时，可先执行：
  ```bash
  ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/ node node_modules/electron/install.js
  ```

## 生产构建

```bash
cd 虚拟形象
cp .env.desktop.example .env.desktop   # 填入后端绝对地址
npm run electron:build
```

产物：`release/虚拟形象 Setup <version>.exe`（NSIS 安装包）与 `release/win-unpacked/`。

必须满足三个条件，否则会白屏或接口 404：

1. `vite build --base=./`（脚本已内置）；
2. `file://` 下使用 Hash 路由（`src/app/router/index.js` 已按协议自动切换）；
3. `VITE_API_BASE_URL` 是绝对地址（`.env.desktop`）。

云端尚未上 HTTPS 时填 `http://<SERVER_IP>/api`；完成域名 + HTTPS 后改为 `https://<域名>/api` 并重新打包。安装包会把该地址烘入产物，分发前务必确认。

打包时默认使用 `node_modules/electron/dist`，避免重复下载。若 electron-builder 仍需要下载 NSIS 组件，可设置：

```bash
ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/
```

## 运行行为

- 生产环境启动后只显示悬浮球；主窗口会以隐藏状态预先创建，保持通知 SSE 与定时器在线。
- 点击球心小人（或双击球体）展开主窗口；主窗口关闭时回到悬浮球。
- 悬浮球右键菜单提供「展开虚拟形象」与「退出」。
- 主窗口最小尺寸 `900×640`，保证桌面布局断点不触发。
- 二次启动会唤起已有实例（单实例锁）。
- 主窗口聚焦时通知走站内弹窗；隐藏/失焦时通过 IPC 调用 Windows 原生通知，点击通知会展开窗口并跳转任务页。

## 检查更新与版本

- 版本号来源：`虚拟形象/package.json` 的 `version`。构建时写入前端全局 `__APP_VERSION__`；
  Electron 运行时通过 `app.getVersion()`（preload 暴露为 `desktopBridge.appVersion()`）读取。
- 服务器更新（后端 / 网页 `dist`）**不会修改已安装的 Electron 包**；桌面端需要下载并安装新包。
- 管理员在网页端「管理 → 应用更新」上传新的 `虚拟形象 Setup <version>.exe` 并发布，
  系统会向所有启用用户推送 `APP_UPDATE` 通知。
- 用户在左侧栏底部「检查更新」（或设置页）查看当前/最新版本、更新说明并下载安装包。
- 首次启用更新功能仍需手动分发新安装包；之后的版本可以通过「检查更新」页下载。
- 发布前先提升 `package.json` 版本并重新打包；管理员填写的版本号必须与安装包一致。
- `.env.desktop` 的 API 地址在打包时写死；服务器域名/IP 变化后必须重新打包。

## 安全设计

- `contextIsolation: true`、`sandbox: true`、`nodeIntegration: false`。
- preload 只暴露 `expand` / `collapse` / `notify` / `secureStore` 等白名单能力。
- Refresh Token 通过主进程 `safeStorage` 加密后写入 `userData/secure-store.json`，渲染进程不持久化明文。
- 外部链接只允许 `http(s)` 并交给系统浏览器；应用内导航限制在本地 `file://` 或开发地址。
- `win.webContents.setWindowOpenHandler` 拒绝新窗口。

## 常见问题

| 现象 | 原因 / 处理 |
| --- | --- |
| 安装后白屏 | `.env.desktop` 未配置或 API 地址不是绝对地址；重新 `npm run build:desktop` |
| 接口 404 | 后端路径本身含 `/api`，`.env.desktop` 应写 `https://域名/api` |
| 收不到通知 | 检查 Windows 专注助手/勿扰、应用通知权限；打包应用需 AppUserModelID（已设置） |
| 接口被 CORS 拦截 | Electron `file://` 的 Origin 为 `null`，后端需允许；当前 CORS 为 `*` |
| 安装时 SmartScreen 警告 | 未配置代码签名证书，属预期行为 |
| 图标未更新 | 重新运行 `node scripts/generate-icon.mjs` 后重新打包 |
