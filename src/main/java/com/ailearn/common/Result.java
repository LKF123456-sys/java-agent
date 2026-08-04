package com.ailearn.common; // 声明包名

import lombok.AllArgsConstructor; // Lombok注解，生成全参构造器
import lombok.Builder; // Lombok注解，生成建造者模式API
import lombok.Data; // Lombok注解，生成getter/setter/toString/equals/hashCode
import lombok.NoArgsConstructor; // Lombok注解，生成无参构造器
import org.slf4j.MDC; // SLF4J的MDC（映射诊断上下文），用于日志链路追踪

import java.io.Serializable; // 序列化接口

/**
 * 统一API响应包装类
 * 用于封装所有REST API的返回结果，提供统一的响应格式
 *
 * @param <T> 响应数据的泛型类型
 * @author AiLearn Platform
 */
@Data // 自动生成getter/setter等
@Builder // 启用建造者模式（Result.<T>builder().code(..).build()）
@NoArgsConstructor // 生成无参构造器
@AllArgsConstructor // 生成全参构造器
public class Result<T> implements Serializable { // 泛型类，实现Serializable支持序列化

    private static final long serialVersionUID = 1L; // 序列化版本号

    /**
     * 响应状态码
     * 200表示成功，其他表示失败
     */
    private int code; // 业务状态码

    /**
     * 响应消息
     * 成功时为"success"，失败时为错误描述信息
     */
    private String message; // 响应消息文本

    /**
     * 响应数据
     * 泛型类型，用于携带具体的业务数据
     */
    private T data; // 泛型业务数据

    /**
     * 响应时间戳
     * 记录响应生成的时间（毫秒级时间戳）
     */
    private long timestamp; // 毫秒时间戳

    /**
     * 链路追踪ID
     * 用于分布式追踪，从MDC中获取，方便日志排查问题
     */
    private String traceId; // 从MDC取的traceId

    /**
     * 创建成功响应（无数据）
     * 用于不需要返回业务数据的接口，如删除、更新等操作
     *
     * @param <T> 响应数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success() { // 无数据成功响应
        return success(null); // 委托给带数据的success方法，data传null
    }

    /**
     * 创建成功响应（带数据）
     * 用于需要返回业务数据的接口，如查询、新增等操作
     *
     * @param data 业务数据
     * @param <T>  响应数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success(T data) { // 带数据的成功响应
        return Result.<T>builder() // 启动建造者
                .code(200) // 状态码200
                .message("success") // 消息success
                .data(data) // 业务数据
                .timestamp(System.currentTimeMillis()) // 当前时间戳
                .traceId(MDC.get("traceId")) // 从MDC取出当前请求的traceId
                .build(); // 构建Result对象
    }

    /**
     * 创建错误响应（仅消息）
     * 使用默认500错误码，用于通用错误场景
     *
     * @param message 错误消息
     * @param <T>     响应数据类型
     * @return 错误的Result对象
     */
    public static <T> Result<T> error(String message) { // 仅消息的错误响应
        return error(500, message); // 委托给带错误码的error方法，默认500
    }

    /**
     * 创建错误响应（带错误码和消息）
     * 用于自定义错误码的业务错误场景
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     响应数据类型
     * @return 错误的Result对象
     */
    public static <T> Result<T> error(int code, String message) { // 带码和消息的错误响应
        return Result.<T>builder() // 启动建造者
                .code(code) // 错误码
                .message(message) // 错误消息
                .data(null) // 错误响应无业务数据
                .timestamp(System.currentTimeMillis()) // 时间戳
                .traceId(MDC.get("traceId")) // traceId
                .build(); // 构建
    }

    /**
     * 根据ErrorCode枚举创建错误响应
     * 用于使用预定义错误码的业务异常场景
     *
     * @param errorCode 错误码枚举
     * @param <T>       响应数据类型
     * @return 错误的Result对象
     */
    public static <T> Result<T> error(ErrorCode errorCode) { // 由枚举构造错误响应
        return error(errorCode.getCode(), errorCode.getMessage()); // 取枚举的码和消息
    }

    /**
     * 根据ErrorCode枚举创建错误响应（附带自定义消息）
     * 用于使用预定义错误码但需要追加详细信息的场景
     *
     * @param errorCode 错误码枚举
     * @param detail    自定义详细错误信息
     * @param <T>       响应数据类型
     * @return 错误的Result对象
     */
    public static <T> Result<T> error(ErrorCode errorCode, String detail) { // 枚举+详情
        String message = errorCode.getMessage(); // 取默认消息
        if (detail != null && !detail.isEmpty()) { // 有详情
            message = message + ": " + detail; // 拼接详情
        }
        return error(errorCode.getCode(), message); // 委托构造
    }
} // Result类结束
