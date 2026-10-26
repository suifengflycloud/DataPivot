package com.datapivot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据同步任务实体
 */
@Data
@TableName("dp_sync_task")
public class SyncTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称 */
    private String taskName;

    /** 源数据库连接 id */
    private Long sourceConnId;

    /** 目标数据库连接 id */
    private Long targetConnId;

    /** 主键冲突策略：OVERWRITE/SKIP/ERROR */
    private String conflictStrategy;

    /** 同步批次大小（条/批） */
    private Integer batchSize;

    /** 是否全量同步：1 是 0 增量（预留） */
    private Integer fullSync;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
