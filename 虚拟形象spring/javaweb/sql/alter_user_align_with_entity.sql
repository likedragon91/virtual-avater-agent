-- 2026-10-23 将 虚拟形象.user 表结构对齐 UserService/user 实体
-- 执行前表内无数据（0 行），不涉及数据迁移
ALTER TABLE `user`
    DROP CHECK `user_chk_1`,
    CHANGE COLUMN `password_hash` `password` varchar(100) COLLATE utf8mb4_zh_0900_as_cs NOT NULL,
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '0-禁用 1-正常 2-锁定',
    MODIFY COLUMN `nickname` varchar(32) COLLATE utf8mb4_zh_0900_as_cs DEFAULT NULL,
    ADD COLUMN `salt` varchar(32) NOT NULL DEFAULT '' COMMENT '密码盐（32位十六进制）' AFTER `password`,
    ADD COLUMN `avatar` varchar(255) DEFAULT NULL COMMENT '头像' AFTER `nickname`,
    ADD COLUMN `phone` varchar(20) DEFAULT NULL COMMENT '手机号' AFTER `avatar`,
    ADD COLUMN `gender` tinyint NOT NULL DEFAULT 0 COMMENT '0-未知 1-男 2-女' AFTER `phone`,
    ADD COLUMN `gender_display_name` varchar(16) DEFAULT NULL AFTER `gender`,
    ADD COLUMN `id_card` varchar(18) DEFAULT NULL COMMENT '身份证号' AFTER `gender_display_name`,
    ADD COLUMN `birthday` date DEFAULT NULL COMMENT '生日' AFTER `id_card`,
    ADD COLUMN `user_type` tinyint NOT NULL DEFAULT 1 COMMENT '1-普通用户 2-管理员 3-VIP' AFTER `birthday`,
    ADD COLUMN `user_type_display_name` varchar(16) DEFAULT NULL AFTER `user_type`,
    ADD COLUMN `status_display_name` varchar(16) DEFAULT NULL AFTER `status`,
    ADD CONSTRAINT `chk_user_status` CHECK (`status` IN (0, 1, 2));

-- 补充：username 长度对齐 UserRegisterCommandDTO 的 50 字符上限
ALTER TABLE `user`
    MODIFY COLUMN `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL;

