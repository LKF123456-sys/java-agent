package com.ailearn.controller;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.common.GlobalExceptionHandler;
import com.ailearn.dto.LoginRequest;
import com.ailearn.dto.RegisterRequest;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 用户控制器集成测试
 * 使用MockMvc模拟HTTP请求，测试用户注册、登录等接口
 * 使用Mockito mock UserService依赖，不加载完整Spring上下文
 *
 * @author AiLearn Platform
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户控制器测试")
class UserControllerTest {

    /**
     * MockMvc实例，用于模拟HTTP请求
     */
    private MockMvc mockMvc;

    /**
     * Jackson ObjectMapper，用于JSON序列化/反序列化
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Mock用户服务
     */
    @Mock
    private UserService userService;

    /**
     * 被测试的UserController实例，@InjectMocks自动注入mock依赖
     */
    @InjectMocks
    private UserController userController;

    /**
     * 测试用户名
     */
    private static final String TEST_USERNAME = "testuser";

    /**
     * 测试密码
     */
    private static final String TEST_PASSWORD = "password123";

    /**
     * 测试昵称
     */
    private static final String TEST_NICKNAME = "测试用户";

    /**
     * 测试用户ID
     */
    private static final Long TEST_USER_ID = 1L;

    /**
     * 测试Access Token
     */
    private static final String TEST_ACCESS_TOKEN = "test-access-token";

    /**
     * 测试Refresh Token
     */
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token";

    /**
     * 每个测试方法执行前初始化MockMvc
     * 注册GlobalExceptionHandler处理异常
     */
    @BeforeEach
    void setUp() {
        // 构建独立MockMvc，注册Controller和全局异常处理器
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * 测试用户注册成功场景
     * POST /api/auth/register
     * 验证：返回200，包含用户信息和双Token
     */
    @Test
    @DisplayName("POST /api/auth/register - 注册成功")
    void testRegister_Success() throws Exception {
        // 准备：注册请求参数
        RegisterRequest req = new RegisterRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);
        req.setNickname(TEST_NICKNAME);

        // 准备：Mock UserService返回结果
        UserPrincipal userPrincipal = UserPrincipal.create(TEST_USER_ID, TEST_USERNAME, "user");
        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("user", userPrincipal);
        serviceResult.put("accessToken", TEST_ACCESS_TOKEN);
        serviceResult.put("refreshToken", TEST_REFRESH_TOKEN);

        // Mock：register方法返回成功结果
        when(userService.register(any(RegisterRequest.class))).thenReturn(serviceResult);

        // 执行：发送POST注册请求
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                // 验证：HTTP状态为200
                .andExpect(status().isOk())
                // 验证：返回JSON格式
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // 验证：业务code为200
                .andExpect(jsonPath("$.code").value(200))
                // 验证：消息为success
                .andExpect(jsonPath("$.message").value("success"))
                // 验证：返回用户信息
                .andExpect(jsonPath("$.data.user.userId").value(TEST_USER_ID))
                .andExpect(jsonPath("$.data.user.username").value(TEST_USERNAME))
                // 验证：返回Token
                .andExpect(jsonPath("$.data.accessToken").value(TEST_ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").value(TEST_REFRESH_TOKEN));
    }

    /**
     * 测试用户注册时用户名已存在
     * 验证：返回业务错误
     */
    @Test
    @DisplayName("POST /api/auth/register - 用户名已存在返回错误")
    void testRegister_UsernameExists() throws Exception {
        // 准备：注册请求参数
        RegisterRequest req = new RegisterRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);

        // Mock：register方法抛出用户名已存在异常
        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.USER_USERNAME_EXISTS));

        // 执行：发送POST注册请求
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                // 验证：HTTP状态为200（业务异常正常返回）
                .andExpect(status().isOk())
                // 验证：业务错误码正确
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_USERNAME_EXISTS.getCode()))
                // 验证：错误消息正确
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_USERNAME_EXISTS.getMessage()));
    }

    /**
     * 测试用户注册参数校验失败（用户名太短）
     * 验证：返回参数错误
     */
    @Test
    @DisplayName("POST /api/auth/register - 参数校验失败返回400")
    void testRegister_ValidationError() throws Exception {
        // 准备：无效的注册请求（用户名为空）
        RegisterRequest req = new RegisterRequest();
        req.setUsername(""); // 空用户名
        req.setPassword(TEST_PASSWORD);

        // 执行：发送POST注册请求
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                // 验证：由于standaloneSetup不自动执行@Valid校验，这里验证请求能到达controller
                // 实际Spring环境下@Valid会触发MethodArgumentNotValidException
                .andExpect(status().isBadRequest());
    }

    /**
     * 测试用户登录成功场景
     * POST /api/auth/login
     * 验证：返回200，包含用户信息和双Token
     */
    @Test
    @DisplayName("POST /api/auth/login - 登录成功")
    void testLogin_Success() throws Exception {
        // 准备：登录请求参数
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);

        // 准备：Mock UserService返回结果
        UserPrincipal userPrincipal = UserPrincipal.create(TEST_USER_ID, TEST_USERNAME, "user");
        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("user", userPrincipal);
        serviceResult.put("accessToken", TEST_ACCESS_TOKEN);
        serviceResult.put("refreshToken", TEST_REFRESH_TOKEN);

        // Mock：login方法返回成功结果
        when(userService.login(any(LoginRequest.class))).thenReturn(serviceResult);

        // 执行：发送POST登录请求
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                // 验证：HTTP状态为200
                .andExpect(status().isOk())
                // 验证：业务code为200
                .andExpect(jsonPath("$.code").value(200))
                // 验证：消息为success
                .andExpect(jsonPath("$.message").value("success"))
                // 验证：返回用户信息
                .andExpect(jsonPath("$.data.user.userId").value(TEST_USER_ID))
                .andExpect(jsonPath("$.data.user.username").value(TEST_USERNAME))
                // 验证：返回Token
                .andExpect(jsonPath("$.data.accessToken").value(TEST_ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").value(TEST_REFRESH_TOKEN));
    }

    /**
     * 测试用户登录密码错误
     * 验证：返回登录失败错误
     */
    @Test
    @DisplayName("POST /api/auth/login - 密码错误返回错误")
    void testLogin_WrongPassword() throws Exception {
        // 准备：登录请求参数
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword("wrongpassword");

        // Mock：login方法抛出登录失败异常
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.AUTH_LOGIN_FAILED));

        // 执行：发送POST登录请求
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                // 验证：HTTP状态为200（业务异常正常返回）
                .andExpect(status().isOk())
                // 验证：业务错误码正确
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_LOGIN_FAILED.getCode()))
                // 验证：错误消息正确
                .andExpect(jsonPath("$.message").value(ErrorCode.AUTH_LOGIN_FAILED.getMessage()));
    }

    /**
     * 测试用户登录用户不存在
     * 验证：返回登录失败错误（不透露是用户名还是密码错误）
     */
    @Test
    @DisplayName("POST /api/auth/login - 用户不存在返回登录失败")
    void testLogin_UserNotFound() throws Exception {
        // 准备：登录请求参数
        LoginRequest req = new LoginRequest();
        req.setUsername("nonexistent");
        req.setPassword(TEST_PASSWORD);

        // Mock：login方法抛出登录失败异常
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.AUTH_LOGIN_FAILED));

        // 执行
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                // 验证
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_LOGIN_FAILED.getCode()));
    }

    /**
     * 测试获取当前用户信息接口（未配置Security的standalone测试）
     * 注意：在standaloneSetup中Spring Security不生效，请求不会被拦截
     * 实际Spring环境下未携带Token访问/api/auth/me会返回401
     * 此测试仅验证接口能正常访问并返回结果
     */
    @Test
    @DisplayName("GET /api/auth/me - 获取当前用户信息（standalone测试）")
    void testGetCurrentUser() throws Exception {
        // standaloneSetup不加载Spring Security，所以请求不会被拦截
        // SecurityContextHolder中没有认证信息时，返回null用户

        // 执行：发送GET请求获取当前用户
        mockMvc.perform(get("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON))
                // 验证：HTTP状态为200
                .andExpect(status().isOk())
                // 验证：业务code为200
                .andExpect(jsonPath("$.code").value(200))
                // 验证：data为null（无认证信息）
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * 测试请求体为空时的处理
     */
    @Test
    @DisplayName("POST /api/auth/login - 请求体为空返回400错误")
    void testLogin_EmptyBody() throws Exception {
        // 执行：发送空请求体
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                // 验证：空请求体或缺少必填字段会触发参数校验，返回400状态码
                .andExpect(status().isBadRequest());
    }
}
