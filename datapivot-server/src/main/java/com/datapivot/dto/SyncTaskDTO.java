package com.datapivot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 同步任务保存请求对象（任务 + 表映射 + 字段映射一次性提交）
 */
@Data
public class SyncTaskDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 100, message = "任务名称长度不能超过100字符")
    private String taskName;

    /** 源数据库连接 id */
    @NotNull(message = "请选择源数据库连接")
    private Long sourceConnId;

    /** 目标数据库连接 id */
    @NotNull(message = "请选择目标数据库连接")
    private Long targetConnId;

    /** 主键冲突策略 */
    @NotBlank(message = "请选择主键冲突策略")
    @Pattern(regexp = "OVERWRITE|SKIP|ERROR", message = "不合法的主键冲突策略")
    private String conflictStrategy;

    /** 同步批次大小 */
    @NotNull(message = "同步批次大小不能为空")
    @Min(value = 1, message = "批次大小最小为 1")
    @Max(value = 100000, message = "批次大小最大为 100000")
    private Integer batchSize;

    /** 是否全量同步（false 为预留增量） */
    @NotNull(message = "请选择同步方式")
    private Boolean fullSync;

    @Size(max = 300, message = "备注长度不能超过300字符")
    private String remark;

    /** 表映射列表 */
    @NotEmpty(message = "请至少配置一组表映射")
    @Valid
    private List<TableMappingDTO> mappings;
}
