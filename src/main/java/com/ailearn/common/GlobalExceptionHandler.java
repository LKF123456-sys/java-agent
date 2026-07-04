package com.ailearn.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 使用@RestControllerAdvice注解统一处理Controller层抛出的异常，
 * 将各种异常转换为统一的Result格式返回给前端，同时记录异常日志便于排查问题
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常BusinessException
     * 业务逻辑中主动抛出的异常，使用预定义的错误码和消息
     *
     * @param e 业务异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getErrorCode(), e.getDetail());
    }

    /**
     * 处理@Valid注解参数校验异常（@RequestBody参数）
     * 当使用@Valid校验请求体参数失败时抛出
     *
     * @param e 参数校验异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("请求体参数校验失败: {}", message);
        return Result.error(ErrorCode.SYSTEM_PARAM_VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 处理约束违反异常（@RequestParam/@PathVariable参数校验）
     * 当使用@Validated校验方法参数（非请求体）失败时抛出
     *
     * @param e 约束违反异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("请求参数约束校验失败: {}", message);
        return Result.error(ErrorCode.SYSTEM_PARAM_VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 处理表单绑定异常
     * 当表单数据绑定到对象失败时抛出
     *
     * @param e 绑定异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("表单数据绑定失败: {}", message);
        return Result.error(ErrorCode.SYSTEM_PARAM_VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 处理HTTP消息不可读异常
     * 当请求体JSON解析失败或格式错误时抛出
     *
     * @param e HTTP消息不可读异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(ErrorCode.SYSTEM_PARAM_FORMAT_ERROR);
    }

    /**
     * 处理参数类型不匹配异常
     * 当请求参数类型转换失败时抛出（如字符串转数字失败）
     *
     * @param e 参数类型不匹配异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = String.format("参数'%s'类型错误", e.getName());
        log.warn("请求参数类型不匹配: {}", message);
        return Result.error(ErrorCode.SYSTEM_PARAM_FORMAT_ERROR.getCode(), message);
    }

    /**
     * 处理非法参数异常
     * 当业务逻辑中传入非法参数时抛出
     *
     * @param e 非法参数异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数异常: {}", e.getMessage());
        return Result.error(ErrorCode.SYSTEM_PARAM_VALIDATION_ERROR.getCode(), e.getMessage());
    }

    /**
     * 处理认证异常
     * 当用户未认证或认证失败时抛出（如登录失败、Token无效）
     *
     * @param e 认证异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return Result.error(ErrorCode.AUTH_TOKEN_INVALID);
    }

    /**
     * 处理权限不足异常
     * 当已认证用户访问无权限资源时抛出
     *
     * @param e 权限不足异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.error(ErrorCode.AUTH_ACCESS_DENIED);
    }

    /**
     * 处理所有其他未捕获的异常（兜底处理）
     * 捕获所有未被前面处理器处理的异常，返回500系统内部错误
     *
     * @param e 异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统内部异常", e);
        return Result.error(ErrorCode.SYSTEM_INTERNAL_ERROR);
    }
}
