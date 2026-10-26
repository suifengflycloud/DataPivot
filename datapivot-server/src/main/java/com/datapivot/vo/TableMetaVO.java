package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 表元数据（来源于 JDBC DatabaseMetaData#getTables）
 */
@Data
public class TableMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** catalog（MySQL 为库名） */
    private String catalog;

    /** schema（Oracle/DM/PG/金仓 有效） */
    private String schema;

    /** 表名 */
    private String tableName;

    /** 展示名：有 schema 时为 schema.table */
    private String displayName;

    /** 表注释 */
    private String remarks;
}
