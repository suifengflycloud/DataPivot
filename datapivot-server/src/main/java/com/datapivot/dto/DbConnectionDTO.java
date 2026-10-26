package com.datapivot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 数据库连接保存 / 连通性测试 请求对象
 */
@Data
public class DbConnectionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（更新时必传） */
    private Long id;

    /** 连接名称 */
    @NotBlank(message = "连接名称不能为空")
    @Size(max = 100, message = "连接名称长度不能超过100字符")
    private String connName;

    /** 数据库类型 */
    @NotBlank(message = "请选择数据库类型")
    @Pattern(regexp = "MYSQL|ORACLE|DM|KINGBASE|POSTGRESQL", message = "不支持的数据库类型")
    private String dbType;

    /** IP 地址（兼容 hostname） */
    @NotBlank(message = "IP地址不能为空")
    @Size(max = 128, message = "IP地址长度不能超过128字符")
    @Pattern(regexp = "^[A-Za-z0-9.\\-_]+$", message = "IP地址格式不正确")
    private String host;

    /** 端口 */
    @NotNull(message = "端口不能为空")
    @Min(value = 1, message = "端口范围 1-65535")
    @Max(value = 65535, message = "端口范围 1-65535")
    private Integer port;

    /** 数据库名 / SID / schema */
    @NotBlank(message = "数据库名不能为空")
    @Size(max = 128, message = "数据库名长度不能超过128字符")
    private String dbName;

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 100, message = "用户名长度不能超过100字符")
    private String username;

    /**
     * 密码：新建时必填；编辑时留空表示不修改；测试连接时为明文。
     * 该字段仅用于传输，存储前统一 AES 加密，返回时脱敏。
     */
    @Size(max = 200, message = "密码长度不能超过200字符")
    private String password;

    /** 驱动类名（按类型自动带出，可修改） */
    @Size(max = 200, message = "驱动类名长度不能超过200字符")
    private String driverClass;

    /** JDBC URL（自动拼接，可修改） */
    @NotBlank(message = "JDBC URL 不能为空")
    @Size(max = 600, message = "JDBC URL 长度不能超过600字符")
    private String jdbcUrl;

    /** 扩展连接参数（可选） */
    @Size(max = 400, message = "扩展参数长度不能超过400字符")
    private String extraParams;

    /** 备注 */
    @Size(max = 300, message = "备注长度不能超过300字符")
    private String remark;
}
