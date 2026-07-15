package com.ailearn.service; // 声明包名，service包存放业务逻辑服务类

import com.ailearn.common.BusinessException; // 业务异常类，用于封装业务逻辑错误
import com.ailearn.common.ErrorCode; // 错误码枚举类，定义系统所有错误码和错误消息
import com.ailearn.dto.LoginRequest; // 登录请求DTO
import com.ailearn.dto.RegisterRequest; // 注册请求DTO
import com.ailearn.entity.User; // 用户实体类，对应数据库user表
import com.ailearn.mapper.UserMapper; // MyBatis-Plus Mapper接口，用户数据访问层
import com.ailearn.security.JwtUtil; // JWT工具类
import com.ailearn.security.UserPrincipal; // 用户主体类
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus Lambda查询构造器
import org.junit.jupiter.api.BeforeEach; // JUnit 5注解，每个测试前执行
import org.junit.jupiter.api.DisplayName; // JUnit 5注解，测试显示名称
import org.junit.jupiter.api.Test; // JUnit 5注解，测试方法
import org.junit.jupiter.api.extension.ExtendWith; // JUnit 5注解，启用扩展
import org.mockito.ArgumentCaptor; // Mockito参数捕获器，用于捕获方法调用时的参数
import org.mockito.InjectMocks; // Mockito注解，自动注入mock对象到被测类
import org.mockito.Mock; // Mockito注解，创建mock对象
import org.mockito.junit.jupiter.MockitoExtension; // Mockito JUnit 5扩展，启用Mockito注解支持
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // BCrypt密码编码器实现
import org.springframework.security.crypto.password.PasswordEncoder; // 密码编码器接口

import java.util.Map; // Map接口

import static org.junit.jupiter.api.Assertions.*; // JUnit断言静态导入
import static org.mockito.ArgumentMatchers.any; // Mockito参数匹配器，匹配任意对象
import static org.mockito.ArgumentMatchers.isA; // Mockito参数匹配器，匹配指定类型
import static org.mockito.Mockito.*; // Mockito静态导入（when、verify、never等）

/**
 * UserService单元测试类
 * 使用Mockito框架mock依赖（UserMapper、PasswordEncoder、JwtUtil）
 * 独立运行，不依赖Spring容器和外部数据库
 *
 * @author AiLearn Platform
 */
@ExtendWith(MockitoExtension.class) // 启用Mockito JUnit 5扩展，支持@Mock、@InjectMocks等注解
@DisplayName("用户服务测试") // 测试类显示名称
class UserServiceTest { // UserService单元测试类定义

    @Mock // 创建UserMapper的Mock对象
    private UserMapper userMapper; // Mock用户数据访问接口

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(); // 使用真实BCrypt实例，行为确定无需mock

    @Mock // 创建JwtUtil的Mock对象
    private JwtUtil jwtUtil; // Mock JWT工具类

    @InjectMocks // 创建UserService实例并自动注入@Mock标注的依赖
    private UserService userService; // 被测UserService实例

    private static final String TEST_USERNAME = "testuser"; // 测试用户名常量
    private static final String TEST_PASSWORD = "password123"; // 测试密码常量
    private static final String TEST_NICKNAME = "测试用户"; // 测试昵称常量
    private static final String TEST_ROLE = "user"; // 测试角色常量
    private static final Long TEST_USER_ID = 1L; // 测试用户ID常量
    private static final String TEST_ACCESS_TOKEN = "test-access-token"; // 测试Access Token常量
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token"; // 测试Refresh Token常量

    @BeforeEach // 每个测试方法执行前初始化
    void setUp() { // 初始化方法
        org.springframework.test.util.ReflectionTestUtils.setField(userService, "passwordEncoder", passwordEncoder); // 反射注入真实的PasswordEncoder
    } // setUp方法结束

    @Test // 测试方法
    @DisplayName("用户注册 - 成功场景") // 测试显示名称
    void testRegister_Success() { // 测试用户注册成功场景
        RegisterRequest req = new RegisterRequest(); // 构造注册请求
        req.setUsername(TEST_USERNAME); // 设置用户名
        req.setPassword(TEST_PASSWORD); // 设置密码
        req.setNickname(TEST_NICKNAME); // 设置昵称
        req.setRole(TEST_ROLE); // 设置角色

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null); // Mock查询用户返回null（用户名不存在）
        doAnswer(invocation -> { // Mock insert操作，模拟MyBatis-Plus回填ID
            User user = invocation.getArgument(0); // 获取传入的User参数
            user.setId(TEST_USER_ID); // 设置用户ID（模拟数据库自增回填）
            return 1; // 返回影响行数1
        }).when(userMapper).insert(any(User.class)); // 当调用userMapper.insert时执行上面的逻辑
        when(jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE)).thenReturn(TEST_ACCESS_TOKEN); // Mock生成Access Token
        when(jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE)).thenReturn(TEST_REFRESH_TOKEN); // Mock生成Refresh Token

        Map<String, Object> result = userService.register(req); // 执行注册

        assertNotNull(result, "注册结果不应为空"); // 断言结果不为空
        assertTrue(result.containsKey("user"), "返回结果应包含user"); // 包含user字段
        assertTrue(result.containsKey("accessToken"), "返回结果应包含accessToken"); // 包含accessToken字段
        assertTrue(result.containsKey("refreshToken"), "返回结果应包含refreshToken"); // 包含refreshToken字段

        UserPrincipal userPrincipal = (UserPrincipal) result.get("user"); // 获取用户主体
        assertEquals(TEST_USER_ID, userPrincipal.getUserId(), "用户ID应正确"); // 用户ID正确
        assertEquals(TEST_USERNAME, userPrincipal.getUsername(), "用户名应正确"); // 用户名正确
        assertEquals(TEST_ROLE, userPrincipal.getRole(), "用户角色应正确"); // 角色正确

        assertEquals(TEST_ACCESS_TOKEN, result.get("accessToken"), "Access Token应正确"); // Access Token正确
        assertEquals(TEST_REFRESH_TOKEN, result.get("refreshToken"), "Refresh Token应正确"); // Refresh Token正确

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class); // 创建User参数捕获器
        verify(userMapper).insert(userCaptor.capture()); // 验证insert被调用并捕获参数
        User savedUser = userCaptor.getValue(); // 获取捕获的User对象
        assertNotEquals(TEST_PASSWORD, savedUser.getPassword(), "保存的密码不应是明文"); // 密码不是明文
        assertTrue(passwordEncoder.matches(TEST_PASSWORD, savedUser.getPassword()), "加密后的密码应能匹配原密码"); // BCrypt加密后能匹配原密码

        verify(jwtUtil).generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 验证generateAccessToken被调用
        verify(jwtUtil).generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 验证generateRefreshToken被调用
    } // testRegister_Success方法结束

    @Test
    @DisplayName("用户注册 - 用户名已存在应抛出异常")
    void testRegister_UsernameExists() { // 测试用户名已存在场景
        RegisterRequest req = new RegisterRequest(); // 构造注册请求
        req.setUsername(TEST_USERNAME); // 设置用户名
        req.setPassword(TEST_PASSWORD); // 设置密码

        User existingUser = new User(); // 构造已存在用户
        existingUser.setUsername(TEST_USERNAME); // 设置用户名
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser); // Mock查询返回已存在用户

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出BusinessException
                () -> userService.register(req), // 执行注册
                "用户名已存在时应抛出BusinessException"); // 断言失败消息

        assertEquals(ErrorCode.USER_USERNAME_EXISTS.getCode(), exception.getCode(), "错误码应为USER_USERNAME_EXISTS"); // 错误码正确

        verify(userMapper, never()).insert(any(User.class)); // 验证insert从未被调用
    } // testRegister_UsernameExists方法结束

    @Test
    @DisplayName("用户注册 - 昵称为空时默认使用用户名")
    void testRegister_NullNickname() { // 测试昵称为空场景
        RegisterRequest req = new RegisterRequest(); // 构造注册请求
        req.setUsername(TEST_USERNAME); // 设置用户名
        req.setPassword(TEST_PASSWORD); // 设置密码
        req.setNickname(null); // 昵称为null

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null); // Mock用户不存在
        doAnswer(invocation -> { // Mock insert回填ID
            User user = invocation.getArgument(0);
            user.setId(TEST_USER_ID);
            return 1;
        }).when(userMapper).insert(any(User.class));
        when(jwtUtil.generateAccessToken(anyLong(), anyString(), anyString())).thenReturn(TEST_ACCESS_TOKEN); // Mock任意参数生成Token
        when(jwtUtil.generateRefreshToken(anyLong(), anyString(), anyString())).thenReturn(TEST_REFRESH_TOKEN);

        userService.register(req); // 执行注册

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class); // 捕获保存的用户
        verify(userMapper).insert(userCaptor.capture()); // 验证insert调用
        assertEquals(TEST_USERNAME, userCaptor.getValue().getNickname(), "昵称为空时应默认使用用户名"); // 昵称默认使用用户名
    } // testRegister_NullNickname方法结束

    @Test
    @DisplayName("用户登录 - 成功场景")
    void testLogin_Success() { // 测试登录成功场景
        LoginRequest req = new LoginRequest(); // 构造登录请求
        req.setUsername(TEST_USERNAME); // 设置用户名
        req.setPassword(TEST_PASSWORD); // 设置密码

        User user = new User(); // 构造用户
        user.setId(TEST_USER_ID); // 设置用户ID
        user.setUsername(TEST_USERNAME); // 设置用户名
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD)); // 设置BCrypt加密后的密码
        user.setRole(TEST_ROLE); // 设置角色
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user); // Mock查询到用户

        when(jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE)).thenReturn(TEST_ACCESS_TOKEN); // Mock Token生成
        when(jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE)).thenReturn(TEST_REFRESH_TOKEN);

        Map<String, Object> result = userService.login(req); // 执行登录

        assertNotNull(result); // 结果不为空
        UserPrincipal userPrincipal = (UserPrincipal) result.get("user"); // 获取用户主体
        assertEquals(TEST_USER_ID, userPrincipal.getUserId()); // 用户ID正确
        assertEquals(TEST_USERNAME, userPrincipal.getUsername()); // 用户名正确
        assertEquals(TEST_ACCESS_TOKEN, result.get("accessToken")); // Token正确
        assertEquals(TEST_REFRESH_TOKEN, result.get("refreshToken"));

        verify(jwtUtil).generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 验证Token生成调用
    } // testLogin_Success方法结束

    @Test
    @DisplayName("用户登录 - 用户不存在应抛出异常")
    void testLogin_UserNotFound() { // 测试用户不存在场景
        LoginRequest req = new LoginRequest(); // 构造登录请求
        req.setUsername(TEST_USERNAME); // 设置用户名
        req.setPassword(TEST_PASSWORD); // 设置密码

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null); // Mock查询不到用户

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出异常
                () -> userService.login(req),
                "用户不存在时应抛出异常");
        assertEquals(ErrorCode.AUTH_LOGIN_FAILED.getCode(), exception.getCode(), "错误码应为AUTH_LOGIN_FAILED"); // 错误码是登录失败（不透露具体原因）
    } // testLogin_UserNotFound方法结束

    @Test
    @DisplayName("用户登录 - 密码错误应抛出异常")
    void testLogin_WrongPassword() { // 测试密码错误场景
        LoginRequest req = new LoginRequest(); // 构造登录请求
        req.setUsername(TEST_USERNAME); // 设置用户名
        req.setPassword("wrongpassword"); // 错误密码

        User user = new User(); // 构造用户
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD)); // 正确密码的加密值
        user.setRole(TEST_ROLE);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user); // Mock查询到用户

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出异常
                () -> userService.login(req),
                "密码错误时应抛出异常");
        assertEquals(ErrorCode.AUTH_LOGIN_FAILED.getCode(), exception.getCode()); // 错误码是登录失败
    } // testLogin_WrongPassword方法结束

    @Test
    @DisplayName("刷新Token - 成功场景")
    void testRefreshToken_Success() { // 测试刷新Token成功场景
        String refreshToken = TEST_REFRESH_TOKEN; // 测试Refresh Token

        when(jwtUtil.validateRefreshToken(refreshToken)).thenReturn(true); // Mock Refresh Token有效
        when(jwtUtil.extractTokenType(refreshToken)).thenReturn(JwtUtil.TOKEN_TYPE_REFRESH); // Mock Token类型是refresh
        when(jwtUtil.extractUserId(refreshToken)).thenReturn(TEST_USER_ID); // Mock提取用户ID
        when(jwtUtil.extractUsername(refreshToken)).thenReturn(TEST_USERNAME); // Mock提取用户名
        when(jwtUtil.extractRole(refreshToken)).thenReturn(TEST_ROLE); // Mock提取角色
        when(jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE)).thenReturn("new-access-token"); // Mock生成新Access Token

        String newAccessToken = userService.refreshToken(refreshToken); // 执行刷新

        assertEquals("new-access-token", newAccessToken, "应返回新的Access Token"); // 返回新Token
        verify(jwtUtil).generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 验证Token生成调用
    } // testRefreshToken_Success方法结束

    @Test
    @DisplayName("刷新Token - 无效Token应抛出异常")
    void testRefreshToken_InvalidToken() { // 测试无效Token场景
        String invalidToken = "invalid-token"; // 无效Token

        when(jwtUtil.validateRefreshToken(invalidToken)).thenReturn(false); // Mock验证失败

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出异常
                () -> userService.refreshToken(invalidToken));
        assertEquals(ErrorCode.AUTH_TOKEN_INVALID.getCode(), exception.getCode()); // 错误码Token无效
    } // testRefreshToken_InvalidToken方法结束

    @Test
    @DisplayName("刷新Token - Token类型错误（Access Token）应抛出异常")
    void testRefreshToken_WrongTokenType() { // 测试Token类型错误场景
        String accessToken = "access-token"; // Access Token

        when(jwtUtil.validateRefreshToken(accessToken)).thenReturn(true); // Mock验证通过（但类型错误）
        when(jwtUtil.extractTokenType(accessToken)).thenReturn(JwtUtil.TOKEN_TYPE_ACCESS); // Mock返回access类型

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出异常
                () -> userService.refreshToken(accessToken));
        assertEquals(ErrorCode.AUTH_TOKEN_INVALID.getCode(), exception.getCode()); // 错误码Token无效
    } // testRefreshToken_WrongTokenType方法结束

    @Test
    @DisplayName("根据ID查询用户 - 成功场景")
    void testGetUserById_Success() { // 测试按ID查询用户成功
        User user = new User(); // 构造用户
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        when(userMapper.selectById(TEST_USER_ID)).thenReturn(user); // Mock查询到用户

        User result = userService.getUserById(TEST_USER_ID); // 执行查询

        assertNotNull(result); // 结果不为空
        assertEquals(TEST_USER_ID, result.getId()); // ID正确
        assertEquals(TEST_USERNAME, result.getUsername()); // 用户名正确
    } // testGetUserById_Success方法结束

    @Test
    @DisplayName("根据ID查询用户 - 用户不存在应抛出异常")
    void testGetUserById_NotFound() { // 测试用户不存在场景
        when(userMapper.selectById(TEST_USER_ID)).thenReturn(null); // Mock查询不到用户

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出异常
                () -> userService.getUserById(TEST_USER_ID));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), exception.getCode()); // 错误码用户不存在
    } // testGetUserById_NotFound方法结束

    @Test
    @DisplayName("更新用户昵称 - 成功场景")
    void testUpdateNickname_Success() { // 测试更新昵称成功
        String newNickname = "新昵称"; // 新昵称

        User user = new User(); // 构造用户
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setNickname(TEST_NICKNAME);
        when(userMapper.selectById(TEST_USER_ID)).thenReturn(user); // Mock查询到用户

        User result = userService.updateNickname(TEST_USER_ID, newNickname); // 执行更新

        assertEquals(newNickname, result.getNickname()); // 昵称已更新
        verify(userMapper).updateById(user); // 验证updateById被调用
    } // testUpdateNickname_Success方法结束

    @Test
    @DisplayName("更新用户昵称 - 用户不存在应抛出异常")
    void testUpdateNickname_UserNotFound() { // 测试用户不存在场景
        when(userMapper.selectById(TEST_USER_ID)).thenReturn(null); // Mock查询不到用户

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出异常
                () -> userService.updateNickname(TEST_USER_ID, "新昵称"));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), exception.getCode()); // 错误码用户不存在
        verify(userMapper, never()).updateById(isA(User.class)); // 验证updateById未被调用
    } // testUpdateNickname_UserNotFound方法结束

    @Test
    @DisplayName("创建用户 - 第一个用户应为管理员")
    void testCreateUser_FirstUserIsAdmin() { // 测试第一个用户是管理员
        when(userMapper.selectCount(null)).thenReturn(0L); // Mock用户数为0（第一个用户）
        doAnswer(invocation -> { // Mock insert回填ID
            User user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        User result = userService.createUser(TEST_USERNAME, TEST_NICKNAME, TEST_PASSWORD); // 执行创建用户

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class); // 捕获保存的用户
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("admin", userCaptor.getValue().getRole(), "第一个用户应为管理员"); // 角色是admin
    } // testCreateUser_FirstUserIsAdmin方法结束

    @Test
    @DisplayName("创建用户 - 非第一个用户应为普通用户")
    void testCreateUser_NotFirstUserIsNormal() { // 测试非第一个用户是普通用户
        when(userMapper.selectCount(null)).thenReturn(5L); // Mock已有5个用户
        doAnswer(invocation -> { // Mock insert回填ID
            User user = invocation.getArgument(0);
            user.setId(6L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        userService.createUser(TEST_USERNAME, TEST_NICKNAME, TEST_PASSWORD); // 执行创建

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class); // 捕获保存的用户
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("user", userCaptor.getValue().getRole(), "非第一个用户应为普通用户"); // 角色是user
    } // testCreateUser_NotFirstUserIsNormal方法结束

    @Test
    @DisplayName("根据用户名查询用户")
    void testFindByUsername() { // 测试按用户名查询用户
        User user = new User(); // 构造用户
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user); // Mock查询到用户

        User result = userService.findByUsername(TEST_USERNAME); // 执行查询

        assertNotNull(result); // 结果不为空
        assertEquals(TEST_USERNAME, result.getUsername()); // 用户名正确
    } // testFindByUsername方法结束
} // UserServiceTest类结束
