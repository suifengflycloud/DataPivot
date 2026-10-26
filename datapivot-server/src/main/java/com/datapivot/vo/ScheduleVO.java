package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务配置返回对象
 */
@Data
public class ScheduleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long taskId;

    /** 关联同步任务名称 */
    private String taskName;

    private String cronExpr;

    private String jobDesc;

    private String owner;

    private Integer xxlJobId;

    /** ENABLED/DISABLED */
    private String status;

    /** 下次执行时间（按 Cron 计算） */
    private LocalDateTime nextTriggerTime;

    /** 最近一次执行结果：SUCCESS/FAILED/null */
    private String lastStatus;

    /** 最近一次执行结束时间 */
    private LocalDateTime lastEndTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
