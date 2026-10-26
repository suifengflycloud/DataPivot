package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 字段元数据（来源于 JDBC DatabaseMetaData#getColumns / getPrimaryKeys）
 */
@Data
public class ColumnMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段名 */
    private String columnName;

    /** 数据库原生类型名（如 VARCHAR2） */
    private String typeName;

    /** java.sql.Types 类型码 */
    private Integer dataType;

    /** 类型大类：STRING/NUMBER/DATETIME/BOOLEAN/BINARY/OTHER（前端类型转换提示用） */
    private String typeCategory;

    /** 长度 */
    private Integer size;

    /** 小数精度 */
    private Integer digits;

    /** 是否允许为空 */
    private Boolean nullable;

    /** 是否主键 */
    private Boolean pk;

    /** 默认值 */
    private String defaultValue;

    /** 字段注释 */
    private String remarks;
}
