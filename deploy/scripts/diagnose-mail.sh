#!/usr/bin/env bash
# 找回密码邮件诊断脚本（只读，不发送邮件、不修改配置、不打印密码）
#
# 用法（在服务器上，需要 root）：
#   sudo bash scripts/diagnose-mail.sh <注册邮箱或用户名>
#
# 建议先触发一次“发送验证码”，再运行本脚本。
set -uo pipefail

ENV_FILE="${ENV_FILE:-/etc/avatar-agent/env}"
SERVICE="${SERVICE:-avatar-agent}"
TARGET="${1:-}"

section() {
    printf '\n===== %s =====\n' "$1"
}

have() {
    command -v "$1" >/dev/null 2>&1
}

# 安全解析 systemd EnvironmentFile：
# 不能直接 source，因为 DB_URL 里含 & 等 shell 特殊字符。
load_env_file() {
    local file="$1"
    local line key value
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%$'\r'}"
        case "$line" in
            ''|\#*) continue ;;
        esac
        case "$line" in
            *=*) ;;
            *) continue ;;
        esac
        key="${line%%=*}"
        value="${line#*=}"
        key="${key#"${key%%[![:space:]]*}"}"
        key="${key%"${key##*[![:space:]]}"}"
        key="${key#$'\xef\xbb\xbf'}"
        [ -z "$key" ] && continue
        case "$value" in
            \"*\") value="${value#\"}"; value="${value%\"}" ;;
            \'*\') value="${value#\'}"; value="${value%\'}" ;;
        esac
        export "$key=$value"
    done < "$file"
}

section "1. systemd 与配置文件"
if have systemctl; then
    systemctl cat "$SERVICE" 2>/dev/null | sed -n '/\[Service\]/,/^\[/p' || echo "读取 systemd unit 失败"
    systemctl show "$SERVICE" \
        -p EnvironmentFiles \
        -p ExecMainStartTimestamp \
        -p MainPID 2>/dev/null || true
else
    echo "未找到 systemctl"
fi

if [ -f "$ENV_FILE" ]; then
    stat -c '%A %U:%G %s bytes %y %n' "$ENV_FILE"
    if grep -q $'\r' "$ENV_FILE"; then
        echo "警告：$ENV_FILE 含 CRLF；systemd 可能把 \\r 读进变量值，建议 dos2unix"
    fi
    if [ "$(head -c 3 "$ENV_FILE" | od -An -tx1 | tr -d ' \n')" = "efbbbf" ]; then
        echo "警告：$ENV_FILE 含 UTF-8 BOM"
    fi
else
    echo "找不到 $ENV_FILE"
fi

section "2. 邮件相关环境变量（不显示密码）"
if [ -f "$ENV_FILE" ]; then
    grep -E '^(MAIL_HOST|MAIL_PORT|MAIL_USERNAME|MAIL_FROM|MAIL_SSL_ENABLED|MAIL_STARTTLS_ENABLED|MAIL_SMTP_AUTH)=' "$ENV_FILE" \
        || echo "缺少 MAIL_* 变量"
    if grep -q '^MAIL_PASSWORD=' "$ENV_FILE"; then
        echo "MAIL_PASSWORD=<set>"
    else
        echo "MAIL_PASSWORD=<missing>"
    fi
fi

section "3. 运行中进程实际收到的环境变量"
PID="$(systemctl show "$SERVICE" -p MainPID --value 2>/dev/null || true)"
if [ -n "${PID:-}" ] && [ "$PID" != "0" ] && [ -r "/proc/$PID/environ" ]; then
    tr '\0' '\n' < "/proc/$PID/environ" \
        | grep -E '^(MAIL_HOST|MAIL_PORT|MAIL_USERNAME|MAIL_FROM|MAIL_SSL_ENABLED|MAIL_STARTTLS_ENABLED|MAIL_SMTP_AUTH)=' \
        || echo "进程里没有 MAIL_*：服务启动时未加载 env，或修改后未重启"
    tr '\0' '\n' < "/proc/$PID/environ" | grep -q '^MAIL_PASSWORD=' \
        && echo "MAIL_PASSWORD=<set>" \
        || echo "MAIL_PASSWORD=<missing>"
else
    echo "无法读取进程环境（需要 root，或服务未运行）"
fi

section "4. 最近找回密码相关日志"
if have journalctl; then
    journalctl -u "$SERVICE" --no-pager -n 500 2>/dev/null \
        | grep -E "password reset|send password reset mail|JavaMailSender" \
        | tail -30 \
        || echo "未找到相关日志：先触发一次发送验证码，再重跑本脚本"
else
    echo "未找到 journalctl"
fi

section "5. SMTP 出站连通性"
if [ -f "$ENV_FILE" ]; then
    load_env_file "$ENV_FILE"
fi

HOST="${MAIL_HOST:-}"
PORT="${MAIL_PORT:-465}"
if [ -n "$HOST" ]; then
    echo "目标：$HOST:$PORT"
    if timeout 6 bash -c "exec 3<>/dev/tcp/$HOST/$PORT" 2>/dev/null; then
        echo "TCP 连接成功"
    else
        echo "TCP 连接失败：检查云厂商出站规则、防火墙、端口是否正确"
    fi

    if have openssl; then
        if [ "$PORT" = "465" ]; then
            timeout 8 openssl s_client -connect "$HOST:$PORT" -servername "$HOST" -brief </dev/null 2>&1 | head -8 || true
        elif [ "$PORT" = "587" ]; then
            timeout 8 openssl s_client -starttls smtp -connect "$HOST:$PORT" -servername "$HOST" -brief </dev/null 2>&1 | head -8 || true
        fi
    fi
else
    echo "MAIL_HOST 为空，跳过"
fi

section "6. SMTP 登录测试（不发送邮件）"
if [ -n "${MAIL_HOST:-}" ] && [ -n "${MAIL_USERNAME:-}" ] && [ -n "${MAIL_PASSWORD:-}" ]; then
    if have python3; then
        # 通过环境变量传参，避免密码出现在 ps 命令行
        export DIAG_SMTP_HOST="$MAIL_HOST"
        export DIAG_SMTP_PORT="${PORT:-465}"
        export DIAG_SMTP_USER="$MAIL_USERNAME"
        export DIAG_SMTP_PASSWORD="$MAIL_PASSWORD"
        export DIAG_SMTP_SSL="${MAIL_SSL_ENABLED:-true}"
        export DIAG_SMTP_STARTTLS="${MAIL_STARTTLS_ENABLED:-false}"

        python3 - <<'PY'
import os
import smtplib
import ssl

host = os.environ["DIAG_SMTP_HOST"]
port = int(os.environ.get("DIAG_SMTP_PORT", "465"))
user = os.environ["DIAG_SMTP_USER"]
password = os.environ["DIAG_SMTP_PASSWORD"]
use_ssl = os.environ.get("DIAG_SMTP_SSL", "true").lower() == "true"
use_starttls = os.environ.get("DIAG_SMTP_STARTTLS", "false").lower() == "true"

ctx = ssl.create_default_context()
try:
    if use_ssl:
        server = smtplib.SMTP_SSL(host, port, context=ctx, timeout=10)
    else:
        server = smtplib.SMTP(host, port, timeout=10)
    with server:
        if use_starttls and not use_ssl:
            server.starttls(context=ctx)
        server.login(user, password)
    print("SMTP LOGIN OK")
except Exception as exc:
    print(f"SMTP LOGIN FAILED: {type(exc).__name__}: {exc}")
PY

        unset DIAG_SMTP_HOST DIAG_SMTP_PORT DIAG_SMTP_USER DIAG_SMTP_PASSWORD DIAG_SMTP_SSL DIAG_SMTP_STARTTLS
    else
        echo "未安装 python3，跳过登录测试"
    fi
else
    echo "MAIL_HOST / MAIL_USERNAME / MAIL_PASSWORD 不完整，跳过"
fi

section "7. Redis 中的重置验证码/冷却键"
if have redis-cli; then
    REDIS_ARGS=(
        -h "${REDIS_HOST:-127.0.0.1}"
        -p "${REDIS_PORT:-6379}"
        -n "${REDIS_DATABASE:-0}"
    )
    if [ -n "${REDIS_PASSWORD:-}" ]; then
        export REDISCLI_AUTH="$REDIS_PASSWORD"
    fi
    redis-cli "${REDIS_ARGS[@]}" --scan --pattern 'auth:reset:*' 2>/dev/null | head -20 \
        || echo "无法读取 Redis"
    if [ -n "$TARGET" ]; then
        EMAIL="$(printf '%s' "$TARGET" | tr '[:upper:]' '[:lower:]')"
        printf 'code TTL: '
        redis-cli "${REDIS_ARGS[@]}" TTL "auth:reset:code:$EMAIL" 2>/dev/null || true
        printf 'cooldown TTL: '
        redis-cli "${REDIS_ARGS[@]}" TTL "auth:reset:cooldown:$EMAIL" 2>/dev/null || true
    fi
    unset REDISCLI_AUTH
else
    echo "未找到 redis-cli，跳过"
fi

section "8. 数据库中的邮箱记录"
if [ -n "$TARGET" ] && have mysql; then
    DB_NAME="${DB_NAME:-虚拟形象}"
    DB_HOST="${DB_HOST:-127.0.0.1}"
    DB_PORT="${DB_PORT:-3306}"
    DB_USER="${DB_USERNAME:-${DB_USER:-root}}"
    TARGET_SQL="${TARGET//\'/\'\'}"
    if [ -n "${DB_PASSWORD:-}" ]; then
        MYSQL_PWD="$DB_PASSWORD" mysql \
            -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" \
            --default-character-set=utf8mb4 -N -B "$DB_NAME" \
            -e "SELECT id, username, email FROM \`user\` WHERE username='${TARGET_SQL}' OR email='${TARGET_SQL}' LIMIT 5;" 2>&1 || true
    else
        echo "DB_PASSWORD 为空，跳过"
    fi
else
    echo "用法：sudo bash $0 <注册邮箱或用户名> 可额外查询 DB 与 Redis 键"
fi

echo
echo "提示：若日志出现 send password reset mail failed，把紧随其后的异常信息发出来即可定位。"
echo "常见原因：QQ/163 使用登录密码而非 SMTP 授权码；MAIL_FROM 与 MAIL_USERNAME 不一致；465/587 与 SSL/STARTTLS 组合错误；云厂商封锁出站端口。"
