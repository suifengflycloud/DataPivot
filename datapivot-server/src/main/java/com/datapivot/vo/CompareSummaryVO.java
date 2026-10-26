package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 结构对比结果汇总
 */
@Data
public class CompareSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新增表数（基准库有、对比库无） */
    private long addedTables;

    /** 删除表数（对比库有、基准库无） */
    private long deletedTables;

    /** 有字段级变更的表数 */
    private long changedTables;

    /** 差异明细总条数 */
    private long totalDiffItems;
}
