-- ============================================================
-- xxl_job 库 schema 对齐脚本（本地演示环境）
--
-- 现象：执行器注册成功，但 admin 的「执行器管理 / 任务管理」报
--       Unknown column 't.title' 或 't.job_desc'。
-- 原因：当前 admin 容器是 3.4.2 镜像，jar 内 Mapper 仍按 2.x 的
--       title / job_desc 查询；而 xxl_job 库是 3.x 的 name 列。
-- 处置：补 2.x 列并从 name 回填；保留 name 列并改为可空，兼容两种写入。
-- 执行前先备份 xxl_job 库。
-- ============================================================
USE `xxl_job`;

ALTER TABLE `xxl_job_group`
  ADD COLUMN `title` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '执行器名称（2.x Mapper 使用）' AFTER `app_name`;

UPDATE `xxl_job_group` SET `title` = `name` WHERE `title` = '';

ALTER TABLE `xxl_job_group`
  MODIFY COLUMN `name` VARCHAR(64) NULL DEFAULT NULL COMMENT '3.x 列，保留兼容';

ALTER TABLE `xxl_job_info`
  ADD COLUMN `job_desc` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '任务描述（2.x Mapper 使用）' AFTER `job_group`;

UPDATE `xxl_job_info` SET `job_desc` = `name` WHERE `job_desc` = '';

ALTER TABLE `xxl_job_info`
  MODIFY COLUMN `name` VARCHAR(255) NULL DEFAULT NULL COMMENT '3.x 列，保留兼容';
