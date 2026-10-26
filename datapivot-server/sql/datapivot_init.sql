-- ============================================================
-- DataPivot 多源数据库迁移与结构对比工具 - 项目自身配置库初始化脚本
-- 适用: MySQL 5.7 / 8.0 (utf8mb4)
-- 使用: mysql -uroot -p < datapivot_init.sql  （可直接重复执行，幂等）
-- ============================================================

CREATE DATABASE IF NOT EXISTS `datapivot` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `datapivot`;

-- ------------------------------------------------------------
-- 1. 数据库连接配置表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dp_db_connection` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `conn_name`     VARCHAR(100) NOT NULL COMMENT '连接名称（别名）',
  `db_type`       VARCHAR(20)  NOT NULL COMMENT '数据库类型: MYSQL/ORACLE/DM/KINGBASE/POSTGRESQL',
  `host`          VARCHAR(128) NOT NULL COMMENT '主机IP',
  `port`          INT          NOT NULL COMMENT '端口',
  `db_name`       VARCHAR(128) NOT NULL COMMENT '数据库名/SID/schema',
  `username`      VARCHAR(100) NOT NULL COMMENT '用户名',
  `password`      VARCHAR(512) NOT NULL COMMENT '密码（AES-GCM 加密存储）',
  `driver_class`  VARCHAR(200)          DEFAULT NULL COMMENT '驱动类名',
  `jdbc_url`      VARCHAR(600) NOT NULL COMMENT 'JDBC URL',
  `extra_params`  VARCHAR(400)          DEFAULT NULL COMMENT '扩展连接参数',
  `remark`        VARCHAR(300)          DEFAULT NULL COMMENT '备注',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_conn_name` (`conn_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据库连接配置表';

-- ------------------------------------------------------------
-- 2. 同步任务表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dp_sync_task` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_name`         VARCHAR(100) NOT NULL COMMENT '任务名称',
  `source_conn_id`    BIGINT       NOT NULL COMMENT '源数据库连接id',
  `target_conn_id`    BIGINT       NOT NULL COMMENT '目标数据库连接id',
  `conflict_strategy` VARCHAR(20)  NOT NULL DEFAULT 'OVERWRITE' COMMENT '主键冲突策略: OVERWRITE/SKIP/ERROR',
  `batch_size`        INT          NOT NULL DEFAULT 1000 COMMENT '同步批次大小(条/批)',
  `full_sync`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否全量同步: 1全量 0增量(预留)',
  `remark`            VARCHAR(300)          DEFAULT NULL COMMENT '备注',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_source_conn` (`source_conn_id`),
  KEY `idx_target_conn` (`target_conn_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据同步任务表';

-- ------------------------------------------------------------
-- 3. 表映射表（源表 -> 目标表，同步任务子表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dp_table_mapping` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id`        BIGINT       NOT NULL COMMENT '所属同步任务id',
  `source_catalog` VARCHAR(128)          DEFAULT NULL COMMENT '源表catalog（MySQL为库名）',
  `source_schema`  VARCHAR(128)          DEFAULT NULL COMMENT '源表schema',
  `source_table`   VARCHAR(200) NOT NULL COMMENT '源表名',
  `target_catalog` VARCHAR(128)          DEFAULT NULL COMMENT '目标表catalog',
  `target_schema`  VARCHAR(128)          DEFAULT NULL COMMENT '目标表schema',
  `target_table`   VARCHAR(200) NOT NULL COMMENT '目标表名',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '同步表映射表';

-- ------------------------------------------------------------
-- 4. 字段映射表（源字段 -> 目标字段，表映射子表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dp_column_mapping` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `mapping_id`    BIGINT       NOT NULL COMMENT '所属表映射id',
  `source_column` VARCHAR(128) NOT NULL COMMENT '源字段名',
  `target_column` VARCHAR(128) NOT NULL COMMENT '目标字段名',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_mapping_id` (`mapping_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '同步字段映射表';

-- ------------------------------------------------------------
-- 5. 任务执行记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dp_sync_record` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id`       BIGINT        NOT NULL COMMENT '同步任务id',
  `task_name`     VARCHAR(100)  NOT NULL COMMENT '任务名称快照',
  `trigger_type`  VARCHAR(20)   NOT NULL COMMENT '触发类型: MANUAL/SCHEDULED',
  `status`        VARCHAR(20)   NOT NULL COMMENT '状态: RUNNING/SUCCESS/FAILED',
  `total_count`   BIGINT        NOT NULL DEFAULT 0 COMMENT '已处理条数',
  `success_count` BIGINT        NOT NULL DEFAULT 0 COMMENT '成功条数',
  `fail_count`    BIGINT        NOT NULL DEFAULT 0 COMMENT '失败条数',
  `start_time`    DATETIME               DEFAULT NULL COMMENT '开始时间',
  `end_time`      DATETIME               DEFAULT NULL COMMENT '结束时间',
  `error_log`     MEDIUMTEXT            COMMENT '执行日志（含错误信息）',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '同步任务执行记录表';

-- ------------------------------------------------------------
-- 6. 定时任务配置表（与 XXL-Job 调度任务对应）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dp_schedule_config` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id`     BIGINT       NOT NULL COMMENT '关联同步任务id',
  `cron_expr`   VARCHAR(64)  NOT NULL COMMENT 'Cron表达式（6位）',
  `job_desc`    VARCHAR(200) NOT NULL COMMENT '任务描述',
  `owner`       VARCHAR(50)  NOT NULL COMMENT '负责人',
  `xxl_job_id`  INT                   DEFAULT NULL COMMENT 'XXL-Job调度中心任务id',
  `status`      VARCHAR(10)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '定时任务配置表';
