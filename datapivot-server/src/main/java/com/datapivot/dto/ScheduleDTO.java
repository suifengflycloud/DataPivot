package com.datapivot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 定时任务配置保存请求对象
 */
@Data
public class ScheduleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联同步任务 id */
    @NotNull(message = "请选择同步任务")
    private Long taskId;

    /** Cron 表达式（6 位：秒 分 时 日 月 周） */
    @NotBlank(message = "Cron 表达式不能为空")
    @Size(max = 64, message = "Cron 表达式长度不能超过64字符")
    @Pattern(regexp = "^[\\d*,\\-?/\\sA-Za-z]+$", message = "Cron 表达式含非法字符")
    private String cronExpr;

    /** 任务描述 */
    @NotBlank(message = "任务描述不能为空")
    @Size(max = 200, message = "任务描述长度不能超过200字符")
    private String jobDesc;

    /** 负责人 */
    @NotBlank(message = "负责人不能为空")
    @Size(max = 50, message = "负责人长度不能超过50字符")
    private String owner;
}
