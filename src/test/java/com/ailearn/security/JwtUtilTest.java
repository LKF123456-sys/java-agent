package com.ailearn.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil单元测试类
 * 测试JWT令牌的生成、验证、解析、过期判断等核心功能
 * 使用ReflectionTestUtils注入测试配置，不依赖Spring容器
 *
 * @author AiLearn Platform
 */
@DisplayName("JWT工具类测试")
class JwtUtilTest {

    /**
     * 待测试的JwtUtil实例
     */
    private JwtUtil jwtUtil;

    /**
     * 测试用的JWT密钥
     */
    private static final String TEST_SECRET = "test-jwt-secret-key-for-unit-testing-only-please-do-not-use-in-production-0123456789";

    /**
     * 测试用的Access Token过期时间：1小时（毫秒）
     */
    private static final Long TEST_ACCESS_EXPIRATION = 3600000L;

    /**
     * 测试用的Refresh Token过期时间：1天（毫秒）
     */
    private static final Long TEST_REFRESH_EXPIRATION = 86400000L;

    /**
     * 测试用户ID
     */
    private static final Long TEST_USER_ID = 1L;

    /**
     * 测试用户名
     */
    private static final String TEST_USERNAME = "testuser";

    /**
     * 测试用户角色
     */
    private static final String TEST_ROLE = "user";

    /**
     * 每个测试方法执行前初始化JwtUtil实例
     * 使用ReflectionTestUtils注入私有字段，模拟Spring @Value注入
     */
    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // 注入测试密钥
        ReflectionTestUtils.setField(jwtUtil, "secretKey", TEST_SECRET);
        // 注入Access Token过期时间
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", TEST_ACCESS_EXPIRATION);
        // 注入Refresh Token过期时间
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", TEST_REFRESH_EXPIRATION);
    }

    /**
     * 测试生成Access Token
     * 验证：Token不为空、可以正确解析出用户信息、Token类型为access
     */
    @Test
    @DisplayName("生成Access Token - 验证Token生成和内容正确性")
    void testGenerateAccessToken() {
        // 执行：生成Access Token
        String token = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);

        // 验证：Token不为空
        assertNotNull(token, "生成的Access Token不应为空");
        assertFalse(token.isEmpty(), "生成的Access Token不应为空字符串");

        // 验证：可以正确解析用户名
        String username = jwtUtil.extractUsername(token);
        assertEquals(TEST_USERNAME, username, "解析出的用户名应与生成时一致");

        // 验证：可以正确解析用户ID
        Long userId = jwtUtil.extractUserId(token);
        assertEquals(TEST_USER_ID, userId, "解析出的用户ID应与生成时一致");

        // 验证：可以正确解析用户角色
        String role = jwtUtil.extractRole(token);
        assertEquals(TEST_ROLE, role, "解析出的用户角色应与生成时一致");

        // 验证：Token类型为access
        String tokenType = jwtUtil.extractTokenType(token);
        assertEquals(JwtUtil.TOKEN_TYPE_ACCESS, tokenType, "Token类型应为access");

        // 验证：Access Token验证通过
        assertTrue(jwtUtil.validateAccessToken(token), "新生成的Access Token应验证通过");
        // 验证：不应作为Refresh Token验证通过
        assertFalse(jwtUtil.validateRefreshToken(token), "Access Token不应通过Refresh Token验证");
    }

    /**
     * 测试生成Refresh Token
     * 验证：Token不为空、可以正确解析出用户信息、Token类型为refresh
     */
    @Test
    @DisplayName("生成Refresh Token - 验证Token生成和内容正确性")
    void testGenerateRefreshToken() {
        // 执行：生成Refresh Token
        String token = jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);

        // 验证：Token不为空
        assertNotNull(token, "生成的Refresh Token不应为空");
        assertFalse(token.isEmpty(), "生成的Refresh Token不应为空字符串");

        // 验证：可以正确解析用户名
        String username = jwtUtil.extractUsername(token);
        assertEquals(TEST_USERNAME, username, "解析出的用户名应与生成时一致");

        // 验证：可以正确解析用户ID
        Long userId = jwtUtil.extractUserId(token);
        assertEquals(TEST_USER_ID, userId, "解析出的用户ID应与生成时一致");

        // 验证：可以正确解析用户角色
        String role = jwtUtil.extractRole(token);
        assertEquals(TEST_ROLE, role, "解析出的用户角色应与生成时一致");

        // 验证：Token类型为refresh
        String tokenType = jwtUtil.extractTokenType(token);
        assertEquals(JwtUtil.TOKEN_TYPE_REFRESH, tokenType, "Token类型应为refresh");

        // 验证：Refresh Token验证通过
        assertTrue(jwtUtil.validateRefreshToken(token), "新生成的Refresh Token应验证通过");
        // 验证：不应作为Access Token验证通过
        assertFalse(jwtUtil.validateAccessToken(token), "Refresh Token不应通过Access Token验证");
    }

    /**
     * 测试Token过期时间提取
     * 验证：提取的过期时间在合理范围内
     */
    @Test
    @DisplayName("提取Token过期时间 - 验证过期时间正确性")
    void testExtractExpiration() {
        // 生成Token前记录时间
        long beforeGenerate = System.currentTimeMillis();
        String token = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        long afterGenerate = System.currentTimeMillis();

        // 提取过期时间
        Date expiration = jwtUtil.extractExpiration(token);
        assertNotNull(expiration, "过期时间不应为空");

        // 验证：过期时间应该在生成时间+过期时间的范围内（允许1秒误差）
        long expectedExpirationMin = beforeGenerate + TEST_ACCESS_EXPIRATION - 1000;
        long expectedExpirationMax = afterGenerate + TEST_ACCESS_EXPIRATION + 1000;
        assertTrue(expiration.getTime() >= expectedExpirationMin,
                "过期时间不应早于预期的最小时间");
        assertTrue(expiration.getTime() <= expectedExpirationMax,
                "过期时间不应晚于预期的最大时间");
    }

    /**
     * 测试未过期的Token判断
     * 验证：新生成的Token不应被判定为过期
     */
    @Test
    @DisplayName("判断Token过期 - 新生成Token不应过期")
    void testIsTokenExpired_NotExpired() {
        String token = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        assertFalse(jwtUtil.isTokenExpired(token), "新生成的Token不应过期");
    }

    /**
     * 测试已过期的Token判断
     * 验证：手动构造的过期Token应被正确识别为过期
     */
    @Test
    @DisplayName("判断Token过期 - 已过期Token应正确识别")
    void testIsTokenExpired_Expired() {
        // 手动构造一个已过期的Token（过期时间设置为1小时前）
        Date now = new Date();
        Date expiredDate = new Date(now.getTime() - 3600000);

        String expiredToken = Jwts.builder()
                .subject(TEST_USERNAME)
                .claim("userId", TEST_USER_ID)
                .claim("role", TEST_ROLE)
                .claim("tokenType", JwtUtil.TOKEN_TYPE_ACCESS)
                .issuedAt(new Date(now.getTime() - 7200000))
                .expiration(expiredDate)
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertTrue(jwtUtil.isTokenExpired(expiredToken), "已过期的Token应被识别为过期");
        assertFalse(jwtUtil.validateAccessToken(expiredToken), "已过期的Token验证应失败");
    }

    /**
     * 测试无效Token的处理
     * 验证：格式错误的Token、篡改的Token应正确被拒绝
     */
    @Test
    @DisplayName("验证无效Token - 格式错误/篡改的Token应验证失败")
    void testValidateToken_InvalidToken() {
        // 测试1：空字符串Token
        assertFalse(jwtUtil.validateAccessToken(""), "空字符串Token应验证失败");
        assertNull(jwtUtil.extractUsername(""), "空字符串Token应无法提取用户名");

        // 测试2：格式错误的Token
        String invalidToken = "invalid.token.here";
        assertFalse(jwtUtil.validateAccessToken(invalidToken), "格式错误的Token应验证失败");
        assertNull(jwtUtil.extractUsername(invalidToken), "格式错误的Token应无法提取用户名");

        // 测试3：被篡改的Token（修改payload部分）
        String validToken = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        String tamperedToken = validToken.substring(0, validToken.lastIndexOf('.') + 1) + "tampered";
        assertFalse(jwtUtil.validateAccessToken(tamperedToken), "被篡改的Token应验证失败");

        // 测试4：使用错误密钥签名的Token
        String wrongSecretToken = Jwts.builder()
                .subject(TEST_USERNAME)
                .claim("userId", TEST_USER_ID)
                .claim("role", TEST_ROLE)
                .claim("tokenType", JwtUtil.TOKEN_TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor("wrong-secret-key-for-testing-12345678901234567890".getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertFalse(jwtUtil.validateAccessToken(wrongSecretToken), "使用错误密钥签名的Token应验证失败");
    }

    /**
     * 测试null输入的处理
     * 验证：传入null时不抛出异常，返回null或false
     */
    @Test
    @DisplayName("处理null输入 - 不应抛出异常")
    void testNullInput() {
        assertNull(jwtUtil.extractUsername(null), "null Token提取用户名应返回null");
        assertNull(jwtUtil.extractUserId(null), "null Token提取用户ID应返回null");
        assertNull(jwtUtil.extractRole(null), "null Token提取角色应返回null");
        assertNull(jwtUtil.extractTokenType(null), "null Token提取类型应返回null");
        assertNull(jwtUtil.extractExpiration(null), "null Token提取过期时间应返回null");
        assertTrue(jwtUtil.isTokenExpired(null), "null Token应被视为已过期");
        assertFalse(jwtUtil.validateAccessToken(null), "null Token验证应返回false");
        assertFalse(jwtUtil.validateRefreshToken(null), "null Token验证应返回false");
        assertNull(jwtUtil.refreshTokens(null), "null Token刷新应返回null");
        assertNull(jwtUtil.refreshAccessToken(null), "null Token刷新应返回null");
    }

    /**
     * 测试refreshTokens方法（刷新Token对）
     * 验证：使用有效的Refresh Token可以刷新出一对新的Token
     */
    @Test
    @DisplayName("刷新Token对 - 使用Refresh Token获取新Token对")
    void testRefreshTokens() {
        // 先生成一个Refresh Token
        String refreshToken = jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);

        // 执行刷新
        Map<String, String> tokens = jwtUtil.refreshTokens(refreshToken);

        // 验证：返回结果不为空
        assertNotNull(tokens, "刷新Token应返回结果");
        assertTrue(tokens.containsKey("accessToken"), "返回结果应包含accessToken");
        assertTrue(tokens.containsKey("refreshToken"), "返回结果应包含refreshToken");

        // 验证：新Token都有效
        String newAccessToken = tokens.get("accessToken");
        String newRefreshToken = tokens.get("refreshToken");
        assertTrue(jwtUtil.validateAccessToken(newAccessToken), "新生成的Access Token应有效");
        assertTrue(jwtUtil.validateRefreshToken(newRefreshToken), "新生成的Refresh Token应有效");

        // 验证：新Token包含正确的用户信息
        assertEquals(TEST_USERNAME, jwtUtil.extractUsername(newAccessToken), "新Access Token用户信息正确");
        assertEquals(TEST_USER_ID, jwtUtil.extractUserId(newAccessToken), "新Access Token用户ID正确");
    }

    /**
     * 测试refreshAccessToken方法（仅刷新Access Token）
     * 验证：使用有效的Refresh Token可以刷新出新的Access Token
     */
    @Test
    @DisplayName("刷新Access Token - 使用Refresh Token获取新Access Token")
    void testRefreshAccessToken() {
        // 先生成一个Refresh Token
        String refreshToken = jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);

        // 执行刷新
        String newAccessToken = jwtUtil.refreshAccessToken(refreshToken);

        // 验证：返回结果不为空
        assertNotNull(newAccessToken, "刷新Access Token应返回结果");

        // 验证：新Token有效
        assertTrue(jwtUtil.validateAccessToken(newAccessToken), "新生成的Access Token应有效");

        // 验证：新Token包含正确的用户信息
        assertEquals(TEST_USERNAME, jwtUtil.extractUsername(newAccessToken), "新Access Token用户名正确");
        assertEquals(TEST_USER_ID, jwtUtil.extractUserId(newAccessToken), "新Access Token用户ID正确");
        assertEquals(TEST_ROLE, jwtUtil.extractRole(newAccessToken), "新Access Token角色正确");
    }

    /**
     * 测试使用无效Token刷新
     * 验证：无效Token无法用于刷新
     */
    @Test
    @DisplayName("刷新Token失败 - 无效Token无法刷新")
    void testRefreshTokens_InvalidToken() {
        // 使用Access Token尝试刷新（应该失败，因为需要Refresh Token）
        String accessToken = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        assertNull(jwtUtil.refreshTokens(accessToken), "Access Token不应能用于刷新Token对");
        assertNull(jwtUtil.refreshAccessToken(accessToken), "Access Token不应能用于刷新Access Token");

        // 使用过期Token尝试刷新
        String expiredToken = Jwts.builder()
                .subject(TEST_USERNAME)
                .claim("userId", TEST_USER_ID)
                .claim("role", TEST_ROLE)
                .claim("tokenType", JwtUtil.TOKEN_TYPE_REFRESH)
                .issuedAt(new Date(System.currentTimeMillis() - 86400000 * 2))
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertNull(jwtUtil.refreshTokens(expiredToken), "过期的Refresh Token不应能刷新");
    }

    /**
     * 测试isTokenExpiredException方法（区分过期异常和其他异常）
     * 验证：正确识别Token是否因过期而无效
     */
    @Test
    @DisplayName("判断Token过期异常 - 区分过期和其他错误")
    void testIsTokenExpiredException() {
        // 有效Token不应返回true
        String validToken = jwtUtil.generateAccessToken(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        assertFalse(jwtUtil.isTokenExpiredException(validToken), "有效Token不应判定为过期异常");

        // 已过期Token应返回true
        String expiredToken = Jwts.builder()
                .subject(TEST_USERNAME)
                .claim("userId", TEST_USER_ID)
                .claim("role", TEST_ROLE)
                .claim("tokenType", JwtUtil.TOKEN_TYPE_ACCESS)
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertTrue(jwtUtil.isTokenExpiredException(expiredToken), "过期Token应判定为过期异常");

        // 格式错误Token应返回false（不是过期问题）
        assertFalse(jwtUtil.isTokenExpiredException("invalid"), "无效Token不应判定为过期异常");
    }

    /**
     * 测试extractClaimsIgnoringExpiration方法（过期Token仍能提取Claims）
     * 验证：即使Token已过期，仍能从中提取Claims信息
     */
    @Test
    @DisplayName("忽略过期提取Claims - 过期Token仍能解析Claims")
    void testExtractClaimsIgnoringExpiration() {
        // 构造一个已过期但签名有效的Token
        String expiredToken = Jwts.builder()
                .subject(TEST_USERNAME)
                .claim("userId", TEST_USER_ID)
                .claim("role", TEST_ROLE)
                .claim("tokenType", JwtUtil.TOKEN_TYPE_ACCESS)
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        // 即使Token过期，仍能提取Claims
        Claims claims = jwtUtil.extractClaimsIgnoringExpiration(expiredToken);
        assertNotNull(claims, "过期Token应仍能提取Claims");
        assertEquals(TEST_USERNAME, claims.getSubject(), "提取的用户名应正确");
        assertEquals(TEST_USER_ID, claims.get("userId", Long.class), "提取的用户ID应正确");
        assertEquals(TEST_ROLE, claims.get("role", String.class), "提取的角色应正确");
    }

    /**
     * 测试不同角色用户的Token生成
     * 验证：不同角色（admin/user）的Token都能正确生成和验证
     */
    @Test
    @DisplayName("不同角色Token生成 - admin/user角色均正常")
    void testDifferentRoles() {
        // 测试管理员角色
        String adminToken = jwtUtil.generateAccessToken(2L, "admin", "admin");
        assertEquals("admin", jwtUtil.extractRole(adminToken), "管理员角色应正确提取");
        assertTrue(jwtUtil.validateAccessToken(adminToken), "管理员Token应验证通过");

        // 测试普通用户角色
        String userToken = jwtUtil.generateAccessToken(3L, "normaluser", "user");
        assertEquals("user", jwtUtil.extractRole(userToken), "普通用户角色应正确提取");
        assertTrue(jwtUtil.validateAccessToken(userToken), "普通用户Token应验证通过");
    }
}
