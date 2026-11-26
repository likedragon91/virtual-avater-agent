-- AI 头像（与用户头像分开）与用户导入的 Live2D 模型

ALTER TABLE `user_profile`
    ADD COLUMN `ai_avatar` VARCHAR(255) DEFAULT NULL AFTER `preferences`;

CREATE TABLE IF NOT EXISTS `avatar_model` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `name`       VARCHAR(120) NOT NULL,
    `driver`     VARCHAR(32) NOT NULL DEFAULT 'live2d',
    `file_path`  VARCHAR(255) NOT NULL,
    `file_size`  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_avatar_model_user` (`user_id`, `created_at`),
    CONSTRAINT `fk_avatar_model_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs COMMENT = '用户导入的 Live2D 模型';
