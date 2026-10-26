package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 字段级差异项
 */
@Data
public class ColumnDiffVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段名 */
    private String columnName;

    /** 差异类型编码（COLUMN_ADD/COLUMN_DELETE/COLUMN_TYPE/COLUMN_LENGTH/COLUMN_NULLABLE/COLUMN_PK/COLUMN_COMMENT） */
    private String diffType;

    /** 差异类型显示名 */
    private String diffTypeLabel;

    /** 变更前值（对比库现状） */
    private String before;

    /** 变更后值（基准库目标值） */
    private String after;

    /** 人读描述（前端直接展示） */
    private String description;

    /** 基准库字段完整元数据（生成 DDL 用；仅 COLUMN_ADD 与字段级修改类差异携带） */
    private ColumnMetaVO baseColumn;
}
