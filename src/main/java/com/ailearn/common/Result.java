package com.ailearn.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一API响应包装类
 * 用于封装所有REST API的返回结果，提供统一的响应格式
 *
 * @param <T> 响应数据的泛型类型
 * @author AiLearn Platform
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应状态码
     * 200表示成功，其他表示失败
     */
    private int code;

    /**
     * 响应消息
     * 成功时为"success"，失败时为错误描述信息
     */
    private String message;

    /**
     * 响应数据
     * 泛型类型，用于携带具体的业务数据
     */
    private T data;

    /**
     * 响应时间戳
     * 记录响应生成的时间（毫秒级时间戳）
     */
    private long timestamp;

    /**
     * 链路追踪ID
     * 用于分布式追踪，从MDC中获取，方便日志排查问题
     */
    private String traceId;

    /**
     * 创建成功响应（无数据）
     * 用于不需要返回业务数据的接口，如删除、更新等操作
     *
     * @param <T> 响应数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 创建成功响应（带数据）
     * 用于需要返回业务数据的接口，如查询、新增等操作
     *
     * @param data 业务数据
     * @param <T>  响应数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .traceId(MDC.get("traceId"))
                .build();
    }

    /**
     * 创建错误响应（仅消息）
     * 使用默认500错误码，用于通用错误场景
     *
     * @param message 错误消息
     * @param <T>     响应数据类型
     * @return 错误的Result对象
     */
    public static <T> Result<T> error(String message) {
        return error(500, message);
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
    public static <T> Result<T> error(int code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .traceId(MDC.get("traceId"))
                .build();
    }

    /**
     * 根据ErrorCode枚举创建错误响应
     * 用于使用预定义错误码的业务异常场景
     *
     * @param errorCode 错误码枚举
     * @param <T>       响应数据类型
     * @return 错误的Result对象
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());
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
    public static <T> Result<T> error(ErrorCode errorCode, String detail) {
        String message = errorCode.getMessage();
        if (detail != null && !detail.isEmpty()) {
            message = message + ": " + detail;
        }
        return error(errorCode.getCode(), message);
    }
}
