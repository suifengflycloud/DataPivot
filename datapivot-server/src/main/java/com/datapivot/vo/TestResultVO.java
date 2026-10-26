package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 连接测试结果
 */
@Data
public class TestResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否连通 */
    private boolean success;

    /** 详细信息（成功时为数据库产品名版本，失败时为具体错误） */
    private String message;

    /** 耗时（毫秒） */
    private long costMs;

    /** 数据库产品描述（成功时返回，如 MySQL 5.7.28） */
    private String dbProduct;
}
