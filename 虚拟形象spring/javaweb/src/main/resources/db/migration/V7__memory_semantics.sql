-- 记忆语义扩展：来源、有效期、替代关系、向量
-- 注意：老库的 memory_item 可能是手工脚本建的，不一定带 V1 里的 chk_memory_type，
-- 因此这里先查 information_schema，再条件删除，不能直接 DROP CHECK。

ALTER TABLE `memory_item`
    ADD COLUMN `source_type`     VARCHAR(16) NOT NULL DEFAULT 'EXTRACTED' AFTER `confidence`,
    ADD COLUMN `expires_at`      DATETIME(3) DEFAULT NULL AFTER `occurred_at`,
    ADD COLUMN `superseded_by`   BIGINT UNSIGNED DEFAULT NULL AFTER `status`,
    ADD COLUMN `embedding`       BLOB DEFAULT NULL,
    ADD COLUMN `embedding_model` VARCHAR(64) DEFAULT NULL,
    ADD COLUMN `embedding_dim`   SMALLINT DEFAULT NULL,
    ADD KEY `idx_memory_user_status_expire` (`user_id`, `status`, `expires_at`),
    ADD KEY `idx_memory_superseded` (`superseded_by`);

ALTER TABLE `chat_session`
    ADD COLUMN `summary_message_id` BIGINT UNSIGNED DEFAULT NULL AFTER `summary_updated_at`;

SET @has_memory_type_check = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'memory_item'
      AND CONSTRAINT_NAME = 'chk_memory_type'
      AND CONSTRAINT_TYPE = 'CHECK'
);

SET @drop_check_sql = IF(@has_memory_type_check > 0,
    'ALTER TABLE `memory_item` DROP CHECK `chk_memory_type`',
    'DO 0');

PREPARE drop_check_stmt FROM @drop_check_sql;
EXECUTE drop_check_stmt;
DEALLOCATE PREPARE drop_check_stmt;

ALTER TABLE `memory_item`
    ADD CONSTRAINT `chk_memory_type`
        CHECK (`memory_type` IN ('PROFILE', 'PREFERENCE', 'EPISODE', 'RELATION', 'PROMISE', 'FACT'));
