-- ============================================================
-- 升级脚本：增量同步功能（2026-10-26）
-- 适用：已按旧版 datapivot_init.sql 初始化过的库
-- MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，重复执行报 Duplicate column 可忽略
-- ============================================================

USE `datapivot`;

-- 表映射表：增量字段与增量水位
ALTER TABLE `dp_table_mapping`
  ADD COLUMN `inc_column`     VARCHAR(128) DEFAULT NULL COMMENT '增量依据字段（源表字段，如 update_time/自增id，全量同步为 null）' AFTER `target_table`,
  ADD COLUMN `inc_last_value` VARCHAR(64)  DEFAULT NULL COMMENT '上次增量同步水位值（增量字段Max值快照，null 表示首次执行将全量拉取）' AFTER `inc_column`;

-- 任务表：full_sync 含义注释修正（列结构不变，仅注释）
ALTER TABLE `dp_sync_task`
  MODIFY COLUMN `full_sync` TINYINT NOT NULL DEFAULT 1 COMMENT '是否全量同步: 1全量 0增量';
