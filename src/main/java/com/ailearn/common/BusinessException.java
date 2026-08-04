package com.ailearn.common; // 声明包名，common包存放通用基础类

import lombok.Getter; // Lombok注解，自动生成所有getter方法

/**
 * 业务异常类
 * 用于封装业务逻辑中出现的可预期异常，继承自RuntimeException，
 * 包含错误码枚举和可选的详细错误信息，由GlobalExceptionHandler统一捕获处理
 *
 * @author AiLearn Platform
 */
@Getter // Lombok注解，自动为errorCode和detail字段生成getter
public class BusinessException extends RuntimeException { // 继承运行时异常（非受检，无需在方法签名声明）

    private static final long serialVersionUID = 1L; // 序列化版本号（Serializable规范要求）

    /**
     * 错误码枚举
     * 包含错误码和默认错误消息
     */
    private final ErrorCode errorCode; // 错误码枚举，标识异常类型

    /**
     * 详细错误信息
     * 可选字段，用于补充错误的具体上下文信息
     */
    private final String detail; // 详细错误描述，可选

    /**
     * 构造方法（仅错误码）
     * 使用ErrorCode中定义的默认消息作为异常消息
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) { // 单参数构造
        this(errorCode, null, null); // 委托给全参构造
    }

    /**
     * 构造方法（错误码 + 详细信息）
     * 使用自定义详细信息拼接默认消息作为异常消息
     *
     * @param errorCode 错误码枚举
     * @param detail    详细错误信息
     */
    public BusinessException(ErrorCode errorCode, String detail) { // 错误码+详情构造
        this(errorCode, detail, null); // 委托给全参构造
    }

    /**
     * 构造方法（错误码 + 原因）
     * 用于包装其他异常作为业务异常的场景
     *
     * @param errorCode 错误码枚举
     * @param cause     原始异常原因
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) { // 错误码+原因构造
        this(errorCode, null, cause); // 委托给全参构造
    }

    /**
     * 构造方法（错误码 + 详细信息 + 原因）
     * 完整构造方法，支持所有参数组合
     *
     * @param errorCode 错误码枚举
     * @param detail    详细错误信息
     * @param cause     原始异常原因
     */
    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) { // 全参构造
        super(buildMessage(errorCode, detail), cause); // 调用父类构造，传入拼接后的消息和原始原因
        this.errorCode = errorCode; // 赋值错误码
        this.detail = detail; // 赋值详情
    }

    /**
     * 静态工厂方法（仅错误码）
     * 提供更简洁的异常创建方式
     *
     * @param errorCode 错误码枚举
     * @return BusinessException实例
     */
    public static BusinessException of(ErrorCode errorCode) { // 静态工厂，更简洁
        return new BusinessException(errorCode); // 委托单参构造
    }

    /**
     * 静态工厂方法（错误码 + 详细信息）
     * 提供更简洁的异常创建方式
     *
     * @param errorCode 错误码枚举
     * @param detail    详细错误信息
     * @return BusinessException实例
     */
    public static BusinessException of(ErrorCode errorCode, String detail) { // 静态工厂（带详情）
        return new BusinessException(errorCode, detail); // 委托构造
    }

    /**
     * 静态工厂方法（错误码 + 原因）
     * 提供更简洁的异常创建方式
     *
     * @param errorCode 错误码枚举
     * @param cause     原始异常原因
     * @return BusinessException实例
     */
    public static BusinessException of(ErrorCode errorCode, Throwable cause) { // 静态工厂（带原因）
        return new BusinessException(errorCode, cause); // 委托构造
    }

    /**
     * 构建异常消息
     * 将ErrorCode的默认消息与detail信息拼接
     *
     * @param errorCode 错误码枚举
     * @param detail    详细错误信息
     * @return 拼接后的完整异常消息
     */
    private static String buildMessage(ErrorCode errorCode, String detail) { // 私有消息拼接方法
        if (detail == null || detail.isEmpty()) { // 无详情
            return errorCode.getMessage(); // 只用错误码默认消息
        }
        return errorCode.getMessage() + ": " + detail; // 默认消息 + 冒号 + 详情
    }

    /**
     * 获取错误码数值
     * 便捷方法，直接从errorCode中获取code值
     *
     * @return 错误码数值
     */
    public int getCode() { // 便捷getter
        return errorCode.getCode(); // 委托给错误码枚举
    }
} // BusinessException类结束
