package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 首页统计返回对象
 */
@Data
public class StatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long connectionCount;

    private long taskCount;

    private long scheduleCount;

    private long todayRecordCount;

    private long todaySuccessCount;

    private long todayFailCount;

    /** 今日成功率（0-100，无记录时为 100） */
    private int successRate;
}
