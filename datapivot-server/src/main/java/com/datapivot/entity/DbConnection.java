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
 * 数据库连接配置实体（密码 AES 加密存储）
 */
@Data
@TableName("dp_db_connection")
public class DbConnection implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 连接名称（自定义别名） */
    private String connName;

    /** 数据库类型：MYSQL/ORACLE/DM/KINGBASE/POSTGRESQL */
    private String dbType;

    /** 主机 IP */
    private String host;

    /** 端口 */
    private Integer port;

    /** 数据库名 / SID / schema */
    private String dbName;

    /** 用户名 */
    private String username;

    /** 密码（AES 密文） */
    private String password;

    /** 驱动类名 */
    private String driverClass;

    /** JDBC URL */
    private String jdbcUrl;

    /** 扩展连接参数 */
    private String extraParams;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 正常 1 已删除 */
    @TableLogic
    private Integer delFlag;
}
