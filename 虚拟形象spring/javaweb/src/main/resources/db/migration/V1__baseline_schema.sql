-- ============================================================
-- 虚拟形象：完整基线表结构（MySQL 8.0，utf8mb4）
--
-- 说明：
-- 1. 新库首次启动时由 Flyway 自动执行本文件。
-- 2. 已有库通过 baseline-on-migrate + baseline-version=0 接管，
--    CREATE TABLE IF NOT EXISTS 不会破坏已有表。
-- 3. 历史手工脚本见 sql/ 目录，仅作记录，不再作为部署依据。
-- ============================================================

-- 用户
CREATE TABLE IF NOT EXISTS `user` (
    `id`                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `username`               VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `email`                  VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `password`               VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
    `salt`                   VARCHAR(32) NOT NULL DEFAULT '',
    `nickname`               VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
    `avatar`                 VARCHAR(255) DEFAULT NULL,
    `phone`                  VARCHAR(20) DEFAULT NULL,
    `gender`                 TINYINT NOT NULL DEFAULT 0 COMMENT '0-未知 1-男 2-女',
    `gender_display_name`    VARCHAR(16) DEFAULT NULL,
    `id_card`                VARCHAR(18) DEFAULT NULL,
    `birthday`               DATE DEFAULT NULL,
    `user_type`              TINYINT NOT NULL DEFAULT 1 COMMENT '1-普通用户 2-管理员 3-VIP',
    `user_type_display_name` VARCHAR(16) DEFAULT NULL,
    `status`                 TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-正常 2-锁定',
    `status_display_name`    VARCHAR(16) DEFAULT NULL,
    `created_at`             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`),
    KEY `idx_user_status_id` (`status`, `id`),
    KEY `idx_user_type_id` (`user_type`, `id`),
    CONSTRAINT `chk_user_status` CHECK (`status` IN (0, 1, 2))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 用户偏好 / 通知设置
CREATE TABLE IF NOT EXISTS `user_profile` (
    `user_id`            BIGINT UNSIGNED NOT NULL,
    `preferences`        JSON NULL COMMENT '人格偏好：personaCode/traits',
    `dnd_start`          TIME DEFAULT NULL,
    `dnd_end`            TIME DEFAULT NULL,
    `proactive_enabled`  TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 关系状态
CREATE TABLE IF NOT EXISTS `relationship_state` (
    `user_id`         BIGINT UNSIGNED NOT NULL,
    `intimacy_total`  DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    `stage_code`      VARCHAR(8) NOT NULL DEFAULT 'S0',
    `stage_since`     DATETIME(3) DEFAULT NULL,
    `user_cap_stage`  VARCHAR(32) DEFAULT NULL,
    `updated_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_relationship_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 亲密度流水
CREATE TABLE IF NOT EXISTS `intimacy_log` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT UNSIGNED NOT NULL,
    `delta`          DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    `balance_after`  DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    `reason`         VARCHAR(32) NOT NULL,
    `ref_type`       VARCHAR(32) DEFAULT NULL,
    `ref_id`         BIGINT UNSIGNED DEFAULT NULL,
    `created_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_intimacy_user_id` (`user_id`, `id`),
    CONSTRAINT `fk_intimacy_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 每日亲密度汇总
CREATE TABLE IF NOT EXISTS `daily_intimacy` (
    `user_id`         BIGINT UNSIGNED NOT NULL,
    `stat_date`       DATE NOT NULL,
    `gained`          DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    `task_count`      INT NOT NULL DEFAULT 0,
    `avg_completion`  DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    `streak_days`     INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`, `stat_date`),
    CONSTRAINT `fk_daily_intimacy_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 阶段升级事件
CREATE TABLE IF NOT EXISTS `stage_event` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL,
    `from_stage`  VARCHAR(8) NOT NULL,
    `to_stage`    VARCHAR(8) NOT NULL,
    `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stage_event_user_to` (`user_id`, `to_stage`),
    CONSTRAINT `fk_stage_event_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 阶段策略
CREATE TABLE IF NOT EXISTS `stage_policy` (
    `code`        VARCHAR(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    `version`     INT NOT NULL,
    `config`      JSON NOT NULL,
    `active`      TINYINT(1) NOT NULL DEFAULT 1,
    `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`code`, `version`),
    KEY `idx_stage_policy_code_active` (`code`, `active`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 人格提示词模板
CREATE TABLE IF NOT EXISTS `prompt_template` (
    `code`        VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    `version`     INT NOT NULL,
    `content`     TEXT NOT NULL,
    `active`      TINYINT(1) NOT NULL DEFAULT 1,
    `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`code`, `version`),
    KEY `idx_prompt_code_active` (`code`, `active`, `version`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- Agent 会话
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`            BIGINT UNSIGNED NOT NULL,
    `title`              VARCHAR(120) DEFAULT NULL,
    `persona_code`       VARCHAR(32) NOT NULL DEFAULT 'default',
    `status`             TINYINT NOT NULL DEFAULT 1 COMMENT '1-正常 0-归档',
    `summary`            TEXT DEFAULT NULL,
    `summary_updated_at` DATETIME(3) DEFAULT NULL,
    `message_count`      INT NOT NULL DEFAULT 0,
    `last_active_at`     DATETIME(3) DEFAULT NULL,
    `created_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_chat_session_user_status_active` (`user_id`, `status`, `last_active_at`),
    CONSTRAINT `fk_chat_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- Agent 消息
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `session_id`         BIGINT UNSIGNED NOT NULL,
    `user_id`            BIGINT UNSIGNED NOT NULL,
    `sender_role`        VARCHAR(16) NOT NULL,
    `content`            TEXT NOT NULL,
    `content_type`       VARCHAR(16) NOT NULL DEFAULT 'text',
    `status`             TINYINT NOT NULL DEFAULT 1 COMMENT '0-生成中 1-完成 2-失败 3-已取消',
    `client_message_id`  VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    `emotion`            JSON NULL,
    `tokens`             INT DEFAULT NULL,
    `model`              VARCHAR(64) DEFAULT NULL,
    `created_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_client` (`session_id`, `client_message_id`),
    KEY `idx_chat_message_session_user_status_id` (`session_id`, `user_id`, `status`, `id`),
    KEY `idx_chat_message_user_created` (`user_id`, `created_at`),
    CONSTRAINT `fk_chat_message_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_chat_message_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 工具调用审计
CREATE TABLE IF NOT EXISTS `tool_call_log` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `message_id`      BIGINT UNSIGNED NOT NULL COMMENT 'assistant 消息 id，即 runId',
    `session_id`      BIGINT UNSIGNED NOT NULL,
    `user_id`         BIGINT UNSIGNED NOT NULL,
    `call_id`         VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    `tool_name`       VARCHAR(64) NOT NULL,
    `arguments`       JSON NULL,
    `result_summary`  VARCHAR(500) DEFAULT NULL,
    `status`          VARCHAR(16) NOT NULL DEFAULT 'OK',
    `duration_ms`     INT DEFAULT NULL,
    `created_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_tool_message` (`message_id`),
    KEY `idx_tool_user_time` (`user_id`, `created_at`),
    KEY `idx_tool_session` (`session_id`),
    CONSTRAINT `fk_tool_message` FOREIGN KEY (`message_id`) REFERENCES `chat_message` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_tool_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_tool_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 长期记忆
CREATE TABLE IF NOT EXISTS `memory_item` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`            BIGINT UNSIGNED NOT NULL,
    `memory_type`        VARCHAR(32) NOT NULL,
    `title`              VARCHAR(255) DEFAULT NULL,
    `content`            TEXT NOT NULL,
    `structured`         JSON NULL,
    `importance`         TINYINT NOT NULL DEFAULT 3,
    `confidence`         DECIMAL(4, 2) NOT NULL DEFAULT 1.00,
    `occurred_at`        DATETIME(3) DEFAULT NULL,
    `source_message_id`  BIGINT UNSIGNED DEFAULT NULL,
    `embedded_at`        DATETIME(3) DEFAULT NULL,
    `access_count`       INT NOT NULL DEFAULT 0,
    `last_accessed_at`   DATETIME(3) DEFAULT NULL,
    `decay_score`        DECIMAL(6, 4) NOT NULL DEFAULT 1.0000,
    `status`             VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `created_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_memory_user_status_type` (`user_id`, `status`, `memory_type`),
    KEY `idx_memory_user_status_order` (`user_id`, `status`, `importance`, `occurred_at`, `created_at`),
    FULLTEXT KEY `ft_memory_content` (`content`) WITH PARSER ngram,
    CONSTRAINT `chk_memory_type` CHECK (`memory_type` IN ('PROFILE', 'PREFERENCE', 'EPISODE', 'RELATION', 'PROMISE')),
    CONSTRAINT `chk_memory_importance` CHECK (`importance` BETWEEN 1 AND 5),
    CONSTRAINT `fk_memory_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_memory_source_message` FOREIGN KEY (`source_message_id`) REFERENCES `chat_message` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 事务闹钟任务
CREATE TABLE IF NOT EXISTS `task` (
    `id`                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`                   BIGINT UNSIGNED NOT NULL,
    `title`                     VARCHAR(200) NOT NULL,
    `note`                      VARCHAR(2000) DEFAULT NULL,
    `due_at`                    DATETIME(3) NOT NULL,
    `tz`                        VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'Asia/Shanghai',
    `rrule`                     VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    `priority`                  TINYINT NOT NULL DEFAULT 2,
    `status`                    VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `source`                    VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    `response_window_minutes`   INT NOT NULL DEFAULT 120,
    `allow_note`                TINYINT(1) NOT NULL DEFAULT 1,
    `intimacy_weight`           DECIMAL(4, 2) NOT NULL DEFAULT 1.00,
    `remind_offsets`            JSON NOT NULL DEFAULT (JSON_ARRAY(0)),
    `created_from_message_id`   BIGINT UNSIGNED DEFAULT NULL,
    `created_at`                DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`                DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_task_user_status_due` (`user_id`, `status`, `due_at`),
    KEY `idx_task_user_due` (`user_id`, `due_at`),
    CONSTRAINT `chk_task_priority` CHECK (`priority` BETWEEN 1 AND 3),
    CONSTRAINT `chk_task_status` CHECK (`status` IN ('ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT `fk_task_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_task_source_message` FOREIGN KEY (`created_from_message_id`) REFERENCES `chat_message` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 任务提醒实例
CREATE TABLE IF NOT EXISTS `task_occurrence` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_id`     BIGINT UNSIGNED NOT NULL,
    `user_id`     BIGINT UNSIGNED NOT NULL,
    `occur_at`    DATETIME(3) NOT NULL,
    `expires_at`  DATETIME(3) NOT NULL,
    `status`      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    `fired_at`    DATETIME(3) DEFAULT NULL,
    `responded_at` DATETIME(3) DEFAULT NULL,
    `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_occurrence_task_time` (`task_id`, `occur_at`),
    KEY `idx_task_occurrence_status_time` (`status`, `occur_at`, `expires_at`),
    KEY `idx_task_occurrence_status_expires` (`status`, `expires_at`),
    KEY `idx_task_occurrence_user_time_status` (`user_id`, `occur_at`, `status`),
    KEY `idx_task_occurrence_task_status_time` (`task_id`, `status`, `occur_at`),
    CONSTRAINT `chk_occurrence_status` CHECK (`status` IN ('PENDING', 'RINGING', 'RESPONDED', 'MISSED', 'CANCELED')),
    CONSTRAINT `fk_occurrence_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_occurrence_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 任务完成度回应
CREATE TABLE IF NOT EXISTS `task_response` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT UNSIGNED NOT NULL,
    `task_id`           BIGINT UNSIGNED NOT NULL,
    `occurrence_id`     BIGINT UNSIGNED NOT NULL,
    `completion_ratio`  TINYINT UNSIGNED NOT NULL,
    `note`              VARCHAR(100) DEFAULT NULL,
    `mood`              VARCHAR(32) DEFAULT NULL,
    `responded_at`      DATETIME(3) NOT NULL,
    `latency_sec`       INT DEFAULT NULL,
    `timeliness`        DECIMAL(4, 2) NOT NULL DEFAULT 1.00,
    `intimacy_delta`    DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    `revised_at`        DATETIME(3) DEFAULT NULL,
    `idempotency_key`   VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    `created_at`        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_response_occurrence` (`occurrence_id`),
    UNIQUE KEY `uk_task_response_idempotency` (`idempotency_key`),
    KEY `idx_task_response_task` (`task_id`),
    KEY `idx_task_response_user_responded` (`user_id`, `responded_at`),
    CONSTRAINT `chk_task_response_completion` CHECK (`completion_ratio` BETWEEN 0 AND 100),
    CONSTRAINT `fk_task_response_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_task_response_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_task_response_occurrence` FOREIGN KEY (`occurrence_id`) REFERENCES `task_occurrence` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- 站内通知
CREATE TABLE IF NOT EXISTS `notification` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL,
    `type`        VARCHAR(32) NOT NULL,
    `title`       VARCHAR(255) NOT NULL,
    `body`        VARCHAR(500) DEFAULT NULL,
    `payload`     JSON NULL,
    `read_at`     DATETIME(3) DEFAULT NULL,
    `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_notification_user_id` (`user_id`, `id`),
    KEY `idx_notification_user_read` (`user_id`, `read_at`),
    CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- Web Push 订阅
CREATE TABLE IF NOT EXISTS `push_subscription` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT UNSIGNED NOT NULL,
    `endpoint`      VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    `p256dh`        VARCHAR(512) NOT NULL,
    `auth`          VARCHAR(512) NOT NULL,
    `user_agent`    VARCHAR(255) DEFAULT NULL,
    `created_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `last_used_at`  DATETIME(3) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_push_endpoint` (`endpoint`),
    KEY `idx_push_user_id` (`user_id`, `id`),
    CONSTRAINT `fk_push_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;

-- LLM 调用日志
CREATE TABLE IF NOT EXISTS `llm_call_log` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`            BIGINT UNSIGNED NOT NULL,
    `scene`              VARCHAR(64) NOT NULL,
    `model`              VARCHAR(64) NOT NULL,
    `prompt_tokens`      INT DEFAULT NULL,
    `completion_tokens`  INT DEFAULT NULL,
    `latency_ms`         INT DEFAULT NULL,
    `cost`               DECIMAL(12, 6) DEFAULT NULL,
    `status`             VARCHAR(16) NOT NULL,
    `trace_id`           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    `created_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_llm_user_created` (`user_id`, `created_at`),
    KEY `idx_llm_trace` (`trace_id`),
    CONSTRAINT `fk_llm_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs;
