package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务执行记录返回对象
 */
@Data
public class RecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long taskId;

    private String taskName;

    /** MANUAL/SCHEDULED */
    private String triggerType;

    /** RUNNING/SUCCESS/FAILED */
    private String status;

    private Long totalCount;

    private Long successCount;

    private Long failCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 执行日志（详情接口返回全量） */
    private String errorLog;
}
