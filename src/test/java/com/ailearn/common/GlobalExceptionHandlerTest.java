package com.ailearn.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 全局异常处理器单元测试
 * 使用独立MockMvc测试GlobalExceptionHandler对各种异常的处理
 * 创建一个测试Controller来抛出不同类型的异常进行验证
 *
 * @author AiLearn Platform
 */
@DisplayName("全局异常处理器测试")
class GlobalExceptionHandlerTest {

    /**
     * MockMvc实例，用于模拟HTTP请求
     */
    private MockMvc mockMvc;

    /**
     * Jackson ObjectMapper，用于JSON解析
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 测试用Controller，用于抛出各种异常
     */
    @RestController
    static class TestController {

        /**
         * 抛出BusinessException的接口
         */
        @GetMapping("/test/business-error")
        public void throwBusinessError() {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }

        /**
         * 抛出IllegalArgumentException的接口
         */
        @GetMapping("/test/illegal-arg")
        public void throwIllegalArg(@RequestParam String value) {
            throw new IllegalArgumentException("参数非法: " + value);
        }

        /**
         * 抛出RuntimeException的接口
         */
        @GetMapping("/test/runtime-error")
        public void throwRuntimeError() {
            throw new RuntimeException("系统运行时错误");
        }

        /**
         * 正常返回的接口
         */
        @GetMapping("/test/ok")
        public Result<String> ok() {
            return Result.success("ok");
        }
    }

    /**
     * 每个测试方法执行前初始化MockMvc
     * 注册GlobalExceptionHandler和测试Controller
     */
    @BeforeEach
    void setUp() {
        // 构建独立MockMvc，注册异常处理器和测试Controller
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * 测试BusinessException异常处理
     * 验证：返回HTTP 200，body包含正确的错误码和消息
     */
    @Test
    @DisplayName("处理BusinessException - 业务异常返回正确错误码")
    void testHandleBusinessException() throws Exception {
        // 执行：请求抛出BusinessException的接口
        mockMvc.perform(get("/test/business-error")
                        .contentType(MediaType.APPLICATION_JSON))
                // 验证：HTTP状态为200（业务异常正常返回）
                .andExpect(status().isOk())
                // 验证：返回JSON格式
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // 验证：错误码正确
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_ACCESS_DENIED.getCode()))
                // 验证：错误消息正确
                .andExpect(jsonPath("$.message").value(ErrorCode.AUTH_ACCESS_DENIED.getMessage()));
    }

    /**
     * 测试IllegalArgumentException异常处理
     * 验证：返回HTTP 400，包含错误消息
     */
    @Test
    @DisplayName("处理IllegalArgumentException - 非法参数返回400")
    void testHandleIllegalArgumentException() throws Exception {
        // 执行：请求抛出IllegalArgumentException的接口
        mockMvc.perform(get("/test/illegal-arg")
                        .param("value", "test")
                        .contentType(MediaType.APPLICATION_JSON))
                // 验证：HTTP状态为400
                .andExpect(status().isBadRequest())
                // 验证：返回JSON格式
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // 验证：错误码为参数校验失败
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_PARAM_VALIDATION_ERROR.getCode()))
                // 验证：错误消息包含异常消息
                .andExpect(jsonPath("$.message").value("参数非法: test"));
    }

    /**
     * 测试通用Exception异常处理
     * 验证：返回HTTP 500，包含系统内部错误
     */
    @Test
    @DisplayName("处理Exception - 未知异常返回500系统错误")
    void testHandleGenericException() throws Exception {
        // 执行：请求抛出RuntimeException的接口
        mockMvc.perform(get("/test/runtime-error")
                        .contentType(MediaType.APPLICATION_JSON))
                // 验证：HTTP状态为500
                .andExpect(status().isInternalServerError())
                // 验证：返回JSON格式
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // 验证：错误码为系统内部错误
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_INTERNAL_ERROR.getCode()))
                // 验证：错误消息为系统内部错误
                .andExpect(jsonPath("$.message").value(ErrorCode.SYSTEM_INTERNAL_ERROR.getMessage()));
    }

    /**
     * 测试正常请求不被异常处理器干扰
     * 验证：正常请求正常返回
     */
    @Test
    @DisplayName("正常请求 - 正常返回不被拦截")
    void testNormalRequest() throws Exception {
        // 执行：请求正常接口
        mockMvc.perform(get("/test/ok")
                        .contentType(MediaType.APPLICATION_JSON))
                // 验证：HTTP状态为200
                .andExpect(status().isOk())
                // 验证：返回code为200
                .andExpect(jsonPath("$.code").value(200))
                // 验证：返回数据为ok
                .andExpect(jsonPath("$.data").value("ok"))
                // 验证：消息为success
                .andExpect(jsonPath("$.message").value("success"));
    }

    /**
     * 测试BusinessException带详细信息
     * 验证：错误消息包含详细信息
     */
    @Test
    @DisplayName("处理BusinessException - 带详细信息的异常")
    void testHandleBusinessExceptionWithDetail() throws Exception {
        // 创建一个带detail的BusinessException（通过额外接口或直接测试handler方法）
        // 由于使用standaloneSetup，我们直接验证handler方法的返回
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BusinessException ex = new BusinessException(ErrorCode.USER_NOT_FOUND, "用户ID: 999");

        // 直接调用handler方法
        Result<Void> result = handler.handleBusinessException(ex);

        // 验证：错误码正确
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), result.getCode());
        // 验证：消息包含详细信息
        assertTrue(result.getMessage().contains(ErrorCode.USER_NOT_FOUND.getMessage()));
        assertTrue(result.getMessage().contains("用户ID: 999"));
    }
}
