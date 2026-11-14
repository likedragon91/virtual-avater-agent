-- Agent 工作台会话 · 最小迁移
-- 目标库：虚拟形象
-- 状态：已于 2026-11-12 执行完毕；保留作为迁移记录，勿重复执行
-- 前置：chat_session / chat_message 当前为空表，可直接 ALTER
-- 设计：不建 chat_run（assistant 消息 id 即 runId），不建 user_memory_index（索引由 memory_item 查询承担）
-- 执行前请确认已备份；本脚本只做增量，不删除任何现有字段

USE `虚拟形象`;

-- 1. 会话归档状态（已于 2026-11-12 执行，保留记录；重跑时请跳过）
-- ALTER TABLE `chat_session`
--     ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1-正常 0-归档' AFTER `persona_code`;

-- 2. 消息：流式状态 + 幂等键 + 更新时间
ALTER TABLE `chat_message`
    ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-生成中 1-完成 2-失败 3-已取消' AFTER `content_type`,
    ADD COLUMN `client_message_id` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '客户端消息幂等键' AFTER `status`,
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER `created_at`,
    ADD UNIQUE KEY `uk_message_client` (`session_id`, `client_message_id`);

-- 3. 工具调用审计（前端可展示“正在查询任务/记忆”，后端可追溯）
CREATE TABLE `tool_call_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `message_id` BIGINT UNSIGNED NOT NULL COMMENT 'assistant 消息 id，即 runId',
    `session_id` BIGINT UNSIGNED NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `call_id` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '模型返回的 tool_call id',
    `tool_name` VARCHAR(64) NOT NULL,
    `arguments` JSON NULL COMMENT '脱敏后的参数摘要，不存敏感原文',
    `result_summary` VARCHAR(500) DEFAULT NULL COMMENT '结果摘要，不存完整记忆正文',
    `status` VARCHAR(16) NOT NULL DEFAULT 'OK' COMMENT 'OK/ERROR/TIMEOUT/DENIED',
    `duration_ms` INT DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_tool_message` (`message_id`),
    KEY `idx_tool_user_time` (`user_id`, `created_at`),
    CONSTRAINT `fk_tool_message` FOREIGN KEY (`message_id`) REFERENCES `chat_message` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_tool_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_tool_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_zh_0900_as_cs;

-- 校验（执行后）
-- SHOW CREATE TABLE chat_session;
-- SHOW CREATE TABLE chat_message;
-- SHOW CREATE TABLE tool_call_log;
