package com.datapivot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 结构对比请求对象
 */
@Data
public class CompareDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 基准库连接 id */
    @NotNull(message = "请选择基准库连接")
    private Long baseConnId;

    /** 对比库连接 id（DDL 脚本的执行目标） */
    @NotNull(message = "请选择对比库连接")
    private Long targetConnId;
}
