package com.ailearn.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 实现双Token机制：access_token（短期2小时）+ refresh_token（长期7天）
 * 支持Token生成、验证、解析Claims、刷新Token等功能
 * 使用jjwt 0.12.6版本API
 *
 * @author AiLearn Platform
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * Token类型常量：访问令牌
     */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /**
     * Token类型常量：刷新令牌
     */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /**
     * JWT密钥
     * 从配置文件jwt.secret读取，默认值为cyber-ai-platform-secret-key-for-jwt-token-generation-please-change-in-production
     * 生产环境务必修改为强密钥
     */
    @Value("${jwt.secret:cyber-ai-platform-secret-key-for-jwt-token-generation-please-change-in-production}")
    private String secretKey;

    /**
     * Access Token过期时间（毫秒）
     * 从配置文件jwt.access-token-expiration读取，默认7200000毫秒（2小时）
     */
    @Value("${jwt.access-token-expiration:7200000}")
    private Long accessTokenExpiration;

    /**
     * Refresh Token过期时间（毫秒）
     * 从配置文件jwt.refresh-token-expiration读取，默认604800000毫秒（7天）
     */
    @Value("${jwt.refresh-token-expiration:604800000}")
    private Long refreshTokenExpiration;

    /**
     * 获取JWT签名密钥
     * 使用HMAC-SHA算法，将配置的密钥字符串转换为SecretKey对象
     *
     * @return SecretKey JWT签名密钥对象
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成Access Token（访问令牌）
     * Access Token用于API访问认证，有效期2小时
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     用户角色
     * @return String 生成的Access Token字符串
     */
    public String generateAccessToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("tokenType", TOKEN_TYPE_ACCESS);
        return buildToken(claims, username, accessTokenExpiration);
    }

    /**
     * 生成Refresh Token（刷新令牌）
     * Refresh Token用于刷新Access Token，有效期7天
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     用户角色
     * @return String 生成的Refresh Token字符串
     */
    public String generateRefreshToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("tokenType", TOKEN_TYPE_REFRESH);
        return buildToken(claims, username, refreshTokenExpiration);
    }

    /**
     * 构建JWT Token的核心方法
     * 根据传入的Claims、主题和过期时间创建JWT
     *
     * @param claims           Token中携带的自定义声明信息
     * @param subject          Token主题（通常为用户名）
     * @param expirationMillis Token过期时间（毫秒）
     * @return String 构建完成的JWT Token字符串
     */
    private String buildToken(Map<String, Object> claims, String subject, Long expirationMillis) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从Token中提取用户名（Subject）
     *
     * @param token JWT Token字符串
     * @return String 用户名，如果解析失败返回null
     */
    public String extractUsername(String token) {
        try {
            return extractAllClaims(token).getSubject();
        } catch (Exception e) {
            log.error("提取用户名失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从Token中提取用户ID
     *
     * @param token JWT Token字符串
     * @return Long 用户ID，如果解析失败返回null
     */
    public Long extractUserId(String token) {
        try {
            return extractAllClaims(token).get("userId", Long.class);
        } catch (Exception e) {
            log.error("提取用户ID失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从Token中提取用户角色
     *
     * @param token JWT Token字符串
     * @return String 用户角色，如果解析失败返回null
     */
    public String extractRole(String token) {
        try {
            return extractAllClaims(token).get("role", String.class);
        } catch (Exception e) {
            log.error("提取用户角色失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从Token中提取Token类型（access/refresh）
     *
     * @param token JWT Token字符串
     * @return String Token类型，如果解析失败返回null
     */
    public String extractTokenType(String token) {
        try {
            return extractAllClaims(token).get("tokenType", String.class);
        } catch (Exception e) {
            log.error("提取Token类型失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从Token中提取过期时间
     *
     * @param token JWT Token字符串
     * @return Date Token过期时间，如果解析失败返回null
     */
    public Date extractExpiration(String token) {
        try {
            return extractAllClaims(token).getExpiration();
        } catch (Exception e) {
            log.error("提取过期时间失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析Token并提取所有Claims（声明）
     * 使用jjwt 0.12.6版本API：Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
     *
     * @param token JWT Token字符串
     * @return Claims 解析后的Claims对象
     * @throws ExpiredJwtException      Token已过期异常
     * @throws UnsupportedJwtException  不支持的JWT异常
     * @throws MalformedJwtException    JWT格式错误异常
     * @throws SecurityException        签名验证失败异常
     * @throws IllegalArgumentException 非法参数异常
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 判断Token是否已过期
     *
     * @param token JWT Token字符串
     * @return Boolean true表示已过期，false表示未过期或解析失败（视为已过期）
     */
    public Boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            return expiration == null || expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期: {}", e.getMessage());
            return true;
        } catch (Exception e) {
            log.error("判断Token是否过期时发生异常: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 验证Token是否有效（签名正确、未过期、类型正确）
     *
     * @param token     JWT Token字符串
     * @param tokenType 期望的Token类型（access或refresh）
     * @return Boolean true表示有效，false表示无效
     */
    public Boolean validateToken(String token, String tokenType) {
        try {
            Claims claims = extractAllClaims(token);
            String extractedType = claims.get("tokenType", String.class);
            boolean isNotExpired = !claims.getExpiration().before(new Date());
            boolean typeMatch = tokenType.equals(extractedType);
            return isNotExpired && typeMatch;
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            log.error("不支持的JWT: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.error("JWT格式错误: {}", e.getMessage());
            return false;
        } catch (SecurityException e) {
            log.error("JWT签名验证失败: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.error("JWT参数非法: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证Token是否为有效的Access Token
     *
     * @param token JWT Token字符串
     * @return Boolean true表示有效Access Token，false表示无效
     */
    public Boolean validateAccessToken(String token) {
        return validateToken(token, TOKEN_TYPE_ACCESS);
    }

    /**
     * 验证Token是否为有效的Refresh Token
     *
     * @param token JWT Token字符串
     * @return Boolean true表示有效Refresh Token，false表示无效
     */
    public Boolean validateRefreshToken(String token) {
        return validateToken(token, TOKEN_TYPE_REFRESH);
    }

    /**
     * 使用Refresh Token刷新获取新的Token对（新Access Token + 新Refresh Token）
     * 验证Refresh Token有效后，使用其中的用户信息生成新的Token
     *
     * @param refreshToken 有效的Refresh Token
     * @return Map<String, String> 包含新的accessToken和refreshToken的Map，如果刷新失败返回null
     */
    public Map<String, String> refreshTokens(String refreshToken) {
        if (!validateRefreshToken(refreshToken)) {
            log.error("刷新Token失败：Refresh Token无效");
            return null;
        }

        try {
            Long userId = extractUserId(refreshToken);
            String username = extractUsername(refreshToken);
            String role = extractRole(refreshToken);

            if (userId == null || username == null || role == null) {
                log.error("刷新Token失败：无法从Refresh Token中提取用户信息");
                return null;
            }

            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", generateAccessToken(userId, username, role));
            tokens.put("refreshToken", generateRefreshToken(userId, username, role));
            return tokens;
        } catch (Exception e) {
            log.error("刷新Token时发生异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 仅刷新Access Token（不刷新Refresh Token）
     * 适用于Refresh Token仍然有效但Access Token过期的场景
     *
     * @param refreshToken 有效的Refresh Token
     * @return String 新的Access Token，如果刷新失败返回null
     */
    public String refreshAccessToken(String refreshToken) {
        if (!validateRefreshToken(refreshToken)) {
            log.error("刷新Access Token失败：Refresh Token无效");
            return null;
        }

        try {
            Long userId = extractUserId(refreshToken);
            String username = extractUsername(refreshToken);
            String role = extractRole(refreshToken);

            if (userId == null || username == null || role == null) {
                log.error("刷新Access Token失败：无法从Refresh Token中提取用户信息");
                return null;
            }

            return generateAccessToken(userId, username, role);
        } catch (Exception e) {
            log.error("刷新Access Token时发生异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断Token是否因过期而无效（区分过期和其他错误）
     * 用于JwtAuthenticationFilter中判断是否需要返回401让前端刷新Token
     *
     * @param token JWT Token字符串
     * @return Boolean true表示Token已过期，false表示Token无效但不是因为过期（或解析成功未过期）
     */
    public Boolean isTokenExpiredException(String token) {
        try {
            extractAllClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 安全地解析Token中的Claims，即使Token已过期也能解析
     * 用于Token过期时仍能提取其中的用户信息（如判断Token类型）
     *
     * @param token JWT Token字符串
     * @return Claims 解析后的Claims对象，如果完全无法解析返回null
     */
    public Claims extractClaimsIgnoringExpiration(String token) {
        try {
            return extractAllClaims(token);
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期，但仍提取Claims: {}", e.getMessage());
            return e.getClaims();
        } catch (Exception e) {
            log.error("提取Claims失败: {}", e.getMessage());
            return null;
        }
    }
}
