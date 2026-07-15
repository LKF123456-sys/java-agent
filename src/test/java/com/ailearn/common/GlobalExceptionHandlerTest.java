package com.ailearn.common; // 声明包名

import com.fasterxml.jackson.databind.ObjectMapper; // Jackson JSON序列化/反序列化类
import org.junit.jupiter.api.BeforeEach; // JUnit前置方法注解
import org.junit.jupiter.api.DisplayName; // JUnit显示名称注解
import org.junit.jupiter.api.Test; // JUnit测试方法注解
import org.springframework.http.MediaType; // Spring MediaType常量类
import org.springframework.test.web.servlet.MockMvc; // Spring MockMvc类，模拟HTTP请求
import org.springframework.test.web.servlet.setup.MockMvcBuilders; // Spring MockMvc构建器
import org.springframework.web.bind.annotation.GetMapping; // Spring GET映射注解
import org.springframework.web.bind.annotation.RequestParam; // Spring请求参数注解
import org.springframework.web.bind.annotation.RestController; // Spring REST控制器注解

import static org.junit.jupiter.api.Assertions.*; // JUnit断言静态导入
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // MockMvc GET请求构建器
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*; // MockMvc结果匹配器

@DisplayName("全局异常处理器测试") // 测试类显示名称
class GlobalExceptionHandlerTest { // 全局异常处理器测试类

    private MockMvc mockMvc; // MockMvc实例

    private final ObjectMapper objectMapper = new ObjectMapper(); // Jackson ObjectMapper

    @RestController // 测试用Controller，用于抛出各种异常
    static class TestController { // 测试控制器静态内部类

        @GetMapping("/test/business-error") // 测试业务异常接口
        public void throwBusinessError() { // 抛出BusinessException的方法
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED); // 抛出权限不足异常
        } // throwBusinessError方法结束

        @GetMapping("/test/illegal-arg") // 测试非法参数异常接口
        public void throwIllegalArg(@RequestParam String value) { // 抛出IllegalArgumentException的方法
            throw new IllegalArgumentException("参数非法: " + value); // 抛出非法参数异常
        } // throwIllegalArg方法结束

        @GetMapping("/test/runtime-error") // 测试运行时异常接口
        public void throwRuntimeError() { // 抛出RuntimeException的方法
            throw new RuntimeException("系统运行时错误"); // 抛出运行时异常
        } // throwRuntimeError方法结束

        @GetMapping("/test/ok") // 测试正常接口
        public Result<String> ok() { // 正常返回方法
            return Result.success("ok"); // 返回成功
        } // ok方法结束
    } // TestController类结束

    @BeforeEach // 每个测试前执行
    void setUp() { // 初始化方法
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController()) // 构建独立MockMvc
                .setControllerAdvice(new GlobalExceptionHandler()) // 注册全局异常处理器
                .build(); // 构建MockMvc
    } // setUp方法结束

    @Test
    @DisplayName("处理BusinessException - 业务异常返回正确错误码")
    void testHandleBusinessException() throws Exception { // 测试业务异常处理
        mockMvc.perform(get("/test/business-error") // 执行GET请求
                        .contentType(MediaType.APPLICATION_JSON)) // 设置Content-Type
                .andExpect(status().isOk()) // 期望HTTP 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // 期望JSON响应
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_ACCESS_DENIED.getCode())) // 期望错误码正确
                .andExpect(jsonPath("$.message").value(ErrorCode.AUTH_ACCESS_DENIED.getMessage())); // 期望错误消息正确
    } // testHandleBusinessException方法结束

    @Test
    @DisplayName("处理IllegalArgumentException - 非法参数返回400")
    void testHandleIllegalArgumentException() throws Exception { // 测试非法参数异常处理
        mockMvc.perform(get("/test/illegal-arg") // 执行GET请求
                        .param("value", "test") // 设置请求参数
                        .contentType(MediaType.APPLICATION_JSON)) // 设置Content-Type
                .andExpect(status().isBadRequest()) // 期望HTTP 400
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // 期望JSON响应
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_PARAM_VALIDATION_ERROR.getCode())) // 期望参数校验错误码
                .andExpect(jsonPath("$.message").value("参数非法: test")); // 期望错误消息包含具体信息
    } // testHandleIllegalArgumentException方法结束

    @Test
    @DisplayName("处理Exception - 未知异常返回500系统错误")
    void testHandleGenericException() throws Exception { // 测试通用异常处理
        mockMvc.perform(get("/test/runtime-error") // 执行GET请求
                        .contentType(MediaType.APPLICATION_JSON)) // 设置Content-Type
                .andExpect(status().isInternalServerError()) // 期望HTTP 500
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // 期望JSON响应
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_INTERNAL_ERROR.getCode())) // 期望系统内部错误码
                .andExpect(jsonPath("$.message").value(ErrorCode.SYSTEM_INTERNAL_ERROR.getMessage())); // 期望系统内部错误消息
    } // testHandleGenericException方法结束

    @Test
    @DisplayName("正常请求 - 正常返回不被拦截")
    void testNormalRequest() throws Exception { // 测试正常请求
        mockMvc.perform(get("/test/ok") // 执行GET请求
                        .contentType(MediaType.APPLICATION_JSON)) // 设置Content-Type
                .andExpect(status().isOk()) // 期望HTTP 200
                .andExpect(jsonPath("$.code").value(200)) // 期望code为200
                .andExpect(jsonPath("$.data").value("ok")) // 期望data为ok
                .andExpect(jsonPath("$.message").value("success")); // 期望message为success
    } // testNormalRequest方法结束

    @Test
    @DisplayName("处理BusinessException - 带详细信息的异常")
    void testHandleBusinessExceptionWithDetail() throws Exception { // 测试带详细信息的业务异常
        GlobalExceptionHandler handler = new GlobalExceptionHandler(); // 创建异常处理器
        BusinessException ex = new BusinessException(ErrorCode.USER_NOT_FOUND, "用户ID: 999"); // 创建带详细信息的异常

        Result<Void> result = handler.handleBusinessException(ex); // 直接调用handler方法

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), result.getCode()); // 错误码正确
        assertTrue(result.getMessage().contains(ErrorCode.USER_NOT_FOUND.getMessage())); // 包含枚举消息
        assertTrue(result.getMessage().contains("用户ID: 999")); // 包含详细信息
    } // testHandleBusinessExceptionWithDetail方法结束
} // GlobalExceptionHandlerTest类结束
