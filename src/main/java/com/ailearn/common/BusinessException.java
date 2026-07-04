package com.ailearn.common;

import lombok.Getter;

/**
 * 业务异常类
 * 用于封装业务逻辑中出现的可预期异常，继承自RuntimeException，
 * 包含错误码枚举和可选的详细错误信息，由GlobalExceptionHandler统一捕获处理
 *
 * @author AiLearn Platform
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码枚举
     * 包含错误码和默认错误消息
     */
    private final ErrorCode errorCode;

    /**
     * 详细错误信息
     * 可选字段，用于补充错误的具体上下文信息
     */
    private final String detail;

    /**
     * 构造方法（仅错误码）
     * 使用ErrorCode中定义的默认消息作为异常消息
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    /**
     * 构造方法（错误码 + 详细信息）
     * 使用自定义详细信息拼接默认消息作为异常消息
     *
     * @param errorCode 错误码枚举
     * @param detail    详细错误信息
     */
    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    /**
     * 构造方法（错误码 + 原因）
     * 用于包装其他异常作为业务异常的场景
     *
     * @param errorCode 错误码枚举
     * @param cause     原始异常原因
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, null, cause);
    }

    /**
     * 构造方法（错误码 + 详细信息 + 原因）
     * 完整构造方法，支持所有参数组合
     *
     * @param errorCode 错误码枚举
     * @param detail    详细错误信息
     * @param cause     原始异常原因
     */
    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(buildMessage(errorCode, detail), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    /**
     * 静态工厂方法（仅错误码）
     * 提供更简洁的异常创建方式
     *
     * @param errorCode 错误码枚举
     * @return BusinessException实例
     */
    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    /**
     * 静态工厂方法（错误码 + 详细信息）
     * 提供更简洁的异常创建方式
     *
     * @param errorCode 错误码枚举
     * @param detail    详细错误信息
     * @return BusinessException实例
     */
    public static BusinessException of(ErrorCode errorCode, String detail) {
        return new BusinessException(errorCode, detail);
    }

    /**
     * 静态工厂方法（错误码 + 原因）
     * 提供更简洁的异常创建方式
     *
     * @param errorCode 错误码枚举
     * @param cause     原始异常原因
     * @return BusinessException实例
     */
    public static BusinessException of(ErrorCode errorCode, Throwable cause) {
        return new BusinessException(errorCode, cause);
    }

    /**
     * 构建异常消息
     * 将ErrorCode的默认消息与detail信息拼接
     *
     * @param errorCode 错误码枚举
     * @param detail    详细错误信息
     * @return 拼接后的完整异常消息
     */
    private static String buildMessage(ErrorCode errorCode, String detail) {
        if (detail == null || detail.isEmpty()) {
            return errorCode.getMessage();
        }
        return errorCode.getMessage() + ": " + detail;
    }

    /**
     * 获取错误码数值
     * 便捷方法，直接从errorCode中获取code值
     *
     * @return 错误码数值
     */
    public int getCode() {
        return errorCode.getCode();
    }
}
