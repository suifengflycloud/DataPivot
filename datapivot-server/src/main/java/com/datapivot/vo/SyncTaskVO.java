package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 同步任务列表返回对象
 */
@Data
public class SyncTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String taskName;

    private Long sourceConnId;

    /** 源连接名称（联查填充） */
    private String sourceConnName;

    private Long targetConnId;

    /** 目标连接名称（联查填充） */
    private String targetConnName;

    /** 冲突策略编码 */
    private String conflictStrategy;

    /** 冲突策略显示名 */
    private String conflictStrategyLabel;

    private Integer batchSize;

    /** 是否全量同步 */
    private Boolean fullSync;

    private String remark;

    /** 表映射数量（统计填充） */
    private Long mappingCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
