package com.ailearn.controller; // 声明包名

import com.ailearn.common.BusinessException; // 业务异常类
import com.ailearn.common.ErrorCode; // 错误码枚举
import com.ailearn.common.GlobalExceptionHandler; // 全局异常处理器
import com.ailearn.dto.LoginRequest; // 登录请求DTO
import com.ailearn.dto.RegisterRequest; // 注册请求DTO
import com.ailearn.security.UserPrincipal; // 用户主体类
import com.ailearn.service.UserService; // 用户服务
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson JSON序列化类
import org.junit.jupiter.api.BeforeEach; // JUnit前置方法注解
import org.junit.jupiter.api.DisplayName; // JUnit显示名称注解
import org.junit.jupiter.api.Test; // JUnit测试方法注解
import org.junit.jupiter.api.extension.ExtendWith; // JUnit扩展注解
import org.mockito.InjectMocks; // Mockito自动注入注解
import org.mockito.Mock; // Mockito创建Mock注解
import org.mockito.junit.jupiter.MockitoExtension; // Mockito JUnit 5扩展
import org.springframework.http.MediaType; // Spring MediaType常量
import org.springframework.test.web.servlet.MockMvc; // Spring MockMvc类
import org.springframework.test.web.servlet.setup.MockMvcBuilders; // Spring MockMvc构建器

import java.util.HashMap; // HashMap实现类
import java.util.Map; // Map接口

import static org.mockito.ArgumentMatchers.any; // Mockito参数匹配器
import static org.mockito.Mockito.when; // Mockito when方法
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // MockMvc GET请求构建器
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // MockMvc POST请求构建器
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*; // MockMvc结果匹配器

@ExtendWith(MockitoExtension.class) // 启用Mockito扩展
@DisplayName("用户控制器测试") // 测试类显示名称
class UserControllerTest { // 用户控制器测试类

    private MockMvc mockMvc; // MockMvc实例

    private final ObjectMapper objectMapper = new ObjectMapper(); // Jackson ObjectMapper

    @Mock // 创建UserService Mock对象
    private UserService userService; // Mock用户服务

    @InjectMocks // 自动注入Mock到UserController
    private UserController userController; // 被测用户控制器

    private static final String TEST_USERNAME = "testuser"; // 测试用户名
    private static final String TEST_PASSWORD = "password123"; // 测试密码
    private static final String TEST_NICKNAME = "测试用户"; // 测试昵称
    private static final Long TEST_USER_ID = 1L; // 测试用户ID
    private static final String TEST_ACCESS_TOKEN = "test-access-token"; // 测试Access Token
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token"; // 测试Refresh Token

    @BeforeEach // 每个测试前执行
    void setUp() { // 初始化方法
        mockMvc = MockMvcBuilders.standaloneSetup(userController) // 构建独立MockMvc
                .setControllerAdvice(new GlobalExceptionHandler()) // 注册全局异常处理器
                .build(); // 构建MockMvc
    } // setUp方法结束

    @Test
    @DisplayName("POST /api/auth/register - 注册成功")
    void testRegister_Success() throws Exception { // 测试注册成功
        RegisterRequest req = new RegisterRequest(); // 创建注册请求
        req.setUsername(TEST_USERNAME); // 设置用户名
        req.setPassword(TEST_PASSWORD); // 设置密码
        req.setNickname(TEST_NICKNAME); // 设置昵称

        UserPrincipal userPrincipal = UserPrincipal.create(TEST_USER_ID, TEST_USERNAME, "user"); // 创建用户主体
        Map<String, Object> serviceResult = new HashMap<>(); // 创建服务返回结果
        serviceResult.put("user", userPrincipal); // 放入用户信息
        serviceResult.put("accessToken", TEST_ACCESS_TOKEN); // 放入Access Token
        serviceResult.put("refreshToken", TEST_REFRESH_TOKEN); // 放入Refresh Token

        when(userService.register(any(RegisterRequest.class))).thenReturn(serviceResult); // Mock register方法返回成功

        mockMvc.perform(post("/api/auth/register") // 执行POST注册请求
                        .contentType(MediaType.APPLICATION_JSON) // 设置Content-Type
                        .content(objectMapper.writeValueAsString(req))) // 设置请求体JSON
                .andExpect(status().isOk()) // 期望HTTP 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // 期望JSON响应
                .andExpect(jsonPath("$.code").value(200)) // 期望业务code为200
                .andExpect(jsonPath("$.message").value("success")) // 期望message为success
                .andExpect(jsonPath("$.data.user.userId").value(TEST_USER_ID)) // 期望用户ID正确
                .andExpect(jsonPath("$.data.user.username").value(TEST_USERNAME)) // 期望用户名正确
                .andExpect(jsonPath("$.data.accessToken").value(TEST_ACCESS_TOKEN)) // 期望Access Token正确
                .andExpect(jsonPath("$.data.refreshToken").value(TEST_REFRESH_TOKEN)); // 期望Refresh Token正确
    } // testRegister_Success方法结束

    @Test
    @DisplayName("POST /api/auth/register - 用户名已存在返回错误")
    void testRegister_UsernameExists() throws Exception { // 测试注册用户名已存在
        RegisterRequest req = new RegisterRequest(); // 创建注册请求
        req.setUsername(TEST_USERNAME); // 设置用户名
        req.setPassword(TEST_PASSWORD); // 设置密码

        when(userService.register(any(RegisterRequest.class))) // Mock register抛出异常
                .thenThrow(new BusinessException(ErrorCode.USER_USERNAME_EXISTS));

        mockMvc.perform(post("/api/auth/register") // 执行POST请求
                        .contentType(MediaType.APPLICATION_JSON) // 设置Content-Type
                        .content(objectMapper.writeValueAsString(req))) // 设置请求体
                .andExpect(status().isOk()) // 期望HTTP 200（业务异常正常返回）
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_USERNAME_EXISTS.getCode())) // 期望错误码正确
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_USERNAME_EXISTS.getMessage())); // 期望错误消息正确
    } // testRegister_UsernameExists方法结束

    @Test
    @DisplayName("POST /api/auth/register - 参数校验失败返回400")
    void testRegister_ValidationError() throws Exception { // 测试注册参数校验失败
        RegisterRequest req = new RegisterRequest(); // 创建注册请求
        req.setUsername(""); // 用户名为空（无效）
        req.setPassword(TEST_PASSWORD); // 设置密码

        mockMvc.perform(post("/api/auth/register") // 执行POST请求
                        .contentType(MediaType.APPLICATION_JSON) // 设置Content-Type
                        .content(objectMapper.writeValueAsString(req))) // 设置请求体
                .andExpect(status().isBadRequest()); // 期望HTTP 400
    } // testRegister_ValidationError方法结束

    @Test
    @DisplayName("POST /api/auth/login - 登录成功")
    void testLogin_Success() throws Exception { // 测试登录成功
        LoginRequest req = new LoginRequest(); // 创建登录请求
        req.setUsername(TEST_USERNAME); // 设置用户名
        req.setPassword(TEST_PASSWORD); // 设置密码

        UserPrincipal userPrincipal = UserPrincipal.create(TEST_USER_ID, TEST_USERNAME, "user"); // 创建用户主体
        Map<String, Object> serviceResult = new HashMap<>(); // 创建服务返回结果
        serviceResult.put("user", userPrincipal); // 放入用户信息
        serviceResult.put("accessToken", TEST_ACCESS_TOKEN); // 放入Access Token
        serviceResult.put("refreshToken", TEST_REFRESH_TOKEN); // 放入Refresh Token

        when(userService.login(any(LoginRequest.class))).thenReturn(serviceResult); // Mock login返回成功

        mockMvc.perform(post("/api/auth/login") // 执行POST登录请求
                        .contentType(MediaType.APPLICATION_JSON) // 设置Content-Type
                        .content(objectMapper.writeValueAsString(req))) // 设置请求体
                .andExpect(status().isOk()) // 期望HTTP 200
                .andExpect(jsonPath("$.code").value(200)) // 期望业务code为200
                .andExpect(jsonPath("$.message").value("success")) // 期望message为success
                .andExpect(jsonPath("$.data.user.userId").value(TEST_USER_ID)) // 期望用户ID正确
                .andExpect(jsonPath("$.data.user.username").value(TEST_USERNAME)) // 期望用户名正确
                .andExpect(jsonPath("$.data.accessToken").value(TEST_ACCESS_TOKEN)) // 期望Access Token正确
                .andExpect(jsonPath("$.data.refreshToken").value(TEST_REFRESH_TOKEN)); // 期望Refresh Token正确
    } // testLogin_Success方法结束

    @Test
    @DisplayName("POST /api/auth/login - 密码错误返回错误")
    void testLogin_WrongPassword() throws Exception { // 测试登录密码错误
        LoginRequest req = new LoginRequest(); // 创建登录请求
        req.setUsername(TEST_USERNAME); // 设置用户名
        req.setPassword("wrongpassword"); // 错误密码

        when(userService.login(any(LoginRequest.class))) // Mock login抛出异常
                .thenThrow(new BusinessException(ErrorCode.AUTH_LOGIN_FAILED));

        mockMvc.perform(post("/api/auth/login") // 执行POST请求
                        .contentType(MediaType.APPLICATION_JSON) // 设置Content-Type
                        .content(objectMapper.writeValueAsString(req))) // 设置请求体
                .andExpect(status().isOk()) // 期望HTTP 200
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_LOGIN_FAILED.getCode())) // 期望错误码正确
                .andExpect(jsonPath("$.message").value(ErrorCode.AUTH_LOGIN_FAILED.getMessage())); // 期望错误消息正确
    } // testLogin_WrongPassword方法结束

    @Test
    @DisplayName("POST /api/auth/login - 用户不存在返回登录失败")
    void testLogin_UserNotFound() throws Exception { // 测试登录用户不存在
        LoginRequest req = new LoginRequest(); // 创建登录请求
        req.setUsername("nonexistent"); // 不存在的用户名
        req.setPassword(TEST_PASSWORD); // 设置密码

        when(userService.login(any(LoginRequest.class))) // Mock login抛出异常
                .thenThrow(new BusinessException(ErrorCode.AUTH_LOGIN_FAILED));

        mockMvc.perform(post("/api/auth/login") // 执行POST请求
                        .contentType(MediaType.APPLICATION_JSON) // 设置Content-Type
                        .content(objectMapper.writeValueAsString(req))) // 设置请求体
                .andExpect(status().isOk()) // 期望HTTP 200
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_LOGIN_FAILED.getCode())); // 期望错误码正确
    } // testLogin_UserNotFound方法结束

    @Test
    @DisplayName("GET /api/auth/me - 获取当前用户信息（standalone测试）")
    void testGetCurrentUser() throws Exception { // 测试获取当前用户信息
        mockMvc.perform(get("/api/auth/me") // 执行GET请求
                        .contentType(MediaType.APPLICATION_JSON)) // 设置Content-Type
                .andExpect(status().isOk()) // 期望HTTP 200
                .andExpect(jsonPath("$.code").value(200)) // 期望业务code为200
                .andExpect(jsonPath("$.data").isEmpty()); // 期望data为空（无认证信息）
    } // testGetCurrentUser方法结束

    @Test
    @DisplayName("POST /api/auth/login - 请求体为空返回400错误")
    void testLogin_EmptyBody() throws Exception { // 测试登录空请求体
        mockMvc.perform(post("/api/auth/login") // 执行POST请求
                        .contentType(MediaType.APPLICATION_JSON) // 设置Content-Type
                        .content("{}")) // 空JSON请求体
                .andExpect(status().isBadRequest()); // 期望HTTP 400
    } // testLogin_EmptyBody方法结束
} // UserControllerTest类结束
