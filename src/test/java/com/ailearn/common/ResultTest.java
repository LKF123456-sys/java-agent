package com.ailearn.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result统一响应类单元测试
 * 测试Result.success()、Result.success(data)、Result.error()等方法
 *
 * @author AiLearn Platform
 */
@DisplayName("统一响应Result测试")
class ResultTest {

    /**
     * 测试Result.success()无参数方法
     * 验证：返回code=200，message="success"，data=null
     */
    @Test
    @DisplayName("success() - 无数据成功响应")
    void testSuccess_NoData() {
        // 执行：调用无参success方法
        Result<Void> result = Result.success();

        // 验证：状态码为200
        assertEquals(200, result.getCode(), "成功响应状态码应为200");
        // 验证：消息为success
        assertEquals("success", result.getMessage(), "成功响应消息应为success");
        // 验证：数据为null
        assertNull(result.getData(), "无数据成功响应data应为null");
        // 验证：时间戳为正数
        assertTrue(result.getTimestamp() > 0, "时间戳应为正数");
    }

    /**
     * 测试Result.success(data)带数据方法
     * 验证：返回code=200，message="success"，data正确携带
     */
    @Test
    @DisplayName("success(data) - 带数据成功响应")
    void testSuccess_WithData() {
        // 准备：测试数据
        String testData = "Hello, World!";

        // 执行：调用带数据的success方法
        Result<String> result = Result.success(testData);

        // 验证：状态码为200
        assertEquals(200, result.getCode(), "成功响应状态码应为200");
        // 验证：消息为success
        assertEquals("success", result.getMessage(), "成功响应消息应为success");
        // 验证：数据正确携带
        assertEquals(testData, result.getData(), "响应数据应与传入数据一致");
        // 验证：时间戳为正数
        assertTrue(result.getTimestamp() > 0, "时间戳应为正数");
    }

    /**
     * 测试Result.success(data)携带复杂对象
     * 验证：能正确携带自定义对象数据
     */
    @Test
    @DisplayName("success(data) - 携带对象数据")
    void testSuccess_WithObjectData() {
        // 准备：测试对象
        TestData testData = new TestData(1L, "测试对象");

        // 执行
        Result<TestData> result = Result.success(testData);

        // 验证
        assertEquals(200, result.getCode());
        assertNotNull(result.getData(), "数据不应为空");
        assertEquals(1L, result.getData().id(), "对象ID应正确");
        assertEquals("测试对象", result.getData().name(), "对象名称应正确");
    }

    /**
     * 测试Result.error(message)仅消息方法
     * 验证：返回code=500，自定义错误消息
     */
    @Test
    @DisplayName("error(message) - 仅消息错误响应")
    void testError_WithMessageOnly() {
        // 准备：错误消息
        String errorMsg = "系统错误";

        // 执行
        Result<Void> result = Result.error(errorMsg);

        // 验证：状态码为500（默认错误码）
        assertEquals(500, result.getCode(), "默认错误码应为500");
        // 验证：消息正确
        assertEquals(errorMsg, result.getMessage(), "错误消息应正确");
        // 验证：数据为null
        assertNull(result.getData(), "错误响应data应为null");
    }

    /**
     * 测试Result.error(code, message)带错误码和消息方法
     * 验证：返回自定义错误码和消息
     */
    @Test
    @DisplayName("error(code, message) - 带错误码和消息")
    void testError_WithCodeAndMessage() {
        // 准备：自定义错误码和消息
        int errorCode = 400;
        String errorMsg = "参数错误";

        // 执行
        Result<Void> result = Result.error(errorCode, errorMsg);

        // 验证：错误码正确
        assertEquals(errorCode, result.getCode(), "错误码应正确");
        // 验证：消息正确
        assertEquals(errorMsg, result.getMessage(), "错误消息应正确");
        // 验证：数据为null
        assertNull(result.getData(), "错误响应data应为null");
        // 验证：时间戳为正数
        assertTrue(result.getTimestamp() > 0, "时间戳应为正数");
    }

    /**
     * 测试Result.error(ErrorCode)使用枚举方法
     * 验证：根据ErrorCode枚举生成正确的错误响应
     */
    @Test
    @DisplayName("error(ErrorCode) - 使用错误码枚举")
    void testError_WithErrorCode() {
        // 准备：使用预定义错误码
        ErrorCode errorCode = ErrorCode.AUTH_LOGIN_FAILED;

        // 执行
        Result<Void> result = Result.error(errorCode);

        // 验证：错误码与枚举一致
        assertEquals(errorCode.getCode(), result.getCode(), "错误码应与枚举code一致");
        // 验证：消息与枚举一致
        assertEquals(errorCode.getMessage(), result.getMessage(), "错误消息应与枚举message一致");
        // 验证：数据为null
        assertNull(result.getData(), "错误响应data应为null");
    }

    /**
     * 测试Result.error(ErrorCode, detail)带详细信息方法
     * 验证：错误消息包含枚举消息和详细信息
     */
    @Test
    @DisplayName("error(ErrorCode, detail) - 带详细错误信息")
    void testError_WithErrorCodeAndDetail() {
        // 准备
        ErrorCode errorCode = ErrorCode.USER_NOT_FOUND;
        String detail = "用户ID: 123";

        // 执行
        Result<Void> result = Result.error(errorCode, detail);

        // 验证：错误码正确
        assertEquals(errorCode.getCode(), result.getCode());
        // 验证：消息包含枚举消息和详细信息
        assertTrue(result.getMessage().contains(errorCode.getMessage()), "消息应包含枚举默认消息");
        assertTrue(result.getMessage().contains(detail), "消息应包含详细信息");
    }

    /**
     * 测试Result.error(ErrorCode, null)detail为null时
     * 验证：detail为null时不追加内容
     */
    @Test
    @DisplayName("error(ErrorCode, null) - detail为null时不追加")
    void testError_WithErrorCodeAndNullDetail() {
        // 准备
        ErrorCode errorCode = ErrorCode.SYSTEM_INTERNAL_ERROR;

        // 执行
        Result<Void> result = Result.error(errorCode, null);

        // 验证：消息仅为枚举消息
        assertEquals(errorCode.getMessage(), result.getMessage(), "detail为null时消息应仅为枚举消息");
    }

    /**
     * 测试常用业务错误码
     * 验证：几个关键错误码的code和message正确
     */
    @Test
    @DisplayName("ErrorCode枚举值验证 - 关键错误码正确性")
    void testErrorCodeValues() {
        // 验证认证相关错误码
        assertEquals(1001, ErrorCode.AUTH_LOGIN_FAILED.getCode(), "登录失败错误码应为1001");
        assertEquals(1005, ErrorCode.AUTH_ACCESS_DENIED.getCode(), "权限不足错误码应为1005");

        // 验证用户相关错误码
        assertEquals(2001, ErrorCode.USER_NOT_FOUND.getCode(), "用户不存在错误码应为2001");
        assertEquals(2002, ErrorCode.USER_USERNAME_EXISTS.getCode(), "用户名已存在错误码应为2002");

        // 验证聊天相关错误码
        assertEquals(3001, ErrorCode.CHAT_CONVERSATION_NOT_FOUND.getCode(), "会话不存在错误码应为3001");

        // 验证系统相关错误码
        assertEquals(9001, ErrorCode.SYSTEM_INTERNAL_ERROR.getCode(), "系统内部错误码应为9001");
        assertEquals(9004, ErrorCode.SYSTEM_PARAM_VALIDATION_ERROR.getCode(), "参数校验失败错误码应为9004");
    }

    /**
     * 测试结果对象的builder模式
     * 验证：使用builder构建Result对象正确
     */
    @Test
    @DisplayName("Result.builder() - 使用建造者模式构建")
    void testResultBuilder() {
        // 执行：使用builder构建
        Result<String> result = Result.<String>builder()
                .code(201)
                .message("created")
                .data("new resource")
                .timestamp(System.currentTimeMillis())
                .build();

        // 验证
        assertEquals(201, result.getCode());
        assertEquals("created", result.getMessage());
        assertEquals("new resource", result.getData());
    }

    /**
     * 简单测试数据记录类
     */
    private record TestData(Long id, String name) {}
}
