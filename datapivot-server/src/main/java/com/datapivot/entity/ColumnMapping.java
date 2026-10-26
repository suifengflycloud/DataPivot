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
 * 字段映射实体：某组表映射下 源字段→目标字段（子表，物理删除）
 */
@Data
@TableName("dp_column_mapping")
public class ColumnMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属表映射 id */
    private Long mappingId;

    /** 源字段名 */
    private String sourceColumn;

    /** 目标字段名 */
    private String targetColumn;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
