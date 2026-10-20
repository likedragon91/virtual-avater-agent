# javaweb 课程 Spring 项目

基于 Spring Boot 的用户管理模块，包含注册、登录、JWT 鉴权与统一响应封装。

## 技术栈

| 项目 | 版本 / 说明 |
| --- | --- |
| Spring Boot | 4.1.1 |
| Java | 17 |
| 数据库 | MySQL（`spring-boot-starter-data-jdbc`） |
| 鉴权 | JWT + Spring Security |
| 工具库 | Hutool |

## 目录结构

```
javaweb课程spring/
├── .gitignore
├── README.md
├── .idea_backup/                 # IDE 自动备份，不纳入版本控制
└── javaweb/                      # Maven 工程
    ├── pom.xml
    ├── mvnw / mvnw.cmd           # Maven Wrapper
    ├── .gitignore
    └── src/
        ├── main/java/com/example/javaweb/
        │   ├── JavawebApplication.java
        │   ├── common/           # Result、ResultCode、全局异常处理
        │   ├── config/           # SecurityConfig、JWTConfig
        │   ├── controller/       # User、Test
        │   ├── DTO/              # 登录/注册的请求与响应对象
        │   ├── entity/           # user 实体
        │   ├── enumClass/        # UserStatus、UserType
        │   ├── exception/        # BusinessException
        │   ├── filter/           # JwtAuthenticationFilter
        │   ├── mapper/           # UserMapper
        │   ├── Service/          # UserService
        │   └── util/             # JWTTokenUtil
        ├── main/resources/
        │   ├── application.properties          # 本地配置，已被忽略
        │   └── application.properties.example  # 配置模板
        └── test/java/com/example/javaweb/
```

## 快速开始

1. **准备数据库**：在本地 MySQL 中创建库 `driving_school`。
2. **配置本地参数**：
   ```bash
   cd javaweb/src/main/resources
   cp application.properties.example application.properties   # Windows 用 copy
   ```
   然后把 `spring.datasource.password` 和 `jwt.secret` 改成本机真实值。
3. **启动**：
   ```bash
   cd javaweb
   ./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
   ```
   默认端口 `1107`（见 `server.port`）。

## 安全约定

- `application.properties` 含数据库密码与 JWT 密钥，**已加入 .gitignore，请勿提交**。
- 提交前建议执行 `git status` 确认没有 `.idea/`、`target/`、`*.log` 和真实配置文件。
