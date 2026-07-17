package com.ailearn.common; // 声明包名，属于通用模块

import jakarta.validation.ConstraintViolation; // 导入约束违反对象，用于提取校验失败的具体信息
import jakarta.validation.ConstraintViolationException; // 导入约束违反异常，由@Validated校验方法参数时抛出
import lombok.extern.slf4j.Slf4j; // 导入Lombok日志注解，自动生成log对象
import org.springframework.http.HttpStatus; // 导入HTTP状态码枚举，用于设置响应状态
import org.springframework.http.converter.HttpMessageNotReadableException; // 导入HTTP消息不可读异常，JSON解析失败时抛出
import org.springframework.security.access.AccessDeniedException; // 导入权限不足异常，Spring Security框架抛出
import org.springframework.security.core.AuthenticationException; // 导入认证异常基类，Spring Security框架抛出
import org.springframework.validation.BindException; // 导入表单绑定异常，表单数据绑定失败时抛出
import org.springframework.validation.FieldError; // 导入字段错误对象，包含校验失败的字段名和错误信息
import org.springframework.web.bind.MethodArgumentNotValidException; // 导入方法参数无效异常，@Valid校验@RequestBody时抛出
import org.springframework.web.bind.annotation.ExceptionHandler; // 导入异常处理器注解，指定处理的异常类型
import org.springframework.web.bind.annotation.ResponseStatus; // 导入响应状态注解，设置HTTP状态码
import org.springframework.web.bind.annotation.RestControllerAdvice; // 导入全局异常处理注解，拦截所有Controller异常
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException; // 导入参数类型不匹配异常

import java.util.stream.Collectors; // 导入Stream收集器，用于拼接多个校验错误信息

/**
 * 全局异常处理器
 * 使用@RestControllerAdvice注解统一处理Controller层抛出的异常，
 * 将各种异常转换为统一的Result格式返回给前端，同时记录异常日志便于排查问题
 *
 * @author AiLearn Platform
 */
@Slf4j // Lombok注解，自动注入log对象用于日志记录
@RestControllerAdvice // 全局异常处理注解，拦截所有@RestController中抛出的异常，返回JSON响应
public class GlobalExceptionHandler { // 全局异常处理器类定义

    /**
     * 处理业务异常BusinessException
     * 业务逻辑中主动抛出的异常，使用预定义的错误码和消息
     *
     * @param e 业务异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class) // 指定本方法处理BusinessException类型的异常
    @ResponseStatus(HttpStatus.OK) // 业务异常返回HTTP 200状态码，具体错误信息在Result的code字段中区分
    public Result<Void> handleBusinessException(BusinessException e) { // 处理业务异常方法
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage()); // 记录警告级别日志，包含错误码和消息
        return Result.error(e.getErrorCode(), e.getDetail()); // 返回统一错误响应，包含错误码和详细信息
    } // handleBusinessException方法结束

    /**
     * 处理@Valid注解参数校验异常（@RequestBody参数）
     * 当使用@Valid校验请求体参数失败时抛出
     *
     * @param e 参数校验异常实例
     * @return 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class) // 指定本方法处理@RequestBody参数校验失败异常
    @ResponseStatus(HttpStatus.BAD_REQUEST) // 参数校验失败返回HTTP 400状态码
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) { // 处理请求体校验异常
        String message = e.getBindingResult().getFieldErrors().stream() // 获取所有字段错误并转为Stream流
                .map(FieldError::getDefaultMessage) // 提取每个字段错误的默认消息文本
                .collect(Collectors.joining("; ")); // 将所有错误消息用分号拼接为一个字符串
        log.warn("请求体参数校验失败: {}", message); // 记录警告日志，包含所有校验失败信息
        return Result.error(ErrorCode.SYSTEM_PARAM_VALIDATION_ERROR.getCode(), message); // 返回参数校验错误响应
    } // handleMethodArgumentNotValidException方法结束

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
    @ExceptionHandler(Exception.class) // 兆底异常处理器，捕获所有未被前面处理器处理的异常
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // 系统内部错误返回HTTP 500状态码
    public Result<Void> handleException(Exception e) { // 处理所有未预期异常的方法
        log.error("系统内部异常", e); // 记录错误级别日志，包含完整异常堆栈便于排查
        return Result.error(ErrorCode.SYSTEM_INTERNAL_ERROR); // 返回系统内部错误响应，不暴露具体异常信息防止信息泄露
    } // handleException方法结束
} // GlobalExceptionHandler类结束
