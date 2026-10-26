package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 表级差异（按表分组，含该表下全部字段级差异）
 */
@Data
public class TableDiffVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 表名 */
    private String tableName;

    /** 展示名（带 schema 前缀） */
    private String displayName;

    /** 差异类型编码（TABLE_ADD/TABLE_DELETE/TABLE_MODIFY） */
    private String diffType;

    /** 差异类型显示名 */
    private String diffTypeLabel;

    /** 字段级差异（仅 TABLE_MODIFY 时存在） */
    private List<ColumnDiffVO> columns = new ArrayList<>();

    /** 基准库该表全部字段元数据（仅 TABLE_ADD 时携带，用于生成 CREATE TABLE） */
    private List<ColumnMetaVO> sourceColumns = new ArrayList<>();

    /** 基准库表注释（仅 TABLE_ADD 时携带） */
    private String tableRemark;
}
