package com.datapivot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表映射实体：任务下的一组 源表→目标表 映射（子表，物理删除）
 */
@Data
@TableName("dp_table_mapping")
public class TableMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属同步任务 id */
    private Long taskId;

    /** 源表 catalog（MySQL 为库名，其他库为 null） */
    private String sourceCatalog;

    /** 源表 schema（Oracle/DM/PG/金仓 有效） */
    private String sourceSchema;

    /** 源表名 */
    private String sourceTable;

    /** 目标表 catalog */
    private String targetCatalog;

    /** 目标表 schema */
    private String targetSchema;

    /** 目标表名 */
    private String targetTable;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
