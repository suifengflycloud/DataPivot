package com.datapivot.enums;

import lombok.Getter;

/**
 * 结构对比差异类型
 * <p>severity 用于前端颜色标注：ADD=绿色、DELETE=红色、MODIFY=黄色。</p>
 */
@Getter
public enum DiffTypeEnum {

    /** 基准库有、对比库无的表（需在对比库新建） */
    TABLE_ADD("TABLE_ADD", "新增表", Severity.ADD),
    /** 对比库有、基准库无的表 */
    TABLE_DELETE("TABLE_DELETE", "删除表", Severity.DELETE),
    /** 两库都有但存在字段级差异的表 */
    TABLE_MODIFY("TABLE_MODIFY", "修改表", Severity.MODIFY),

    /** 新增字段 */
    COLUMN_ADD("COLUMN_ADD", "新增字段", Severity.ADD),
    /** 删除字段 */
    COLUMN_DELETE("COLUMN_DELETE", "删除字段", Severity.DELETE),
    /** 字段类型变更 */
    COLUMN_TYPE("COLUMN_TYPE", "类型变更", Severity.MODIFY),
    /** 字段长度变更 */
    COLUMN_LENGTH("COLUMN_LENGTH", "长度变更", Severity.MODIFY),
    /** 是否允许为空变更 */
    COLUMN_NULLABLE("COLUMN_NULLABLE", "可空性变更", Severity.MODIFY),
    /** 主键变更 */
    COLUMN_PK("COLUMN_PK", "主键变更", Severity.MODIFY),
    /** 字段注释变更 */
    COLUMN_COMMENT("COLUMN_COMMENT", "注释变更", Severity.MODIFY);

    private final String code;
    private final String label;
    private final Severity severity;

    DiffTypeEnum(String code, String label, Severity severity) {
        this.code = code;
        this.label = label;
        this.severity = severity;
    }

    /** 差异严重级别（决定前端标签颜色） */
    public enum Severity {
        ADD, DELETE, MODIFY
    }
}
