package com.datapivot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务配置实体（与 XXL-Job 调度任务一一对应）
 */
@Data
@TableName("dp_schedule_config")
public class ScheduleConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的同步任务 id */
    private Long taskId;

    /** Cron 表达式（6 位，XXL-Job 格式） */
    private String cronExpr;

    /** 任务描述 */
    private String jobDesc;

    /** 负责人 */
    private String owner;

    /** XXL-Job 调度中心中的任务 id */
    private Integer xxlJobId;

    /** 状态：ENABLED/DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
