package com.datapivot.config;

import com.datapivot.common.BusinessException;
import com.datapivot.common.R;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器
 * <p>拦截所有异常并转换为统一返回体，保证前端永远拿到友好提示，而非堆栈信息；
 * 系统级异常仅在服务端日志中记录堆栈，绝不外抛到响应体。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：预期内失败，仅记录简要信息（不打堆栈，不产生 ERROR 噪音） */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.info("业务异常: {}", e.getMessage());
        return R.fail(e.getMessage());
    }

    /** @RequestBody 参数校验失败（Spring Validation） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? "请求参数不合法" : fieldError.getDefaultMessage();
        return R.fail("参数校验失败: " + msg);
    }

    /** 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? "请求参数不合法" : fieldError.getDefaultMessage();
        return R.fail("参数校验失败: " + msg);
    }

    /** 单参数校验失败（@RequestParam 上的约束注解） */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        return R.fail("参数校验失败: " + e.getMessage());
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail("缺少必填参数: " + e.getParameterName());
    }

    /** 参数类型不匹配 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.fail("参数类型不正确: " + e.getName());
    }

    /** 请求体不可读（JSON 格式错误） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        return R.fail("请求体格式错误，请检查 JSON 数据");
    }

    /** 请求方式不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail("请求方式不支持: " + e.getMethod());
    }

    /** 404 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public R<Void> handleNotFound(NoHandlerFoundException e) {
        return R.fail("接口不存在: " + e.getRequestURL());
    }

    /** 唯一键冲突 */
    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.info("数据唯一性冲突: {}", e.getMessage());
        return R.fail("保存失败：数据已存在（唯一键冲突）");
    }

    /** 数据访问异常：记录堆栈，对外只给友好提示 */
    @ExceptionHandler(DataAccessException.class)
    public R<Void> handleDataAccess(DataAccessException e) {
        log.error("数据库访问异常", e);
        return R.fail("数据库操作异常，请检查服务端日志");
    }

    /** 兜底异常：任何未预期异常都不向前端暴露堆栈 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统未处理异常", e);
        return R.fail("系统繁忙，请稍后重试");
    }
}
