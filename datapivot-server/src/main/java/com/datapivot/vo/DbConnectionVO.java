package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库连接返回对象（密码仅返回脱敏形式，绝不返回明文/密文）
 */
@Data
public class DbConnectionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 连接名称 */
    private String connName;

    /** 数据库类型编码 */
    private String dbType;

    /** 数据库类型显示名 */
    private String dbTypeLabel;

    private String host;

    private Integer port;

    private String dbName;

    private String username;

    /** 脱敏密码：仅展示前两位 + **** + 后两位 */
    private String passwordMasked;

    private String driverClass;

    private String jdbcUrl;

    private String extraParams;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
