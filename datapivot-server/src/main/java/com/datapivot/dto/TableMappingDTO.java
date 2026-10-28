package com.datapivot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 表映射请求对象
 */
@Data
public class TableMappingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 128, message = "catalog 长度不能超过128字符")
    private String sourceCatalog;

    @Size(max = 128, message = "schema 长度不能超过128字符")
    private String sourceSchema;

    /** 源表名 */
    @NotBlank(message = "源表不能为空")
    @Size(max = 200, message = "表名长度不能超过200字符")
    @Pattern(regexp = "^[A-Za-z0-9_$#\\u4e00-\\u9fa5]+$", message = "源表名含非法字符")
    private String sourceTable;

    @Size(max = 128, message = "catalog 长度不能超过128字符")
    private String targetCatalog;

    @Size(max = 128, message = "schema 长度不能超过128字符")
    private String targetSchema;

    /** 目标表名 */
    @NotBlank(message = "目标表不能为空")
    @Size(max = 200, message = "表名长度不能超过200字符")
    @Pattern(regexp = "^[A-Za-z0-9_$#\\u4e00-\\u9fa5]+$", message = "目标表名含非法字符")
    private String targetTable;

    /** 增量依据字段（源表字段，增量同步时必填） */
    @Size(max = 128, message = "增量字段长度不能超过128字符")
    @Pattern(regexp = "^[A-Za-z0-9_$#\\u4e00-\\u9fa5]+$", message = "增量字段名含非法字符")
    private String incColumn;

    /** 字段映射（至少配置一条） */
    @NotEmpty(message = "每组表映射至少配置一个字段映射")
    @Valid
    private List<ColumnMappingDTO> columns;
}
