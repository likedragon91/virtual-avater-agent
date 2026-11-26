-- 桌面端安装包版本。管理员上传并发布后，更新页和全站通知读取最新一条。
CREATE TABLE IF NOT EXISTS `app_version` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `version`    VARCHAR(32) NOT NULL,
    `notes`      TEXT DEFAULT NULL,
    `file_path`  VARCHAR(255) NOT NULL,
    `file_name`  VARCHAR(255) NOT NULL,
    `file_size`  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_version_version` (`version`),
    KEY `idx_app_version_created` (`created_at`),
    CONSTRAINT `fk_app_version_user` FOREIGN KEY (`created_by`)
        REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs
  COMMENT = '桌面端安装包版本';
