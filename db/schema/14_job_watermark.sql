-- ============================================================
-- XXL-JOB 水位表（增量/全量同步任务用）
-- 独立脚本，避免重跑 campus_life_schema.sql 时清空业务数据
-- 执行方式：在 campus_life 库里整段运行
-- ============================================================
USE `campus_life`;

CREATE TABLE IF NOT EXISTS `t_job_watermark` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `job_name` VARCHAR(64) NOT NULL COMMENT '任务名，如 shopEsFullSyncJob',
  `shard_index` INT NOT NULL DEFAULT 0 COMMENT '分片下标',
  `biz_date` DATE NOT NULL COMMENT '业务日期，用于按天重置游标',
  `cursor` BIGINT NOT NULL DEFAULT 0 COMMENT '已处理到的游标（通常是 id）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0进行中 1完成',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_shard_date` (`job_name`, `shard_index`, `biz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度任务水位';
