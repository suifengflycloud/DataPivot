package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 同步执行进度（前端轮询对象）
 */
@Data
public class SyncProgressVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行记录 id */
    private Long recordId;

    private Long taskId;

    private String taskName;

    /** RUNNING/SUCCESS/FAILED */
    private String status;

    /** 总条数（各源表 COUNT 汇总） */
    private long total;

    /** 已处理条数 = 成功 + 失败 */
    private long processed;

    private long successCount;

    private long failCount;

    /** 进度百分比 0-100 */
    private int percent;

    /** 当前正在同步的表 */
    private String currentTable;

    /** 最近执行日志（时间正序） */
    private List<String> logs = new ArrayList<>();
}
