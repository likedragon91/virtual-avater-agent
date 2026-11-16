#!/usr/bin/env bash
# MySQL 逻辑备份脚本
#
# 依赖环境变量：
#   DB_PASSWORD   必填
# 可选环境变量：
#   DB_NAME       默认 虚拟形象
#   DB_HOST       默认 127.0.0.1
#   DB_PORT       默认 3306
#   DB_USER       默认 root
#   BACKUP_DIR    默认 /var/backups/avatar-agent
#   RETENTION_DAYS 默认 7
#   MYSQLDUMP_BIN 默认 mysqldump
#
# 示例：
#   DB_PASSWORD=xxx BACKUP_DIR=/var/backups/avatar-agent \
#     /opt/avatar-agent/scripts/backup-mysql.sh
set -euo pipefail

DB_NAME="${DB_NAME:-虚拟形象}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/avatar-agent}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"

: "${DB_PASSWORD:?请先设置 DB_PASSWORD}"
command -v "$MYSQLDUMP_BIN" >/dev/null 2>&1 || {
    echo "[backup] 找不到 $MYSQLDUMP_BIN，请安装 MySQL 客户端" >&2
    exit 1
}

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
FILE="$BACKUP_DIR/${DB_NAME}-${STAMP}.sql.gz"

MYSQL_PWD="$DB_PASSWORD" "$MYSQLDUMP_BIN" \
    --host="$DB_HOST" \
    --port="$DB_PORT" \
    --user="$DB_USER" \
    --single-transaction \
    --quick \
    --routines \
    --triggers \
    --no-tablespaces \
    --set-gtid-purged=OFF \
    --default-character-set=utf8mb4 \
    "$DB_NAME" | gzip -9 > "$FILE"

if [ ! -s "$FILE" ]; then
    echo "[backup] 备份文件为空：$FILE" >&2
    rm -f "$FILE"
    exit 1
fi

find "$BACKUP_DIR" -type f -name "${DB_NAME}-*.sql.gz" -mtime +"$RETENTION_DAYS" -delete

echo "[backup] 完成：$FILE"
