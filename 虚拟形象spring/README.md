# 虚拟形象 Agent · 后端

Spring Boot 后端：账号体系、Agent 对话（SSE + 工具调用）、任务、关系养成、人格、通知与 Web Push。

## 技术栈

| 项目 | 版本 / 说明 |
| --- | --- |
| Spring Boot | 4.1.1 |
| Java | 17 |
| 数据库 | MySQL 8 + MyBatis-Plus 3.5.15 |
| 迁移 | Flyway（`db/migration`） |
| 缓存 | Redis（JWT 白名单、验证码、在线会话、搜索缓存） |
| 鉴权 | JWT + Spring Security |
| 实时通信 | SSE（对话流、通知流） |
| 外部服务 | DeepSeek、博查搜索、SMTP、Web Push（VAPID） |

## 目录结构

```text
javaweb/
├── pom.xml
├── mvnw / mvnw.cmd
├── docs/openapi/                    # OpenAPI 3.0 契约
├── sql/                             # 已废弃的历史手工脚本，仅作记录
└── src/
    ├── main/java/com/example/javaweb/
    │   ├── JavawebApplication.java
    │   ├── common/                  # Result、ResultCode、全局异常处理
    │   ├── config/                  # Security、JWT、线程池、外部服务配置
    │   ├── controller/              # 9 个 REST 控制器
    │   ├── dto/                     # request / response / projection / agent
    │   ├── entity/                  # 16 个 MyBatis-Plus 实体
    │   ├── enums/                   # UserStatus、UserType
    │   ├── exception/               # BusinessException、LlmException
    │   ├── mapper/                  # 19 个 Mapper
    │   ├── persona/                 # 预设人格目录
    │   ├── security/                # JWT 过滤器、当前用户解析
    │   ├── service/                 # 认证、Agent、任务、关系、通知等 21 个服务
    │   └── util/                    # JWT、SM3、日期工具
    ├── main/resources/
    │   ├── application.properties.example
    │   └── db/migration/            # V1 基线、V3 种子（V2 为 Java 迁移）
    └── test/
        ├── java/.../JavawebApplicationTests.java
        └── resources/application-test.properties   # H2 内存库，不依赖真实 MySQL/Redis
```

## 快速开始

1. **准备数据库**：创建库 `虚拟形象`（`utf8mb4`）；Redis 默认 `127.0.0.1:6379`。
2. **配置本地参数**：
   ```bash
   cd javaweb/src/main/resources
   cp application.properties.example application.properties
   ```
   至少填写 `DB_PASSWORD` 与 `JWT_SECRET`（≥32 字符）；对话需要 `DEEPSEEK_API_KEY`。
3. **启动**：
   ```bash
   cd javaweb
   ./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
   ```
   默认端口 `1107`。

首次启动时 Flyway 会自动建表并写入人格与阶段策略种子数据。

## 主要接口

| 分组 | 前缀 | 说明 |
| --- | --- | --- |
| 用户 | `/api/user` | 登录、注册、验证码、资料、用户列表、在线用户、找回密码、退出 |
| Agent | `/api/agent` | 会话 CRUD、消息历史、SSE 流式对话、取消生成 |
| 任务 | `/api/tasks`、`/api/task-occurrences` | 任务 CRUD、回应提交 |
| 关系 | `/api/relationship` | 当前关系、成长记录 |
| 人格 | `/api/persona` | 选项、当前人格、保存 |
| 通知 | `/api/notifications` | 列表、未读数、已读、SSE 流、设置、Web Push |

完整契约见 [`docs/openapi/javaweb-api.yaml`](docs/openapi/javaweb-api.yaml)。

## 测试

```bash
./mvnw test
```

`application-test.properties` 使用 H2 内存库并关闭 Flyway，因此测试不依赖本机 MySQL / Redis。

## 安全约定

- `application.properties` 含数据库密码与 JWT 密钥，**已加入 `.gitignore`，请勿提交**。
- 密码：前端 SM3 摘要 → 后端「随机盐 + SM3」存储，常量时间比较。
- Token：JWT + Redis jti 白名单，退出 / 禁用 / 重置密码可立即撤销。
- Agent 工具白名单、参数化 SQL、`userId` 由服务端上下文注入，不信任客户端传参。
- 提交前执行 `git status`，确认没有 `.env`、真实配置、`target/`、日志与 IDE 文件。

## 规划中

- Refresh Token 分环境策略（Electron 1h/30d，浏览器 30min/14d）与轮换。
- 健康检查、结构化日志与指标。
