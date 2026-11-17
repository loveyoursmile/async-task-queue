package com.example.asyncqueue.common.exception;

import com.example.asyncqueue.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * @author async-queue
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 背压拒绝：返回 429，明确告知调用方稍后重试。 */
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(QueueFullException.class)
    public Result<Void> handleQueueFull(QueueFullException ex) {
        log.warn("队列已满，拒绝提交：{}", ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /** 业务异常：返回 400。 */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException ex) {
        log.warn("业务异常 code={}：{}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /** 参数校验异常：返回 400。 */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public Result<Void> handleParam(Exception ex) {
        log.warn("参数异常：{}", ex.getMessage());
        return Result.fail("INVALID_PARAM", ex.getMessage());
    }

    /** 未预期异常：返回 500，记录完整堆栈。 */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception ex) {
        log.error("系统异常", ex);
        return Result.fail("SYSTEM_ERROR", "系统异常，请稍后重试");
    }
}
