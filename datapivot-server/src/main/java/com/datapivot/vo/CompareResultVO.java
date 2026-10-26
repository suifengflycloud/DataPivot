package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构对比结果（差异明细 + DDL 升级脚本）
 */
@Data
public class CompareResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 基准库名称 */
    private String baseConnName;

    /** 对比库名称 */
    private String targetConnName;

    /** 目标库类型编码（决定 DDL 方言） */
    private String targetDbType;

    /** 两库类型是否不一致（true 时前端给出提示） */
    private boolean typeMismatch;

    private CompareSummaryVO summary = new CompareSummaryVO();

    /** 表级差异列表（按表名排序） */
    private List<TableDiffVO> tableDiffs = new ArrayList<>();

    /** 自动生成的 DDL 升级脚本（以对比库方言输出） */
    private String ddlScript;
}
