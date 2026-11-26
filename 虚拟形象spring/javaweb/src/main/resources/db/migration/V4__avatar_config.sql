-- 用户形象配置：当前只存像素驱动的预设与配色，后续可扩展 pixel-pack / live2d
CREATE TABLE IF NOT EXISTS `avatar_config` (
    `user_id` BIGINT UNSIGNED NOT NULL,
    `driver` VARCHAR(32) NOT NULL DEFAULT 'pixel',
    `config` JSON NOT NULL,
    `revision` INT NOT NULL DEFAULT 1,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_avatar_config_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_zh_0900_as_cs COMMENT = '用户形象配置';
