# 虚拟形象 Agent

全栈像素风虚拟陪伴应用：Vue 3 + PixiJS 前端，Spring Boot + MySQL + Redis 后端。

## 功能现状

- **账号体系**：注册、登录、图形验证码、JWT 鉴权、在线用户、管理员用户管理。
- **像素舞台**：256×256 逻辑分辨率、最近邻采样、CSS 整数倍放大、槽位分层与情绪映射。
- **聊天页**：左侧聊天记录区域，右侧形象舞台；聊天接口尚未接入，当前为占位状态。
- **形象档案**：位于设置页，也可从右上角用户头像的「个人设置」进入。
- **其他模块**：衣橱、任务、记忆簿为静态占位页，等待后端接口就绪后接入。
- **交互**：侧栏可收起/展开，选择会保存在浏览器本地。

## 技术栈

### 前端（`虚拟形象/`）

| 领域 | 选型 |
| --- | --- |
| 框架 | Vue 3（`<script setup>`）+ Vue Router |
| 构建 | Vite 5 |
| 状态 | Pinia 3 |
| UI | Element Plus 2.x |
| 2D 渲染 | PixiJS 8 |
| 请求 | 原生 fetch 封装（统一 401 处理） |

### 后端（`虚拟形象spring/`）

| 领域 | 选型 |
| --- | --- |
| 框架 | Spring Boot 4.1.1 |
| 语言 | Java 17 |
| 数据库 | MySQL 8（Spring Data JDBC） |
| 缓存 | Redis（JWT 白名单、验证码、在线会话） |
| 鉴权 | JWT + Spring Security |

## 目录结构

```
.
├── 虚拟形象/          # 前端（Vue 3 + Vite + PixiJS）
│   ├── src/
│   │   ├── app/       # 入口、路由、应用壳
│   │   ├── features/  # auth / chat / avatar / wardrobe / task / memory / settings / admin
│   │   ├── shared/    # api / stores / utils / ui
│   │   └── styles/
│   └── package.json
└── 虚拟形象spring/    # 后端（Spring Boot Maven 工程）
    └── javaweb/
        ├── src/main/java/com/example/javaweb/
        └── sql/
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

   修改 `spring.datasource.password` 与 `jwt.secret`。

2. 启动：

   ```bash
   cd 虚拟形象spring/javaweb
   ./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
   ```

   默认端口 `1107`。

### 启动前端

```bash
cd 虚拟形象
npm install
npm run dev
```

前端默认 `http://localhost:5173`，接口地址为 `http://localhost:1107`。

## 配置与安全

- `application.properties` 含数据库密码与 JWT 密钥，**已加入 `.gitignore`，请勿提交**；仓库中只保留 `application.properties.example` 模板。
- 日志、`node_modules`、`dist`、`target`、IDE 配置均不会入库。
