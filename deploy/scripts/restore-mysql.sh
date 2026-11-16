#!/usr/bin/env bash
# MySQL 逻辑备份恢复脚本
#
# 用法：
#   DB_PASSWORD=xxx ./restore-mysql.sh /var/backups/avatar-agent/虚拟形象-20261114-030000.sql.gz
#
# 注意：恢复会覆盖同名库中的现有数据，执行前请确认目标库和备份文件。
set -euo pipefail

FILE="${1:?用法: $0 <backup.sql.gz>}"
DB_NAME="${DB_NAME:-虚拟形象}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"

: "${DB_PASSWORD:?请先设置 DB_PASSWORD}"
[ -f "$FILE" ] || { echo "[restore] 文件不存在：$FILE" >&2; exit 1; }
command -v "$MYSQL_BIN" >/dev/null 2>&1 || {
    echo "[restore] 找不到 $MYSQL_BIN，请安装 MySQL 客户端" >&2
    exit 1
}

gunzip -c "$FILE" | MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" \
    --host="$DB_HOST" \
    --port="$DB_PORT" \
    --user="$DB_USER" \
    --default-character-set=utf8mb4 \
    "$DB_NAME"

echo "[restore] 完成：$FILE -> $DB_NAME"
