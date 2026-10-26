package com.datapivot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 同步任务执行记录实体
 */
@Data
@TableName("dp_sync_record")
public class SyncRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 同步任务 id */
    private Long taskId;

    /** 任务名称快照 */
    private String taskName;

    /** 触发类型：MANUAL/SCHEDULED */
    private String triggerType;

    /** 状态：RUNNING/SUCCESS/FAILED */
    private String status;

    /** 已处理条数 */
    private Long totalCount;

    /** 成功条数 */
    private Long successCount;

    /** 失败条数 */
    private Long failCount;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 执行日志（含错误信息） */
    private String errorLog;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
