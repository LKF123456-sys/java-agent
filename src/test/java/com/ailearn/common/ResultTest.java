package com.ailearn.common; // 声明包名

import org.junit.jupiter.api.DisplayName; // JUnit显示名称注解
import org.junit.jupiter.api.Test; // JUnit测试方法注解

import static org.junit.jupiter.api.Assertions.*; // JUnit断言静态导入

@DisplayName("统一响应Result测试") // 测试类显示名称
class ResultTest { // Result统一响应测试类

    @Test // 测试方法
    @DisplayName("success() - 无数据成功响应")
    void testSuccess_NoData() { // 测试无数据成功响应
        Result<Void> result = Result.success(); // 调用无参success方法

        assertEquals(200, result.getCode(), "成功响应状态码应为200"); // 状态码200
        assertEquals("success", result.getMessage(), "成功响应消息应为success"); // 消息为success
        assertNull(result.getData(), "无数据成功响应data应为null"); // data为null
        assertTrue(result.getTimestamp() > 0, "时间戳应为正数"); // 时间戳为正数
    } // testSuccess_NoData方法结束

    @Test
    @DisplayName("success(data) - 带数据成功响应")
    void testSuccess_WithData() { // 测试带数据成功响应
        String testData = "Hello, World!"; // 测试数据

        Result<String> result = Result.success(testData); // 调用带数据success

        assertEquals(200, result.getCode(), "成功响应状态码应为200"); // 状态码200
        assertEquals("success", result.getMessage(), "成功响应消息应为success"); // 消息为success
        assertEquals(testData, result.getData(), "响应数据应与传入数据一致"); // 数据正确
        assertTrue(result.getTimestamp() > 0, "时间戳应为正数"); // 时间戳为正数
    } // testSuccess_WithData方法结束

    @Test
    @DisplayName("success(data) - 携带对象数据")
    void testSuccess_WithObjectData() { // 测试携带对象数据
        TestData testData = new TestData(1L, "测试对象"); // 构造测试对象

        Result<TestData> result = Result.success(testData); // 调用success

        assertEquals(200, result.getCode()); // 状态码200
        assertNotNull(result.getData(), "数据不应为空"); // 数据不为空
        assertEquals(1L, result.getData().id(), "对象ID应正确"); // ID正确
        assertEquals("测试对象", result.getData().name(), "对象名称应正确"); // 名称正确
    } // testSuccess_WithObjectData方法结束

    @Test
    @DisplayName("error(message) - 仅消息错误响应")
    void testError_WithMessageOnly() { // 测试仅消息错误响应
        String errorMsg = "系统错误"; // 错误消息

        Result<Void> result = Result.error(errorMsg); // 调用error

        assertEquals(500, result.getCode(), "默认错误码应为500"); // 默认错误码500
        assertEquals(errorMsg, result.getMessage(), "错误消息应正确"); // 消息正确
        assertNull(result.getData(), "错误响应data应为null"); // data为null
    } // testError_WithMessageOnly方法结束

    @Test
    @DisplayName("error(code, message) - 带错误码和消息")
    void testError_WithCodeAndMessage() { // 测试带错误码和消息
        int errorCode = 400; // 错误码
        String errorMsg = "参数错误"; // 错误消息

        Result<Void> result = Result.error(errorCode, errorMsg); // 调用error

        assertEquals(errorCode, result.getCode(), "错误码应正确"); // 错误码正确
        assertEquals(errorMsg, result.getMessage(), "错误消息应正确"); // 消息正确
        assertNull(result.getData(), "错误响应data应为null"); // data为null
        assertTrue(result.getTimestamp() > 0, "时间戳应为正数"); // 时间戳为正数
    } // testError_WithCodeAndMessage方法结束

    @Test
    @DisplayName("error(ErrorCode) - 使用错误码枚举")
    void testError_WithErrorCode() { // 测试使用错误码枚举
        ErrorCode errorCode = ErrorCode.AUTH_LOGIN_FAILED; // 登录失败错误码

        Result<Void> result = Result.error(errorCode); // 调用error

        assertEquals(errorCode.getCode(), result.getCode(), "错误码应与枚举code一致"); // 错误码一致
        assertEquals(errorCode.getMessage(), result.getMessage(), "错误消息应与枚举message一致"); // 消息一致
        assertNull(result.getData(), "错误响应data应为null"); // data为null
    } // testError_WithErrorCode方法结束

    @Test
    @DisplayName("error(ErrorCode, detail) - 带详细错误信息")
    void testError_WithErrorCodeAndDetail() { // 测试带详细信息的错误
        ErrorCode errorCode = ErrorCode.USER_NOT_FOUND; // 用户不存在错误码
        String detail = "用户ID: 123"; // 详细信息

        Result<Void> result = Result.error(errorCode, detail); // 调用error

        assertEquals(errorCode.getCode(), result.getCode()); // 错误码正确
        assertTrue(result.getMessage().contains(errorCode.getMessage()), "消息应包含枚举默认消息"); // 包含枚举消息
        assertTrue(result.getMessage().contains(detail), "消息应包含详细信息"); // 包含详细信息
    } // testError_WithErrorCodeAndDetail方法结束

    @Test
    @DisplayName("error(ErrorCode, null) - detail为null时不追加")
    void testError_WithErrorCodeAndNullDetail() { // 测试detail为null
        ErrorCode errorCode = ErrorCode.SYSTEM_INTERNAL_ERROR; // 系统内部错误

        Result<Void> result = Result.error(errorCode, null); // 调用error，detail为null

        assertEquals(errorCode.getMessage(), result.getMessage(), "detail为null时消息应仅为枚举消息"); // 消息仅为枚举消息
    } // testError_WithErrorCodeAndNullDetail方法结束

    @Test
    @DisplayName("ErrorCode枚举值验证 - 关键错误码正确性")
    void testErrorCodeValues() { // 测试关键错误码值
        assertEquals(1001, ErrorCode.AUTH_LOGIN_FAILED.getCode(), "登录失败错误码应为1001"); // 登录失败1001
        assertEquals(1005, ErrorCode.AUTH_ACCESS_DENIED.getCode(), "权限不足错误码应为1005"); // 权限不足1005
        assertEquals(2001, ErrorCode.USER_NOT_FOUND.getCode(), "用户不存在错误码应为2001"); // 用户不存在2001
        assertEquals(2002, ErrorCode.USER_USERNAME_EXISTS.getCode(), "用户名已存在错误码应为2002"); // 用户名存在2002
        assertEquals(3001, ErrorCode.CHAT_CONVERSATION_NOT_FOUND.getCode(), "会话不存在错误码应为3001"); // 会话不存在3001
        assertEquals(9001, ErrorCode.SYSTEM_INTERNAL_ERROR.getCode(), "系统内部错误码应为9001"); // 系统错误9001
        assertEquals(9004, ErrorCode.SYSTEM_PARAM_VALIDATION_ERROR.getCode(), "参数校验失败错误码应为9004"); // 参数错误9004
    } // testErrorCodeValues方法结束

    @Test
    @DisplayName("Result.builder() - 使用建造者模式构建")
    void testResultBuilder() { // 测试建造者模式
        Result<String> result = Result.<String>builder() // 创建Builder
                .code(201) // 设置状态码201
                .message("created") // 设置消息
                .data("new resource") // 设置数据
                .timestamp(System.currentTimeMillis()) // 设置时间戳
                .build(); // 构建Result

        assertEquals(201, result.getCode()); // 状态码正确
        assertEquals("created", result.getMessage()); // 消息正确
        assertEquals("new resource", result.getData()); // 数据正确
    } // testResultBuilder方法结束

    private record TestData(Long id, String name) {} // 测试数据记录类
} // ResultTest类结束
