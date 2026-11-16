-- 首次部署：创建数据库（表结构由应用启动时的 Flyway 自动创建）
-- 在 MySQL 8.0 中执行：
--   mysql -u root -p < deploy/sql/00_create_database.sql

CREATE DATABASE IF NOT EXISTS `虚拟形象`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_zh_0900_as_cs;

-- 可选：为应用创建独立数据库账号（把 CHANGE_ME 换成强密码）
-- CREATE USER IF NOT EXISTS 'avatar'@'localhost' IDENTIFIED BY 'CHANGE_ME';
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
--     ON `虚拟形象`.* TO 'avatar'@'localhost';
-- FLUSH PRIVILEGES;
