# 虚拟形象 Agent

全栈像素风虚拟陪伴应用：一套 Vue 3 前端，三种承载形态（桌面浏览器 / 手机 / Electron 悬浮球），后端为 Spring Boot + MySQL + Redis。

## 承载形态

| 环境 | 说明 | 状态 |
| --- | --- | --- |
| Windows 桌面浏览器 | 现有桌面布局，宽屏三栏 / 双栏 | 已支持 |
| 手机浏览器 | 同一份代码在 ≤900px 断点降级为单列布局 + 抽屉导航 | 已支持 |
| Electron 悬浮球 | 透明置顶悬浮球 + 展开桌面窗口 + Windows 原生通知 | 已支持 |
| PWA | manifest、独立窗口、应用壳缓存与 Web Push | 已支持（需 HTTPS） |

核心原则：桌面版和手机版不是两个项目，而是同一套源码在不同断点下的两种布局。

## 功能现状

- **账号体系**：注册、登录、图形验证码、JWT 鉴权、在线用户、管理员用户管理、个人资料编辑、邮箱找回密码、管理员启用/禁用账号。
- **Agent 对话**：会话管理、SSE 流式输出、工具调用（资料、任务、记忆、联网搜索）、取消生成、Markdown 安全渲染。
- **任务**：增删改查、重复规则（每天/每周/每月）、到点提醒、完成度回应与修正、30 秒轮询。
- **关系养成**：亲密度 0–1000、阶段 S0–S4、每日上限、连击、成长记录。
- **人格**：预设人格、五维特质、关系阶段策略。
- **通知**：站内通知 SSE、通知设置与免打扰、Web Push（VAPID）。
- **管理端**：用户分页查询、启用/禁用、在线会话列表。
- **像素舞台**：256×256 逻辑分辨率、最近邻采样、槽位分层、情绪映射与待机动画。
- **PWA**：manifest、独立窗口、应用壳缓存；Web Push 需 HTTPS，iOS 需 16.4+ 并添加到主屏幕。
- **规划中**：衣橱、记忆簿（路由保留，暂未在主导航展示）。

## 技术栈

### 前端（`虚拟形象/`）

| 领域 | 选型 |
| --- | --- |
| 框架 | Vue 3（`<script setup>`）+ Vue Router 4 |
| 构建 | Vite 5，Pixi 单独拆 chunk |
| 状态 | Pinia 3 |
| UI | Element Plus 2.x |
| 2D 渲染 | PixiJS 8 |
| 请求 | 原生 fetch 封装（统一 401 处理、SSE 解析） |

### 后端（`虚拟形象spring/javaweb/`）

| 领域 | 选型 |
| --- | --- |
| 框架 | Spring Boot 4.1.1 |
| 语言 | Java 17 |
| 数据库 | MySQL 8 + MyBatis-Plus 3.5（Flyway 版本化迁移） |
| 缓存 | Redis（JWT 白名单、验证码、在线会话、搜索缓存） |
| 鉴权 | JWT + Spring Security |
| 实时通信 | SSE（对话流、通知流） |
| 外部服务 | DeepSeek、博查搜索、SMTP、Web Push |

## 目录结构

```text
.
├── 虚拟形象/                 # 前端（Vue 3 + Vite + PixiJS）
│   ├── src/
│   │   ├── app/              # 入口、路由、应用壳
│   │   ├── features/         # auth / chat / avatar / task / relationship / settings / admin
│   │   ├── shared/           # api / stores / utils / composables / ui
│   │   └── styles/           # 全局样式、响应式约定、像素风约定
│   └── package.json
├── 虚拟形象spring/javaweb/   # 后端（Spring Boot Maven 工程）
│   ├── src/main/java/        # controller / service / mapper / entity / dto / security
│   ├── src/main/resources/   # 配置模板、Flyway 迁移
│   ├── docs/openapi/         # 完整 OpenAPI 契约
│   └── sql/                  # 已废弃的历史手工脚本
├── deploy/                   # 部署手册、Nginx、MySQL 备份脚本
├── docs/architecture.md      # 架构与数据流说明
└── .github/workflows/ci.yml  # 后端测试 + 前端构建
```

## 本地运行

### 前置环境

- Node.js 18+
- JDK 17
- MySQL 8（库名 `虚拟形象`，字符集 `utf8mb4`）
- Redis

```sql
CREATE DATABASE `虚拟形象` DEFAULT CHARACTER SET utf8mb4;
```

### 启动后端

1. 复制配置模板并填写真实值：

   ```bash
   cd 虚拟形象spring/javaweb/src/main/resources
   cp application.properties.example application.properties
   ```

   至少配置 `DB_PASSWORD` 与 `JWT_SECRET`（32 字符以上）；对话功能还需要 `DEEPSEEK_API_KEY`。

2. 启动：

   ```bash
   cd 虚拟形象spring/javaweb
   ./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
   ```

   默认端口 `1107`。首次启动由 Flyway 自动建表并写入人格/阶段种子数据。

### 启动前端

```bash
cd 虚拟形象
npm install
npm run dev
```

前端默认 `http://localhost:5173`，`/api` 由 Vite 代理到 `http://localhost:1107`。

### 启动 Electron 桌面壳

```bash
cd 虚拟形象
npm run electron:dev     # 开发：加载 5173
npm run electron:build   # 打包：release/虚拟形象 Setup <version>.exe
```

生产构建前需复制 `.env.desktop.example` 为 `.env.desktop` 并填写后端绝对地址。详见 [`docs/desktop.md`](docs/desktop.md)。

### PWA

- 生产构建后通过 HTTPS 访问，浏览器出现「安装应用」入口。
- Android Chrome 可直接安装并接收 Web Push；iOS 16.4+ 需先「添加到主屏幕」再授权通知。
- `public/sw.js` 负责应用壳缓存与推送接收；开发环境不注册 SW。

### 环境变量（后端）

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `DB_PASSWORD` | 是 | MySQL 密码 |
| `JWT_SECRET` | 是 | JWT 签名密钥，至少 32 字符 |
| `DEEPSEEK_API_KEY` | 对话功能 | DeepSeek 密钥 |
| `BOCHA_API_KEY` | 联网搜索 | 博查搜索密钥 |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | 找回密码 | SMTP 配置 |
| `WEB_PUSH_PUBLIC_KEY` / `WEB_PUSH_PRIVATE_KEY` | Web Push | VAPID 密钥 |

## 测试与构建

```bash
# 后端（使用 H2 测试 profile，不依赖真实 MySQL/Redis）
cd 虚拟形象spring/javaweb
./mvnw test

# 前端
cd 虚拟形象
npm run build
```

CI 会在 push / PR 时执行后端测试与前端构建。

## 配置与安全

- `application.properties` 含数据库密码与 JWT 密钥，**已加入 `.gitignore`，请勿提交**；仓库中只保留 `application.properties.example`。
- 密码在前端做 SM3 摘要后传输，后端使用「随机盐 + SM3」存储并以常量时间比较。
- JWT 通过 Redis jti 白名单支持退出登录、管理员禁用与重置密码后的即时撤销。
- 日志、`node_modules`、`dist`、`target`、IDE 配置均不会入库。

## 文档

- 架构与数据流：[`docs/architecture.md`](docs/architecture.md)
- Electron 桌面壳：[`docs/desktop.md`](docs/desktop.md)
- 接口契约：[`虚拟形象spring/javaweb/docs/openapi/javaweb-api.yaml`](虚拟形象spring/javaweb/docs/openapi/javaweb-api.yaml)
- 部署手册：[`deploy/README.md`](deploy/README.md)
- 产品需求：[`虚拟形象spring/javaweb/需求文档.md`](虚拟形象spring/javaweb/需求文档.md)

## Roadmap

1. **P0**：仓库可复现 + 全局响应式改造（已完成）。
2. **P1**：Refresh Token 分环境策略、Electron 悬浮球与 Windows 原生通知（已完成）。
3. **P2**：PWA 可安装、应用壳缓存、前后端最小测试与文档完善（已完成；移动端 Web Push 需真机验证）。
4. **后续**：衣橱与记忆簿功能、Docker Compose 部署、i18n。

演示流程见 [`docs/demo.md`](docs/demo.md)。
