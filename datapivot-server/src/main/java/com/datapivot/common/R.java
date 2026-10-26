package com.datapivot.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体
 * <p>所有接口统一返回 { code: 200/500, msg: 提示信息, data: 业务数据 }。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功状态码 */
    public static final int SUCCESS = 200;
    /** 失败状态码 */
    public static final int FAIL = 500;

    private int code;
    private String msg;
    private T data;

    private R() {
    }

    private R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(SUCCESS, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS, "操作成功", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(SUCCESS, msg, data);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(FAIL, msg, null);
    }

    public static <T> R<T> fail(String msg, T data) {
        return new R<>(FAIL, msg, data);
    }
}
