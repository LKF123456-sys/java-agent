package com.ailearn.security; // 声明包名，security包存放认证授权相关类

import io.jsonwebtoken.Claims; // JJWT库类，表示JWT的声明体（payload），包含JWT中的所有 claims 信息
import io.jsonwebtoken.Jwts; // JJWT库核心类，用于构建、解析、验证JWT令牌
import io.jsonwebtoken.security.Keys; // JJWT库类，用于生成和处理HMAC签名密钥
import org.junit.jupiter.api.BeforeEach; // JUnit 5注解，标记每个测试方法执行前要运行的方法
import org.junit.jupiter.api.DisplayName; // JUnit 5注解，为测试类或测试方法指定自定义显示名称
import org.junit.jupiter.api.Test; // JUnit 5注解，标记一个方法为测试方法
import org.springframework.test.util.ReflectionTestUtils; // Spring测试工具类，用于通过反射注入私有字段，方便单元测试

import java.nio.charset.StandardCharsets; // Java标准库类，定义标准字符集常量（如UTF-8）
import java.util.Date; // Java标准库类，表示日期时间
import java.util.Map; // Java标准库类，键值对映射接口

import static org.junit.jupiter.api.Assertions.*; // 静态导入JUnit 5所有断言方法（assertEquals、assertNotNull、assertTrue等）

/**
 * JwtUtil单元测试类
 * 测试JWT令牌的生成、验证、解析、过期判断等核心功能
 * 使用ReflectionTestUtils注入测试配置，不依赖Spring容器
 *
 * @author AiLearn Platform
 */
@DisplayName("JWT工具类测试") // JUnit 5注解，指定该测试类在测试报告中显示的名称
class JwtUtilTest { // JwtUtil单元测试类定义（注意：包级私有，无public修饰符）

    /**
     * 待测试的JwtUtil实例
     */
    private JwtUtil jwtUtil; // 声明待测试的JwtUtil对象，在@BeforeEach中初始化

    /**
     * 测试用的JWT密钥
     */
    private static final String TEST_SECRET = "test-jwt-secret-key-for-unit-testing-only-please-do-not-use-in-production-0123456789"; // 测试用密钥，足够长满足HMAC-SHA签名要求

    /**
     * 测试用的Access Token过期时间：1小时（毫秒）
     */
    private static final Long TEST_ACCESS_EXPIRATION = 3600000L; // Access Token过期时间常量，1小时=60*60*1000毫秒

    /**
     * 测试用的Refresh Token过期时间：1天（毫秒）
     */
    private static final Long TEST_REFRESH_EXPIRATION = 86400000L; // Refresh Token过期时间常量，1天=24*60*60*1000毫秒

    /**
     * 测试用户ID
     */
    private static final Long TEST_USER_ID = 1L; // 测试用户ID常量

    /**
     * 测试用户名
     */
    private static final String TEST_USERNAME = "testuser"; // 测试用户名常量

    /**
     * 测试用户角色
     */
    private static final String TEST_ROLE = "user"; // 测试用户角色常量

    /**
     * 每个测试方法执行前初始化JwtUtil实例
     * 使用ReflectionTestUtils注入私有字段，模拟Spring @Value注入
     */
    @BeforeEach // JUnit 5注解，标记该方法在每个测试方法执行前运行
    void setUp() { // 初始化方法
        jwtUtil = new JwtUtil(); // 创建JwtUtil实例
        ReflectionTestUtils.setField(jwtUtil, "secretKey", TEST_SECRET); // 通过反射注入私有字段secretKey（测试密钥）
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", TEST_ACCESS_EXPIRATION); // 通过反射注入accessToken过期时间
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", TEST_REFRESH_EXPIRATION); // 通过反射注入refreshToken过期时间
    } // setUp方法结束

    /**
     * 测试生成Access Token
     * 验证：Token不为空、可以正确解析出用户信息、Token类型为access
     */
    @Test // JUnit 5注解，标记为测试方法
    @DisplayName("生成Access Token - 验证Token生成和内容正确性") // 指定测试方法显示名称
    void testGenerateAccessToken() { // 测试生成Access Token的方法
        String token = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 调用被测方法生成Access Token

        assertNotNull(token, "生成的Access Token不应为空"); // 断言Token不为null
        assertFalse(token.isEmpty(), "生成的Access Token不应为空字符串"); // 断言Token不是空字符串

        String username = jwtUtil.extractUsername(token); // 从Token中提取用户名
        assertEquals(TEST_USERNAME, username, "解析出的用户名应与生成时一致"); // 断言提取的用户名与生成时一致

        Long userId = jwtUtil.extractUserId(token); // 从Token中提取用户ID
        assertEquals(TEST_USER_ID, userId, "解析出的用户ID应与生成时一致"); // 断言提取的用户ID正确

        String role = jwtUtil.extractRole(token); // 从Token中提取用户角色
        assertEquals(TEST_ROLE, role, "解析出的用户角色应与生成时一致"); // 断言提取的角色正确

        String tokenType = jwtUtil.extractTokenType(token); // 从Token中提取Token类型
        assertEquals(JwtUtil.TOKEN_TYPE_ACCESS, tokenType, "Token类型应为access"); // 断言Token类型为access

        assertTrue(jwtUtil.validateAccessToken(token), "新生成的Access Token应验证通过"); // 断言Access Token验证通过
        assertFalse(jwtUtil.validateRefreshToken(token), "Access Token不应通过Refresh Token验证"); // 断言Access Token不能作为Refresh Token通过验证
    } // testGenerateAccessToken方法结束

    /**
     * 测试生成Refresh Token
     * 验证：Token不为空、可以正确解析出用户信息、Token类型为refresh
     */
    @Test
    @DisplayName("生成Refresh Token - 验证Token生成和内容正确性")
    void testGenerateRefreshToken() { // 测试生成Refresh Token的方法
        String token = jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 调用被测方法生成Refresh Token

        assertNotNull(token, "生成的Refresh Token不应为空"); // 断言Token不为null
        assertFalse(token.isEmpty(), "生成的Refresh Token不应为空字符串"); // 断言Token不是空字符串

        String username = jwtUtil.extractUsername(token); // 提取用户名
        assertEquals(TEST_USERNAME, username, "解析出的用户名应与生成时一致"); // 断言用户名正确

        Long userId = jwtUtil.extractUserId(token); // 提取用户ID
        assertEquals(TEST_USER_ID, userId, "解析出的用户ID应与生成时一致"); // 断言用户ID正确

        String role = jwtUtil.extractRole(token); // 提取角色
        assertEquals(TEST_ROLE, role, "解析出的用户角色应与生成时一致"); // 断言角色正确

        String tokenType = jwtUtil.extractTokenType(token); // 提取Token类型
        assertEquals(JwtUtil.TOKEN_TYPE_REFRESH, tokenType, "Token类型应为refresh"); // 断言Token类型为refresh

        assertTrue(jwtUtil.validateRefreshToken(token), "新生成的Refresh Token应验证通过"); // 断言Refresh Token验证通过
        assertFalse(jwtUtil.validateAccessToken(token), "Refresh Token不应通过Access Token验证"); // 断言Refresh Token不能作为Access Token通过验证
    } // testGenerateRefreshToken方法结束

    /**
     * 测试Token过期时间提取
     * 验证：提取的过期时间在合理范围内
     */
    @Test
    @DisplayName("提取Token过期时间 - 验证过期时间正确性")
    void testExtractExpiration() { // 测试提取过期时间的方法
        long beforeGenerate = System.currentTimeMillis(); // 记录生成Token前的时间
        String token = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 生成Token
        long afterGenerate = System.currentTimeMillis(); // 记录生成Token后的时间

        Date expiration = jwtUtil.extractExpiration(token); // 提取过期时间
        assertNotNull(expiration, "过期时间不应为空"); // 断言过期时间不为null

        long expectedExpirationMin = beforeGenerate + TEST_ACCESS_EXPIRATION - 1000; // 计算预期最小过期时间（允许1秒误差）
        long expectedExpirationMax = afterGenerate + TEST_ACCESS_EXPIRATION + 1000; // 计算预期最大过期时间
        assertTrue(expiration.getTime() >= expectedExpirationMin, "过期时间不应早于预期的最小时间"); // 断言过期时间不早于最小值
        assertTrue(expiration.getTime() <= expectedExpirationMax, "过期时间不应晚于预期的最大时间"); // 断言过期时间不晚于最大值
    } // testExtractExpiration方法结束

    /**
     * 测试未过期的Token判断
     * 验证：新生成的Token不应被判定为过期
     */
    @Test
    @DisplayName("判断Token过期 - 新生成Token不应过期")
    void testIsTokenExpired_NotExpired() { // 测试未过期Token的方法
        String token = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 生成新Token
        assertFalse(jwtUtil.isTokenExpired(token), "新生成的Token不应过期"); // 断言新Token未过期
    } // testIsTokenExpired_NotExpired方法结束

    /**
     * 测试已过期的Token判断
     * 验证：手动构造的过期Token应被正确识别为过期
     */
    @Test
    @DisplayName("判断Token过期 - 已过期Token应正确识别")
    void testIsTokenExpired_Expired() { // 测试已过期Token的方法
        Date now = new Date(); // 获取当前时间
        Date expiredDate = new Date(now.getTime() - 3600000); // 构造过期时间（1小时前）

        String expiredToken = Jwts.builder() // 使用JJWT构建器手动构造过期Token
                .subject(TEST_USERNAME) // 设置subject（用户名）
                .claim("userId", TEST_USER_ID) // 添加userId自定义声明
                .claim("role", TEST_ROLE) // 添加role自定义声明
                .claim("tokenType", JwtUtil.TOKEN_TYPE_ACCESS) // 添加tokenType声明
                .issuedAt(new Date(now.getTime() - 7200000)) // 设置签发时间为2小时前
                .expiration(expiredDate) // 设置过期时间为1小时前（已过期）
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8))) // 使用测试密钥签名
                .compact(); // 构建并压缩为Token字符串

        assertTrue(jwtUtil.isTokenExpired(expiredToken), "已过期的Token应被识别为过期"); // 断言过期Token被识别为过期
        assertFalse(jwtUtil.validateAccessToken(expiredToken), "已过期的Token验证应失败"); // 断言过期Token验证失败
    } // testIsTokenExpired_Expired方法结束

    /**
     * 测试无效Token的处理
     * 验证：格式错误的Token、篡改的Token应正确被拒绝
     */
    @Test
    @DisplayName("验证无效Token - 格式错误/篡改的Token应验证失败")
    void testValidateToken_InvalidToken() { // 测试无效Token处理的方法
        assertFalse(jwtUtil.validateAccessToken(""), "空字符串Token应验证失败"); // 测试空字符串Token
        assertNull(jwtUtil.extractUsername(""), "空字符串Token应无法提取用户名"); // 空Token无法提取用户名

        String invalidToken = "invalid.token.here"; // 构造格式错误的Token
        assertFalse(jwtUtil.validateAccessToken(invalidToken), "格式错误的Token应验证失败"); // 格式错误Token验证失败
        assertNull(jwtUtil.extractUsername(invalidToken), "格式错误的Token应无法提取用户名"); // 格式错误Token无法提取用户名

        String validToken = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 生成有效Token
        String tamperedToken = validToken.substring(0, validToken.lastIndexOf('.') + 1) + "tampered"; // 篡改签名部分
        assertFalse(jwtUtil.validateAccessToken(tamperedToken), "被篡改的Token应验证失败"); // 被篡改Token验证失败

        String wrongSecretToken = Jwts.builder() // 构造使用错误密钥签名的Token
                .subject(TEST_USERNAME) // 设置用户名
                .claim("userId", TEST_USER_ID) // 设置用户ID
                .claim("role", TEST_ROLE) // 设置角色
                .claim("tokenType", JwtUtil.TOKEN_TYPE_ACCESS) // 设置Token类型
                .issuedAt(new Date()) // 设置签发时间为现在
                .expiration(new Date(System.currentTimeMillis() + 3600000)) // 设置过期时间1小时后
                .signWith(Keys.hmacShaKeyFor("wrong-secret-key-for-testing-12345678901234567890".getBytes(StandardCharsets.UTF_8))) // 使用错误密钥签名
                .compact(); // 构建Token
        assertFalse(jwtUtil.validateAccessToken(wrongSecretToken), "使用错误密钥签名的Token应验证失败"); // 错误密钥Token验证失败
    } // testValidateToken_InvalidToken方法结束

    /**
     * 测试null输入的处理
     * 验证：传入null时不抛出异常，返回null或false
     */
    @Test
    @DisplayName("处理null输入 - 不应抛出异常")
    void testNullInput() { // 测试null输入处理的方法
        assertNull(jwtUtil.extractUsername(null), "null Token提取用户名应返回null"); // null Token提取用户名返回null
        assertNull(jwtUtil.extractUserId(null), "null Token提取用户ID应返回null"); // null Token提取用户ID返回null
        assertNull(jwtUtil.extractRole(null), "null Token提取角色应返回null"); // null Token提取角色返回null
        assertNull(jwtUtil.extractTokenType(null), "null Token提取类型应返回null"); // null Token提取类型返回null
        assertNull(jwtUtil.extractExpiration(null), "null Token提取过期时间应返回null"); // null Token提取过期时间返回null
        assertTrue(jwtUtil.isTokenExpired(null), "null Token应被视为已过期"); // null Token视为过期
        assertFalse(jwtUtil.validateAccessToken(null), "null Token验证应返回false"); // null Token验证失败
        assertFalse(jwtUtil.validateRefreshToken(null), "null Token验证应返回false"); // null Token验证失败
        assertNull(jwtUtil.refreshTokens(null), "null Token刷新应返回null"); // null Token刷新返回null
        assertNull(jwtUtil.refreshAccessToken(null), "null Token刷新应返回null"); // null Token刷新返回null
    } // testNullInput方法结束

    /**
     * 测试refreshTokens方法（刷新Token对）
     * 验证：使用有效的Refresh Token可以刷新出一对新的Token
     */
    @Test
    @DisplayName("刷新Token对 - 使用Refresh Token获取新Token对")
    void testRefreshTokens() { // 测试刷新Token对的方法
        String refreshToken = jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 先生成一个有效的Refresh Token

        Map<String, String> tokens = jwtUtil.refreshTokens(refreshToken); // 调用刷新方法

        assertNotNull(tokens, "刷新Token应返回结果"); // 断言返回结果不为null
        assertTrue(tokens.containsKey("accessToken"), "返回结果应包含accessToken"); // 结果包含accessToken
        assertTrue(tokens.containsKey("refreshToken"), "返回结果应包含refreshToken"); // 结果包含refreshToken

        String newAccessToken = tokens.get("accessToken"); // 获取新的Access Token
        String newRefreshToken = tokens.get("refreshToken"); // 获取新的Refresh Token
        assertTrue(jwtUtil.validateAccessToken(newAccessToken), "新生成的Access Token应有效"); // 新Access Token有效
        assertTrue(jwtUtil.validateRefreshToken(newRefreshToken), "新生成的Refresh Token应有效"); // 新Refresh Token有效

        assertEquals(TEST_USERNAME, jwtUtil.extractUsername(newAccessToken), "新Access Token用户信息正确"); // 新Token用户信息正确
        assertEquals(TEST_USER_ID, jwtUtil.extractUserId(newAccessToken), "新Access Token用户ID正确"); // 新Token用户ID正确
    } // testRefreshTokens方法结束

    /**
     * 测试refreshAccessToken方法（仅刷新Access Token）
     * 验证：使用有效的Refresh Token可以刷新出新的Access Token
     */
    @Test
    @DisplayName("刷新Access Token - 使用Refresh Token获取新Access Token")
    void testRefreshAccessToken() { // 测试仅刷新Access Token的方法
        String refreshToken = jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 生成Refresh Token

        String newAccessToken = jwtUtil.refreshAccessToken(refreshToken); // 调用刷新Access Token方法

        assertNotNull(newAccessToken, "刷新Access Token应返回结果"); // 返回结果不为null
        assertTrue(jwtUtil.validateAccessToken(newAccessToken), "新生成的Access Token应有效"); // 新Token有效

        assertEquals(TEST_USERNAME, jwtUtil.extractUsername(newAccessToken), "新Access Token用户名正确"); // 用户名正确
        assertEquals(TEST_USER_ID, jwtUtil.extractUserId(newAccessToken), "新Access Token用户ID正确"); // 用户ID正确
        assertEquals(TEST_ROLE, jwtUtil.extractRole(newAccessToken), "新Access Token角色正确"); // 角色正确
    } // testRefreshAccessToken方法结束

    /**
     * 测试使用无效Token刷新
     * 验证：无效Token无法用于刷新
     */
    @Test
    @DisplayName("刷新Token失败 - 无效Token无法刷新")
    void testRefreshTokens_InvalidToken() { // 测试无效Token刷新的方法
        String accessToken = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 生成Access Token
        assertNull(jwtUtil.refreshTokens(accessToken), "Access Token不应能用于刷新Token对"); // Access Token不能刷新Token对
        assertNull(jwtUtil.refreshAccessToken(accessToken), "Access Token不应能用于刷新Access Token"); // Access Token不能刷新Access Token

        String expiredToken = Jwts.builder() // 构造已过期的Refresh Token
                .subject(TEST_USERNAME)
                .claim("userId", TEST_USER_ID)
                .claim("role", TEST_ROLE)
                .claim("tokenType", JwtUtil.TOKEN_TYPE_REFRESH) // Token类型为refresh
                .issuedAt(new Date(System.currentTimeMillis() - 86400000 * 2)) // 2天前签发
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // 1小时前过期
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8))) // 正确密钥签名
                .compact();
        assertNull(jwtUtil.refreshTokens(expiredToken), "过期的Refresh Token不应能刷新"); // 过期Token不能刷新
    } // testRefreshTokens_InvalidToken方法结束

    /**
     * 测试isTokenExpiredException方法（区分过期异常和其他异常）
     * 验证：正确识别Token是否因过期而无效
     */
    @Test
    @DisplayName("判断Token过期异常 - 区分过期和其他错误")
    void testIsTokenExpiredException() { // 测试判断Token过期异常的方法
        String validToken = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 生成有效Token
        assertFalse(jwtUtil.isTokenExpiredException(validToken), "有效Token不应判定为过期异常"); // 有效Token不是过期异常

        String expiredToken = Jwts.builder() // 构造已过期Token
                .subject(TEST_USERNAME)
                .claim("userId", TEST_USER_ID)
                .claim("role", TEST_ROLE)
                .claim("tokenType", JwtUtil.TOKEN_TYPE_ACCESS)
                .issuedAt(new Date(System.currentTimeMillis() - 7200000)) // 2小时前签发
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // 1小时前过期
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertTrue(jwtUtil.isTokenExpiredException(expiredToken), "过期Token应判定为过期异常"); // 过期Token是过期异常

        assertFalse(jwtUtil.isTokenExpiredException("invalid"), "无效Token不应判定为过期异常"); // 无效Token不是过期异常（格式错误）
    } // testIsTokenExpiredException方法结束

    /**
     * 测试extractClaimsIgnoringExpiration方法（过期Token仍能提取Claims）
     * 验证：即使Token已过期，仍能从中提取Claims信息
     */
    @Test
    @DisplayName("忽略过期提取Claims - 过期Token仍能解析Claims")
    void testExtractClaimsIgnoringExpiration() { // 测试忽略过期提取Claims的方法
        String expiredToken = Jwts.builder() // 构造已过期但签名有效的Token
                .subject(TEST_USERNAME)
                .claim("userId", TEST_USER_ID)
                .claim("role", TEST_ROLE)
                .claim("tokenType", JwtUtil.TOKEN_TYPE_ACCESS)
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        Claims claims = jwtUtil.extractClaimsIgnoringExpiration(expiredToken); // 忽略过期提取Claims
        assertNotNull(claims, "过期Token应仍能提取Claims"); // 能提取到Claims
        assertEquals(TEST_USERNAME, claims.getSubject(), "提取的用户名应正确"); // 用户名正确
        assertEquals(TEST_USER_ID, claims.get("userId", Long.class), "提取的用户ID应正确"); // 用户ID正确
        assertEquals(TEST_ROLE, claims.get("role", String.class), "提取的角色应正确"); // 角色正确
    } // testExtractClaimsIgnoringExpiration方法结束

    /**
     * 测试不同角色用户的Token生成
     * 验证：不同角色（admin/user）的Token都能正确生成和验证
     */
    @Test
    @DisplayName("不同角色Token生成 - admin/user角色均正常")
    void testDifferentRoles() { // 测试不同角色Token生成的方法
        String adminToken = jwtUtil.generateAccessToken(2L, "admin", "admin"); // 生成管理员Token
        assertEquals("admin", jwtUtil.extractRole(adminToken), "管理员角色应正确提取"); // 管理员角色正确
        assertTrue(jwtUtil.validateAccessToken(adminToken), "管理员Token应验证通过"); // 管理员Token验证通过

        String userToken = jwtUtil.generateAccessToken(3L, "normaluser", "user"); // 生成普通用户Token
        assertEquals("user", jwtUtil.extractRole(userToken), "普通用户角色应正确提取"); // 普通用户角色正确
        assertTrue(jwtUtil.validateAccessToken(userToken), "普通用户Token应验证通过"); // 普通用户Token验证通过
    } // testDifferentRoles方法结束
} // JwtUtilTest类结束
