# 接口契约与 Apifox 交换文件

本目录存放可导入 Apifox 的 OpenAPI 文件。

## 文件说明

| 文件 | 范围 |
|---|---|
| `javaweb-api.yaml` | **全量接口**：账号、验证码、Agent 会话、任务与闹钟、亲密度、人格、通知、通知设置、Web Push。前置 URL 已写为 `http://127.0.0.1:1107/api`。 |
| `agent-workbench.yaml` | 仅 `/api/agent/**` 的 Agent 会话契约（`javaweb-api.yaml` 已包含这部分）。 |

导入 `javaweb-api.yaml` 即可一次性获得项目全部 38 个接口。

## 单一事实来源

**Apifox 为准。** 本文件（`agent-workbench.yaml`）是 Apifox 的导入/导出交换文件，不是第二份需要手工维护的文档。

## 推荐流程

1. 在 Apifox 中修改、评审接口（请求、响应、错误码、示例）。
2. 从 Apifox 导出 **OpenAPI 3.0 YAML**，覆盖 `agent-workbench.yaml`。
3. 后端按契约实现或调整代码。
4. 前端直接使用 Apifox 的 Mock / 调试 / 自动化测试。

> 不要同时在 Apifox 和代码注释里各维护一份接口文档。两边不一致时，以 Apifox 为准，代码随后对齐。

## 首次导入 Apifox

Apifox → 项目设置 → 导入数据 → OpenAPI / Swagger → 选择 `javaweb-api.yaml`（全量接口）。

如果只想导入 Agent 会话能力，也可以选择 `agent-workbench.yaml`。

导入后建议：

- Apifox 环境的前置 URL 使用 `http://127.0.0.1:1107/api`；
- **spec 里的 paths 是相对 `/api` 的**：Agent 接口写 `/agent/sessions`，账号接口写 `/user/login`；
  如果前置 URL 已经包含 `/api`，就不要再写 `/api/agent/...`，否则会变成 `/api/api/agent/...`；
- 配置环境变量 `token`，请求头使用 `Authorization: Bearer {{token}}`；
- 为 `POST /agent/sessions/{id}/messages/stream` 单独建一个 SSE 请求，前端联调时使用同样的 `clientMessageId` 语义。

## 账号接口约定（当前 Apifox 导出里的需要注意）

- 登录、注册、验证码是**公开接口**，Apifox 里不要勾选 Bearer Token；
- 登录 / 注册的 `password` 是前端 **SM3 后的 64 位十六进制摘要**，不是明文 `123456`；
- 统一响应是 `{"code":"200","msg":"操作成功","data":...}`，不是 `{"code":0,"message":"ok"}`；
- `/user/list` 需要管理员权限；
- `GET /user/message` 不需要 requestBody。

## 契约约定

- 需要登录：`Authorization: Bearer <JWT>`。
- 未登录 / token 失效：HTTP 401。
- 越权：HTTP 403。
- 业务错误：HTTP 200，响应体 `code` 非 `"200"`（见 `ErrorResponse`）。
- 流式接口：`text/event-stream`；流建立前的错误返回 `application/json`。
- SSE 事件：`meta` → `delta` → `tool_start` / `tool_end` → `done` / `error`。

## 当前覆盖范围

`javaweb-api.yaml` 覆盖全部 Controller：

- 账号：登录、注册、用户信息、用户列表（管理员）、在线用户（管理员）、退出登录
- 验证码：`GET /user/captcha`
- Agent 会话：会话 CRUD、历史消息、SSE 流式回答、停止生成
- 任务与闹钟：任务 CRUD、提醒实例、完成度回应
- 亲密度：当前关系、成长流水
- 人格：预设列表、当前人格、保存人格
- 通知：列表、未读数、已读、SSE 实时推送
- 通知设置：免打扰与主动消息开关
- Web Push：公钥、订阅、取消订阅、测试推送
