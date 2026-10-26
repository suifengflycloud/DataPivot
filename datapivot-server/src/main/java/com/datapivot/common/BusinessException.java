package com.datapivot.common;

import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常：用于在各层传递"预期内"的失败信息（如参数不合法、连接失败等）。
 * <p>由全局异常处理器捕获后原样返回给前端，不打印堆栈到响应体。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
