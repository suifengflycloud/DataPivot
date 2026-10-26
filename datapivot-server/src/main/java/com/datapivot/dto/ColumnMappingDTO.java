package com.datapivot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 字段映射请求对象
 */
@Data
public class ColumnMappingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 源字段名 */
    @NotBlank(message = "源字段名不能为空")
    @Size(max = 128, message = "字段名长度不能超过128字符")
    @Pattern(regexp = "^[A-Za-z0-9_$#\\u4e00-\\u9fa5]+$", message = "源字段名含非法字符")
    private String sourceColumn;

    /** 目标字段名 */
    @NotBlank(message = "目标字段名不能为空")
    @Size(max = 128, message = "字段名长度不能超过128字符")
    @Pattern(regexp = "^[A-Za-z0-9_$#\\u4e00-\\u9fa5]+$", message = "目标字段名含非法字符")
    private String targetColumn;
}
