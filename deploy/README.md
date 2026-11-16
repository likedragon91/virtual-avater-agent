# 部署说明

## 目录约定

```text
/var/www/avatar-agent/dist     前端构建产物（Nginx root）
/opt/avatar-agent/app.jar      后端 Spring Boot jar
/etc/avatar-agent/env          后端环境变量（600 权限）
/etc/nginx/conf.d/avatar-agent.conf
/var/backups/avatar-agent      数据库备份目录
```

## 0. 初始化数据库（Flyway）

首次部署只需要创建空库，**不需要手工执行建表 SQL**：

```bash
mysql -u root -p < deploy/sql/00_create_database.sql
```

应用启动时 Flyway 会自动：

- 新库：执行 `V1__baseline_schema.sql` 建表、`V2` 补索引、`V3` 写人格和阶段种子；
- 已有库：通过 `baseline-on-migrate=true` 接管，补缺失索引和种子；
- 迁移记录保存在 `flyway_schema_history` 表。

如果要创建独立数据库账号，可以使用 `deploy/sql/00_create_database.sql` 中注释掉的语句，然后把账号密码填到 `/etc/avatar-agent/env` 的 `DB_USERNAME` / `DB_PASSWORD`。

首次注册的用户默认是普通用户。需要管理员时执行：

```sql
UPDATE `user` SET `user_type` = 2, `user_type_display_name` = '管理员'
WHERE `username` = '你的账号';
```

不要使用默认管理员密码，也不要提交管理员密码到仓库。

## 1. 构建前端

```bash
cd 虚拟形象
npm ci
npm run build
```

默认 API 地址是相对路径 `/api`，同域部署不需要额外配置。
如前后端分域，构建前创建 `.env.production`：

```properties
VITE_API_BASE_URL=https://api.example.com/api
VITE_ASSET_BASE_URL=https://cdn.example.com
```

## 2. 构建后端

```bash
cd 虚拟形象spring/javaweb
./mvnw clean package -DskipTests
```

生成的 jar 在 `target/javaweb-0.0.1-SNAPSHOT.jar`。

## 3. 配置环境变量

参考 `javaweb/src/main/resources/application.properties.example`，创建 `/etc/avatar-agent/env`：

```bash
DB_PASSWORD=生产数据库密码
JWT_SECRET=至少32位随机值
DEEPSEEK_API_KEY=sk-...
BOCHA_API_KEY=博查搜索密钥
MAIL_HOST=smtp.qq.com
MAIL_PORT=465
MAIL_USERNAME=你的邮箱
MAIL_PASSWORD=邮箱SMTP授权码
MAIL_FROM=你的邮箱
MAIL_SSL_ENABLED=true
MAIL_STARTTLS_ENABLED=false
WEB_PUSH_PUBLIC_KEY=...
WEB_PUSH_PRIVATE_KEY=...
WEB_PUSH_SUBJECT=mailto:admin@example.com
```

```bash
sudo chmod 600 /etc/avatar-agent/env
```

### 邮件服务（找回密码）

QQ 邮箱 / 163 邮箱需要先在网页版设置中开启 SMTP 服务，并生成**授权码**（不是登录密码）：

```text
MAIL_HOST=smtp.qq.com
MAIL_PORT=465
MAIL_USERNAME=your@qq.com
MAIL_PASSWORD=SMTP授权码
MAIL_FROM=your@qq.com
MAIL_SSL_ENABLED=true
MAIL_STARTTLS_ENABLED=false
```

Gmail 等使用 587 端口的服务：

```text
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_SSL_ENABLED=false
MAIL_STARTTLS_ENABLED=true
```

不配置邮件时应用仍能启动，但登录页“找回密码”只会返回通用成功提示，不会真正发出邮件。

## 4. systemd 启动后端

`/etc/systemd/system/avatar-agent.service`：

```ini
[Unit]
Description=Avatar Agent Backend
After=network.target mysql.service redis.service

[Service]
User=avatar
WorkingDirectory=/opt/avatar-agent
EnvironmentFile=/etc/avatar-agent/env
ExecStart=/usr/bin/java -Xms512m -Xmx1g -jar /opt/avatar-agent/app.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now avatar-agent
```

## 5. 数据库备份与恢复

复制脚本到服务器并赋予执行权限：

```bash
sudo mkdir -p /opt/avatar-agent/scripts
sudo cp deploy/scripts/*.sh /opt/avatar-agent/scripts/
sudo chmod 700 /opt/avatar-agent/scripts/*.sh
```

手动备份：

```bash
set -a
. /etc/avatar-agent/env
set +a
BACKUP_DIR=/var/backups/avatar-agent /opt/avatar-agent/scripts/backup-mysql.sh
```

每天凌晨 3 点自动备份并保留 7 天，编辑 `crontab -e`：

```cron
0 3 * * * set -a; . /etc/avatar-agent/env; set +a; BACKUP_DIR=/var/backups/avatar-agent /opt/avatar-agent/scripts/backup-mysql.sh >> /var/log/avatar-agent-backup.log 2>&1
```

恢复：

```bash
set -a
. /etc/avatar-agent/env
set +a
/opt/avatar-agent/scripts/restore-mysql.sh /var/backups/avatar-agent/虚拟形象-20261114-030000.sql.gz
```

恢复会覆盖目标库中的现有数据，执行前先确认备份文件和目标库。

建议同时开启云厂商的磁盘快照，形成“每日逻辑备份 + 定期快照”的双重保障。

## 6. 配置 Nginx + HTTPS

```bash
sudo cp deploy/nginx/avatar-agent.conf /etc/nginx/conf.d/avatar-agent.conf
# 修改 server_name、root、证书路径
sudo nginx -t
sudo systemctl reload nginx
```

### 没有域名时先用 IP 测试

如果暂时只有服务器 IP，可先使用 HTTP 配置：

```bash
sudo cp deploy/nginx/avatar-agent-http.conf /etc/nginx/conf.d/avatar-agent.conf
sudo nginx -t && sudo systemctl reload nginx
```

然后浏览器访问 `http://<SERVER_IP>`（替换为你的服务器 IP）。

注意：**HTTP 下 Service Worker、Web Push 和 PWA 安装都不会生效**，只能测试普通页面、登录和聊天。正式上线需要域名 + HTTPS。

首次申请证书：

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d avatar.example.com
```

## 7. 验证

```bash
# 首页
curl -I https://avatar.example.com/

# 登录接口应返回 JSON（不是 HTML 首页）
curl -I https://avatar.example.com/api/user/captcha

# SSE 流不应被 Nginx 缓冲
curl -N https://avatar.example.com/api/notifications/stream \
  -H "Authorization: Bearer <token>"
```

## 关键点

- Service Worker 和 Web Push 只在 HTTPS 下工作。
- SSE 必须在 Nginx 中设置 `proxy_buffering off`，并加长读取超时。
- `sw.js` 必须禁止缓存，否则用户会长期使用旧版本。
- `/assets/` 下的文件名带 hash，可以放心长缓存。
- 安全头统一放在 server 层；如果 location 里再写 `add_header`，会覆盖 server 层所有 `add_header`。
