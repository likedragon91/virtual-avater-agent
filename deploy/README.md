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
# HTTPS 上线前保持 false；上线后再改为 true
WEBPUSH_ENABLED=false
```

```bash
sudo chmod 600 /etc/avatar-agent/env
```

> Web Push 的 VAPID 密钥不要更换，否则所有已有推送订阅都会失效。

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

配置已包含：

- `/sw.js` 禁止缓存；
- `/manifest.webmanifest` 使用 `application/manifest+json` 并禁止长期缓存；
- `/assets/` 长缓存；
- `/api/` 关闭代理缓冲并加长 SSE 超时。

### 没有域名时先用 IP 测试

如果暂时只有服务器 IP，可先使用 HTTP 配置：

```bash
sudo cp deploy/nginx/avatar-agent-http.conf /etc/nginx/conf.d/avatar-agent.conf
sudo nginx -t && sudo systemctl reload nginx
```

然后浏览器访问 `http://<SERVER_IP>`（替换为你的服务器 IP）。

注意：Service Worker、Web Push 与 PWA 安装都要求**安全上下文**（HTTPS 或 localhost）。通过服务器 IP + HTTP 访问不算安全上下文，只能测试普通页面、登录和聊天。正式上线需要域名 + HTTPS（国内服务器通常还需完成备案）；临时验收可把站点通过 SSH 端口转发映射到 `http://localhost:<端口>` 再测。

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

## 8. 日常更新（发布新版本）

推荐「本地构建 + 服务器原子切换」：服务器只运行产物，不安装 Node / Maven。

### 8.1 发布前备份

```bash
# 保留上一版，便于回滚
sudo cp /opt/avatar-agent/app.jar /opt/avatar-agent/app.jar.prev
sudo cp -a /var/www/avatar-agent/dist /var/www/avatar-agent/dist.prev

# 数据库逻辑备份
set -a; . /etc/avatar-agent/env; set +a
BACKUP_DIR=/var/backups/avatar-agent /opt/avatar-agent/scripts/backup-mysql.sh
```

### 8.2 本地构建

```bash
cd 虚拟形象spring/javaweb
./mvnw clean package -DskipTests

cd ../../../虚拟形象
npm ci
npm test
npm run build
```

### 8.3 上传到 staging 目录

```bash
rsync -avz --delete \
  虚拟形象/dist/ deploy@<服务器>:/var/www/avatar-agent/dist.new/

scp 虚拟形象spring/javaweb/target/javaweb-0.0.1-SNAPSHOT.jar \
  deploy@<服务器>:/opt/avatar-agent/app.jar.new
```

上传目录需对部署用户可写；如果不可写，先创建并 `chown` 给部署用户，或用受限制的 sudo 完成切换。

### 8.4 原子切换并重启

```bash
ssh deploy@<服务器>
sudo bash
set -e

mv /opt/avatar-agent/app.jar /opt/avatar-agent/app.jar.prev
mv /opt/avatar-agent/app.jar.new /opt/avatar-agent/app.jar

mv /var/www/avatar-agent/dist /var/www/avatar-agent/dist.prev
mv /var/www/avatar-agent/dist.new /var/www/avatar-agent/dist

systemctl restart avatar-agent
```

本次更新如果修改了 Nginx 配置（例如新增 manifest MIME），再执行：

```bash
nginx -t && systemctl reload nginx
```

### 8.5 验证

```bash
curl -I https://<域名>/
curl -fsS https://<域名>/api/user/captcha
curl -I https://<域名>/manifest.webmanifest
curl -I https://<域名>/sw.js
journalctl -u avatar-agent -n 100 --no-pager
```

浏览器端重点验证：登录、刷新页面后无感续期、退出登录、SSE 聊天、任务提醒。

### 8.6 回滚

```bash
sudo bash
set -e
mv /opt/avatar-agent/app.jar.prev /opt/avatar-agent/app.jar
rm -rf /var/www/avatar-agent/dist
mv /var/www/avatar-agent/dist.prev /var/www/avatar-agent/dist
systemctl restart avatar-agent
```

Flyway 迁移是前向的，不能单独回滚；只有数据被破坏时才使用数据库备份恢复。

### 8.7 本次升级注意事项

- **旧会话需要重登一次**：旧 Access Token 没有 Refresh Token。浏览器端默认 30 分钟、Electron 默认 1 小时，到期后无法续期。
- **PWA 验收需要安全上下文**：必须 HTTPS 或 localhost；直接访问服务器 IP 不行，国内机器需先完成域名备案。
- **Electron 必须重新打包**：`npm run electron:build`；API 地址在 `.env.desktop` 中烘入。未上 HTTPS 时用 `http://<SERVER_IP>/api`，上域名后改用 `https://<域名>/api` 再重新打包。
- **CORS**：同域部署不依赖 CORS；收紧为具体域名时必须同时允许 Electron 的 `null` Origin，否则桌面端全断。
- **TTL 覆盖变量名**：`JWT_ACCESS_EXPIRATION_WEB`、`JWT_ACCESS_EXPIRATION_DESKTOP`、`JWT_REFRESH_EXPIRATION_WEB`、`JWT_REFRESH_EXPIRATION_DESKTOP`；默认值已够用，本次不需要添加。
- **VAPID 不要更换**：已有推送订阅会全部失效；HTTPS 前保持 `WEBPUSH_ENABLED=false`。
- **业务数据不受影响**：更新只替换 jar 与前端 dist，数据库中的账号、关系、任务数据保持不变。

## 关键点

- Service Worker 和 Web Push 只在安全上下文（HTTPS 或 localhost）下工作。
- SSE 必须在 Nginx 中设置 `proxy_buffering off`，并加长读取超时。
- `sw.js` 必须禁止缓存，否则用户会长期使用旧版本。
- `/assets/` 下的文件名带 hash，可以放心长缓存。
- 安全头统一放在 server 层；如果 location 里再写 `add_header`，会覆盖 server 层所有 `add_header`。
