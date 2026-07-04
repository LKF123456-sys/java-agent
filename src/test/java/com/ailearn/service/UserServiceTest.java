package com.ailearn.service;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.dto.LoginRequest;
import com.ailearn.dto.RegisterRequest;
import com.ailearn.entity.User;
import com.ailearn.mapper.UserMapper;
import com.ailearn.security.JwtUtil;
import com.ailearn.security.UserPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

/**
 * UserService单元测试类
 * 使用Mockito框架mock依赖（UserMapper、PasswordEncoder、JwtUtil）
 * 独立运行，不依赖Spring容器和外部数据库
 *
 * @author AiLearn Platform
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务测试")
class UserServiceTest {

    /**
     * Mock用户数据访问接口
     */
    @Mock
    private UserMapper userMapper;

    /**
     * Mock密码编码器（使用真实BCrypt实例，因为其方法稳定可靠）
     */
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Mock JWT工具类
     */
    @Mock
    private JwtUtil jwtUtil;

    /**
     * 被测试的UserService实例，@InjectMocks会自动注入mock的依赖
     */
    @InjectMocks
    private UserService userService;

    /**
     * 测试用户数据
     */
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_NICKNAME = "测试用户";
    private static final String TEST_ROLE = "user";
    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_ACCESS_TOKEN = "test-access-token";
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token";

    /**
     * 每个测试方法执行前的初始化
     * 注意：由于@InjectMocks不会注入我们手动创建的PasswordEncoder，需要使用反射设置
     */
    @BeforeEach
    void setUp() {
        // 使用反射注入真实的PasswordEncoder（因为BCrypt的行为是确定的，无需mock）
        org.springframework.test.util.ReflectionTestUtils.setField(userService, "passwordEncoder", passwordEncoder);
    }

    /**
     * 测试用户注册成功场景
     * 验证：用户名不存在时注册成功、密码被加密、保存用户、返回Token
     */
    @Test
    @DisplayName("用户注册 - 成功场景")
    void testRegister_Success() {
        // 准备：构造注册请求
        RegisterRequest req = new RegisterRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);
        req.setNickname(TEST_NICKNAME);
        req.setRole(TEST_ROLE);

        // Mock：用户不存在（findByUsername返回null）
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        // Mock：insert操作后设置用户ID（模拟MyBatis-Plus自动回填ID）
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(TEST_USER_ID);
            return 1;
        }).when(userMapper).insert(any(User.class));
        // Mock：JWT Token生成
        when(jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE)).thenReturn(TEST_ACCESS_TOKEN);
        when(jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE)).thenReturn(TEST_REFRESH_TOKEN);

        // 执行：注册
        Map<String, Object> result = userService.register(req);

        // 验证：返回结果包含必要字段
        assertNotNull(result, "注册结果不应为空");
        assertTrue(result.containsKey("user"), "返回结果应包含user");
        assertTrue(result.containsKey("accessToken"), "返回结果应包含accessToken");
        assertTrue(result.containsKey("refreshToken"), "返回结果应包含refreshToken");

        // 验证：用户信息正确
        UserPrincipal userPrincipal = (UserPrincipal) result.get("user");
        assertEquals(TEST_USER_ID, userPrincipal.getUserId(), "用户ID应正确");
        assertEquals(TEST_USERNAME, userPrincipal.getUsername(), "用户名应正确");
        assertEquals(TEST_ROLE, userPrincipal.getRole(), "用户角色应正确");

        // 验证：Token正确
        assertEquals(TEST_ACCESS_TOKEN, result.get("accessToken"), "Access Token应正确");
        assertEquals(TEST_REFRESH_TOKEN, result.get("refreshToken"), "Refresh Token应正确");

        // 验证：密码被加密（捕获insert的User参数验证密码不是明文）
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertNotEquals(TEST_PASSWORD, savedUser.getPassword(), "保存的密码不应是明文");
        assertTrue(passwordEncoder.matches(TEST_PASSWORD, savedUser.getPassword()), "加密后的密码应能匹配原密码");

        // 验证：JWT生成方法被调用
        verify(jwtUtil).generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        verify(jwtUtil).generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
    }

    /**
     * 测试注册时用户名已存在场景
     * 验证：抛出USER_USERNAME_EXISTS异常
     */
    @Test
    @DisplayName("用户注册 - 用户名已存在应抛出异常")
    void testRegister_UsernameExists() {
        // 准备：构造注册请求
        RegisterRequest req = new RegisterRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);

        // Mock：用户名已存在
        User existingUser = new User();
        existingUser.setUsername(TEST_USERNAME);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

        // 执行&验证：应抛出BusinessException
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.register(req),
                "用户名已存在时应抛出BusinessException");

        // 验证：错误码正确
        assertEquals(ErrorCode.USER_USERNAME_EXISTS.getCode(), exception.getCode(),
                "错误码应为USER_USERNAME_EXISTS");

        // 验证：insert未被调用
        verify(userMapper, never()).insert(any(User.class));
    }

    /**
     * 测试注册时昵称为空的场景
     * 验证：昵称默认使用用户名
     */
    @Test
    @DisplayName("用户注册 - 昵称为空时默认使用用户名")
    void testRegister_NullNickname() {
        // 准备：注册请求不设置昵称
        RegisterRequest req = new RegisterRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);
        req.setNickname(null);

        // Mock依赖
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(TEST_USER_ID);
            return 1;
        }).when(userMapper).insert(any(User.class));
        when(jwtUtil.generateAccessToken(anyLong(), anyString(), anyString())).thenReturn(TEST_ACCESS_TOKEN);
        when(jwtUtil.generateRefreshToken(anyLong(), anyString(), anyString())).thenReturn(TEST_REFRESH_TOKEN);

        // 执行
        userService.register(req);

        // 验证：保存的用户昵称应等于用户名
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals(TEST_USERNAME, userCaptor.getValue().getNickname(),
                "昵称为空时应默认使用用户名");
    }

    /**
     * 测试用户登录成功场景
     * 验证：正确密码登录成功、返回Token
     */
    @Test
    @DisplayName("用户登录 - 成功场景")
    void testLogin_Success() {
        // 准备：构造登录请求
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);

        // Mock：查询到用户，密码已加密
        User user = new User();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        user.setRole(TEST_ROLE);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        // Mock：Token生成
        when(jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE)).thenReturn(TEST_ACCESS_TOKEN);
        when(jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE)).thenReturn(TEST_REFRESH_TOKEN);

        // 执行：登录
        Map<String, Object> result = userService.login(req);

        // 验证：返回结果正确
        assertNotNull(result);
        UserPrincipal userPrincipal = (UserPrincipal) result.get("user");
        assertEquals(TEST_USER_ID, userPrincipal.getUserId());
        assertEquals(TEST_USERNAME, userPrincipal.getUsername());
        assertEquals(TEST_ACCESS_TOKEN, result.get("accessToken"));
        assertEquals(TEST_REFRESH_TOKEN, result.get("refreshToken"));

        // 验证：Token生成被调用
        verify(jwtUtil).generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
    }

    /**
     * 测试登录时用户不存在场景
     * 验证：抛出AUTH_LOGIN_FAILED异常（不透露是用户名还是密码错误）
     */
    @Test
    @DisplayName("用户登录 - 用户不存在应抛出异常")
    void testLogin_UserNotFound() {
        // 准备
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);

        // Mock：用户不存在
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // 执行&验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.login(req),
                "用户不存在时应抛出异常");
        assertEquals(ErrorCode.AUTH_LOGIN_FAILED.getCode(), exception.getCode(),
                "错误码应为AUTH_LOGIN_FAILED");
    }

    /**
     * 测试登录时密码错误场景
     * 验证：抛出AUTH_LOGIN_FAILED异常
     */
    @Test
    @DisplayName("用户登录 - 密码错误应抛出异常")
    void testLogin_WrongPassword() {
        // 准备
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword("wrongpassword");

        // Mock：用户存在但密码不匹配
        User user = new User();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        user.setRole(TEST_ROLE);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        // 执行&验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.login(req),
                "密码错误时应抛出异常");
        assertEquals(ErrorCode.AUTH_LOGIN_FAILED.getCode(), exception.getCode());
    }

    /**
     * 测试刷新Token成功场景
     * 验证：有效的Refresh Token能刷新出新的Access Token
     */
    @Test
    @DisplayName("刷新Token - 成功场景")
    void testRefreshToken_Success() {
        // 准备：有效的Refresh Token
        String refreshToken = TEST_REFRESH_TOKEN;

        // Mock：Refresh Token验证通过，类型正确
        when(jwtUtil.validateRefreshToken(refreshToken)).thenReturn(true);
        when(jwtUtil.extractTokenType(refreshToken)).thenReturn(JwtUtil.TOKEN_TYPE_REFRESH);
        when(jwtUtil.extractUserId(refreshToken)).thenReturn(TEST_USER_ID);
        when(jwtUtil.extractUsername(refreshToken)).thenReturn(TEST_USERNAME);
        when(jwtUtil.extractRole(refreshToken)).thenReturn(TEST_ROLE);
        when(jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE)).thenReturn("new-access-token");

        // 执行
        String newAccessToken = userService.refreshToken(refreshToken);

        // 验证
        assertEquals("new-access-token", newAccessToken, "应返回新的Access Token");
        verify(jwtUtil).generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
    }

    /**
     * 测试刷新Token时Token无效场景
     * 验证：无效Token抛出AUTH_TOKEN_INVALID异常
     */
    @Test
    @DisplayName("刷新Token - 无效Token应抛出异常")
    void testRefreshToken_InvalidToken() {
        String invalidToken = "invalid-token";

        // Mock：Token验证失败
        when(jwtUtil.validateRefreshToken(invalidToken)).thenReturn(false);

        // 执行&验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.refreshToken(invalidToken));
        assertEquals(ErrorCode.AUTH_TOKEN_INVALID.getCode(), exception.getCode());
    }

    /**
     * 测试刷新Token时Token类型错误（使用Access Token刷新）
     * 验证：抛出AUTH_TOKEN_INVALID异常
     */
    @Test
    @DisplayName("刷新Token - Token类型错误（Access Token）应抛出异常")
    void testRefreshToken_WrongTokenType() {
        String accessToken = "access-token";

        // Mock：Token有效但类型是access而不是refresh
        when(jwtUtil.validateRefreshToken(accessToken)).thenReturn(true);
        when(jwtUtil.extractTokenType(accessToken)).thenReturn(JwtUtil.TOKEN_TYPE_ACCESS);

        // 执行&验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.refreshToken(accessToken));
        assertEquals(ErrorCode.AUTH_TOKEN_INVALID.getCode(), exception.getCode());
    }

    /**
     * 测试根据ID查询用户成功场景
     */
    @Test
    @DisplayName("根据ID查询用户 - 成功场景")
    void testGetUserById_Success() {
        // Mock：查询到用户
        User user = new User();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        when(userMapper.selectById(TEST_USER_ID)).thenReturn(user);

        // 执行
        User result = userService.getUserById(TEST_USER_ID);

        // 验证
        assertNotNull(result);
        assertEquals(TEST_USER_ID, result.getId());
        assertEquals(TEST_USERNAME, result.getUsername());
    }

    /**
     * 测试根据ID查询用户不存在场景
     * 验证：抛出USER_NOT_FOUND异常
     */
    @Test
    @DisplayName("根据ID查询用户 - 用户不存在应抛出异常")
    void testGetUserById_NotFound() {
        // Mock：用户不存在
        when(userMapper.selectById(TEST_USER_ID)).thenReturn(null);

        // 执行&验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.getUserById(TEST_USER_ID));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 测试更新昵称成功场景
     */
    @Test
    @DisplayName("更新用户昵称 - 成功场景")
    void testUpdateNickname_Success() {
        String newNickname = "新昵称";

        // Mock：查询到用户
        User user = new User();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setNickname(TEST_NICKNAME);
        when(userMapper.selectById(TEST_USER_ID)).thenReturn(user);

        // 执行
        User result = userService.updateNickname(TEST_USER_ID, newNickname);

        // 验证：昵称被更新
        assertEquals(newNickname, result.getNickname());
        verify(userMapper).updateById(user);
    }

    /**
     * 测试更新昵称时用户不存在场景
     */
    @Test
    @DisplayName("更新用户昵称 - 用户不存在应抛出异常")
    void testUpdateNickname_UserNotFound() {
        when(userMapper.selectById(TEST_USER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.updateNickname(TEST_USER_ID, "新昵称"));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), exception.getCode());
        verify(userMapper, never()).updateById(isA(User.class));
    }

    /**
     * 测试创建第一个用户为管理员场景
     * 验证：系统中第一个注册的用户角色为admin
     */
    @Test
    @DisplayName("创建用户 - 第一个用户应为管理员")
    void testCreateUser_FirstUserIsAdmin() {
        // Mock：当前用户数为0（第一个用户）
        when(userMapper.selectCount(null)).thenReturn(0L);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        // 执行
        User result = userService.createUser(TEST_USERNAME, TEST_NICKNAME, TEST_PASSWORD);

        // 验证：角色为admin
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("admin", userCaptor.getValue().getRole(), "第一个用户应为管理员");
    }

    /**
     * 测试创建非第一个用户为普通用户场景
     */
    @Test
    @DisplayName("创建用户 - 非第一个用户应为普通用户")
    void testCreateUser_NotFirstUserIsNormal() {
        // Mock：已有用户
        when(userMapper.selectCount(null)).thenReturn(5L);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(6L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        // 执行
        userService.createUser(TEST_USERNAME, TEST_NICKNAME, TEST_PASSWORD);

        // 验证：角色为user
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("user", userCaptor.getValue().getRole(), "非第一个用户应为普通用户");
    }

    /**
     * 测试findByUsername方法
     */
    @Test
    @DisplayName("根据用户名查询用户")
    void testFindByUsername() {
        // Mock
        User user = new User();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        // 执行
        User result = userService.findByUsername(TEST_USERNAME);

        // 验证
        assertNotNull(result);
        assertEquals(TEST_USERNAME, result.getUsername());
    }
}
