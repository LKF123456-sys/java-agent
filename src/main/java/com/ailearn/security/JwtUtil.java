package com.ailearn.security; // 声明包名，security包存放认证授权相关类

import io.jsonwebtoken.Claims; // JWT声明（payload）接口，承载Token中的自定义数据
import io.jsonwebtoken.ExpiredJwtException; // Token过期异常
import io.jsonwebtoken.Jwts; // jjwt入口类，提供builder()/parser()等核心方法
import io.jsonwebtoken.MalformedJwtException; // JWT格式错误异常
import io.jsonwebtoken.UnsupportedJwtException; // 不支持的JWT异常
import io.jsonwebtoken.security.Keys; // 密钥工具类，用于生成HMAC密钥
import io.jsonwebtoken.security.SecurityException; // 签名验证失败异常
import lombok.extern.slf4j.Slf4j; // Lombok日志注解
import org.springframework.beans.factory.annotation.Value; // Spring属性注入注解
import org.springframework.stereotype.Component; // Spring组件注解

import javax.crypto.SecretKey; // 对称密钥接口（HMAC用）
import java.nio.charset.StandardCharsets; // 字符集常量（UTF-8）
import java.util.Date; // 日期类，表示时间点
import java.util.HashMap; // 哈希表实现
import java.util.Map; // Map接口

/**
 * JWT工具类
 * 实现双Token机制：access_token（短期2小时）+ refresh_token（长期7天）
 * 支持Token生成、验证、解析Claims、刷新Token等功能
 * 使用jjwt 0.13.0版本API（与0.12完全兼容）
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log
@Component // 注册为Spring Bean
public class JwtUtil { // JWT工具类定义

    /**
     * Token类型常量：访问令牌
     */
    public static final String TOKEN_TYPE_ACCESS = "access"; // 访问令牌类型标识

    /**
     * Token类型常量：刷新令牌
     */
    public static final String TOKEN_TYPE_REFRESH = "refresh"; // 刷新令牌类型标识

    /**
     * JWT密钥
     * 从配置文件jwt.secret读取，默认值为cyber-ai-platform-secret-key-for-jwt-token-generation-please-change-in-production
     * 生产环境务必修改为强密钥
     */
    @Value("${jwt.secret:cyber-ai-platform-secret-key-for-jwt-token-generation-please-change-in-production}") // 从yml注入，冒号后为默认值
    private String secretKey; // 密钥字符串

    /**
     * Access Token过期时间（毫秒）
     * 从配置文件jwt.access-token-expiration读取，默认7200000毫秒（2小时）
     */
    @Value("${jwt.access-token-expiration:7200000}") // 默认2小时
    private Long accessTokenExpiration; // 访问令牌过期毫秒数

    /**
     * Refresh Token过期时间（毫秒）
     * 从配置文件jwt.refresh-token-expiration读取，默认604800000毫秒（7天）
     */
    @Value("${jwt.refresh-token-expiration:604800000}") // 默认7天
    private Long refreshTokenExpiration; // 刷新令牌过期毫秒数

    /**
     * 获取JWT签名密钥
     * 使用HMAC-SHA算法，将配置的密钥字符串转换为SecretKey对象
     *
     * @return SecretKey JWT签名密钥对象
     */
    private SecretKey getSigningKey() { // 私有方法，每次调用都从密钥字符串生成
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8); // 密钥字符串转字节数组（UTF-8）
        return Keys.hmacShaKeyFor(keyBytes); // 用jjwt工具生成HMAC-SHA密钥
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
    public String generateAccessToken(Long userId, String username, String role) { // 生成访问令牌
        Map<String, Object> claims = new HashMap<>(); // 创建自定义声明Map
        claims.put("userId", userId); // 放入用户ID
        claims.put("role", role); // 放入角色
        claims.put("tokenType", TOKEN_TYPE_ACCESS); // 放入令牌类型=access
        return buildToken(claims, username, accessTokenExpiration); // 委托buildToken构建
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
    public String generateRefreshToken(Long userId, String username, String role) { // 生成刷新令牌
        Map<String, Object> claims = new HashMap<>(); // 创建声明Map
        claims.put("userId", userId); // 用户ID
        claims.put("role", role); // 角色
        claims.put("tokenType", TOKEN_TYPE_REFRESH); // 令牌类型=refresh
        return buildToken(claims, username, refreshTokenExpiration); // 委托buildToken构建
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
    private String buildToken(Map<String, Object> claims, String subject, Long expirationMillis) { // 核心构建方法
        Date now = new Date(); // 当前时间
        Date expiryDate = new Date(now.getTime() + expirationMillis); // 过期时间=现在+有效期

        return Jwts.builder() // 启动JWT构建器
                .claims(claims) // 设置自定义声明
                .subject(subject) // 设置主题（用户名）
                .issuedAt(now) // 设置签发时间
                .expiration(expiryDate) // 设置过期时间
                .signWith(getSigningKey()) // 用HMAC密钥签名
                .compact(); // 压缩为字符串形式返回
    }

    /**
     * 从Token中提取用户名（Subject）
     *
     * @param token JWT Token字符串
     * @return String 用户名，如果解析失败返回null
     */
    public String extractUsername(String token) { // 提取用户名
        try { // 尝试解析
            return extractAllClaims(token).getSubject(); // 取Subject字段
        } catch (Exception e) { // 解析失败
            log.error("提取用户名失败: {}", e.getMessage()); // 错误日志
            return null; // 返回null
        }
    }

    /**
     * 从Token中提取用户ID
     *
     * @param token JWT Token字符串
     * @return Long 用户ID，如果解析失败返回null
     */
    public Long extractUserId(String token) { // 提取用户ID
        try { // 尝试解析
            return extractAllClaims(token).get("userId", Long.class); // 取userId声明并转Long
        } catch (Exception e) { // 失败
            log.error("提取用户ID失败: {}", e.getMessage()); // 错误日志
            return null; // 返回null
        }
    }

    /**
     * 从Token中提取用户角色
     *
     * @param token JWT Token字符串
     * @return String 用户角色，如果解析失败返回null
     */
    public String extractRole(String token) { // 提取角色
        try { // 尝试
            return extractAllClaims(token).get("role", String.class); // 取role声明
        } catch (Exception e) { // 失败
            log.error("提取用户角色失败: {}", e.getMessage()); // 错误日志
            return null; // 返回null
        }
    }

    /**
     * 从Token中提取Token类型（access/refresh）
     *
     * @param token JWT Token字符串
     * @return String Token类型，如果解析失败返回null
     */
    public String extractTokenType(String token) { // 提取令牌类型
        try { // 尝试
            return extractAllClaims(token).get("tokenType", String.class); // 取tokenType声明
        } catch (Exception e) { // 失败
            log.error("提取Token类型失败: {}", e.getMessage()); // 错误日志
            return null; // 返回null
        }
    }

    /**
     * 从Token中提取过期时间
     *
     * @param token JWT Token字符串
     * @return Date Token过期时间，如果解析失败返回null
     */
    public Date extractExpiration(String token) { // 提取过期时间
        try { // 尝试
            return extractAllClaims(token).getExpiration(); // 取过期时间字段
        } catch (Exception e) { // 失败
            log.error("提取过期时间失败: {}", e.getMessage()); // 错误日志
            return null; // 返回null
        }
    }

    /**
     * 解析Token并提取所有Claims（声明）
     * 使用jjwt 0.13.0版本API：Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
     *
     * @param token JWT Token字符串
     * @return Claims 解析后的Claims对象
     * @throws ExpiredJwtException      Token已过期异常
     * @throws UnsupportedJwtException  不支持的JWT异常
     * @throws MalformedJwtException    JWT格式错误异常
     * @throws SecurityException        签名验证失败异常
     * @throws IllegalArgumentException 非法参数异常
     */
    private Claims extractAllClaims(String token) { // 核心解析方法
        return Jwts.parser() // 创建解析器
                .verifyWith(getSigningKey()) // 设置验签密钥（与签发密钥相同）
                .build() // 构建解析器
                .parseSignedClaims(token) // 解析并验签，返回Jws对象
                .getPayload(); // 取payload（Claims）
    }

    /**
     * 判断Token是否已过期
     *
     * @param token JWT Token字符串
     * @return Boolean true表示已过期，false表示未过期或解析失败（视为已过期）
     */
    public Boolean isTokenExpired(String token) { // 判断是否过期
        try { // 尝试
            Date expiration = extractExpiration(token); // 取过期时间
            return expiration == null || expiration.before(new Date()); // 为null或在当前时间之前→过期
        } catch (ExpiredJwtException e) { // 已过期异常
            log.debug("Token已过期: {}", e.getMessage()); // 调试日志
            return true; // 确认过期
        } catch (Exception e) { // 其他异常
            log.error("判断Token是否过期时发生异常: {}", e.getMessage()); // 错误日志
            return true; // 异常时保守视为过期
        }
    }

    /**
     * 验证Token是否有效（签名正确、未过期、类型正确）
     *
     * @param token     JWT Token字符串
     * @param tokenType 期望的Token类型（access或refresh）
     * @return Boolean true表示有效，false表示无效
     */
    public Boolean validateToken(String token, String tokenType) { // 验证Token
        try { // 尝试验证
            Claims claims = extractAllClaims(token); // 解析全部声明
            String extractedType = claims.get("tokenType", String.class); // 取实际类型
            boolean isNotExpired = !claims.getExpiration().before(new Date()); // 未过期
            boolean typeMatch = tokenType.equals(extractedType); // 类型匹配
            return isNotExpired && typeMatch; // 两者都满足才有效
        } catch (ExpiredJwtException e) { // 过期
            log.debug("Token已过期: {}", e.getMessage()); // 调试日志
            return false; // 无效
        } catch (UnsupportedJwtException e) { // 不支持的JWT
            log.error("不支持的JWT: {}", e.getMessage()); // 错误日志
            return false; // 无效
        } catch (MalformedJwtException e) { // 格式错误
            log.error("JWT格式错误: {}", e.getMessage()); // 错误日志
            return false; // 无效
        } catch (SecurityException e) { // 签名失败
            log.error("JWT签名验证失败: {}", e.getMessage()); // 错误日志
            return false; // 无效
        } catch (IllegalArgumentException e) { // 参数非法
            log.error("JWT参数非法: {}", e.getMessage()); // 错误日志
            return false; // 无效
        } catch (Exception e) { // 其他
            log.error("Token验证失败: {}", e.getMessage()); // 错误日志
            return false; // 无效
        }
    }

    /**
     * 验证Token是否为有效的Access Token
     *
     * @param token JWT Token字符串
     * @return Boolean true表示有效Access Token，false表示无效
     */
    public Boolean validateAccessToken(String token) { // 验证访问令牌
        return validateToken(token, TOKEN_TYPE_ACCESS); // 委托validateToken，期望类型=access
    }

    /**
     * 验证Token是否为有效的Refresh Token
     *
     * @param token JWT Token字符串
     * @return Boolean true表示有效Refresh Token，false表示无效
     */
    public Boolean validateRefreshToken(String token) { // 验证刷新令牌
        return validateToken(token, TOKEN_TYPE_REFRESH); // 委托validateToken，期望类型=refresh
    }

    /**
     * 使用Refresh Token刷新获取新的Token对（新Access Token + 新Refresh Token）
     * 验证Refresh Token有效后，使用其中的用户信息生成新的Token
     *
     * @param refreshToken 有效的Refresh Token
     * @return Map<String, String> 包含新的accessToken和refreshToken的Map，如果刷新失败返回null
     */
    public Map<String, String> refreshTokens(String refreshToken) { // 刷新令牌对
        if (!validateRefreshToken(refreshToken)) { // 刷新令牌无效
            log.error("刷新Token失败：Refresh Token无效"); // 错误日志
            return null; // 返回null
        }

        try { // 尝试刷新
            Long userId = extractUserId(refreshToken); // 提取用户ID
            String username = extractUsername(refreshToken); // 提取用户名
            String role = extractRole(refreshToken); // 提取角色

            if (userId == null || username == null || role == null) { // 信息不完整
                log.error("刷新Token失败：无法从Refresh Token中提取用户信息"); // 错误日志
                return null; // 返回null
            }

            Map<String, String> tokens = new HashMap<>(); // 创建结果Map
            tokens.put("accessToken", generateAccessToken(userId, username, role)); // 生成新访问令牌
            tokens.put("refreshToken", generateRefreshToken(userId, username, role)); // 生成新刷新令牌
            return tokens; // 返回令牌对
        } catch (Exception e) { // 异常
            log.error("刷新Token时发生异常: {}", e.getMessage()); // 错误日志
            return null; // 返回null
        }
    }

    /**
     * 仅刷新Access Token（不刷新Refresh Token）
     * 适用于Refresh Token仍然有效但Access Token过期的场景
     *
     * @param refreshToken 有效的Refresh Token
     * @return String 新的Access Token，如果刷新失败返回null
     */
    public String refreshAccessToken(String refreshToken) { // 仅刷新访问令牌
        if (!validateRefreshToken(refreshToken)) { // 刷新令牌无效
            log.error("刷新Access Token失败：Refresh Token无效"); // 错误日志
            return null; // 返回null
        }

        try { // 尝试
            Long userId = extractUserId(refreshToken); // 提取用户ID
            String username = extractUsername(refreshToken); // 提取用户名
            String role = extractRole(refreshToken); // 提取角色

            if (userId == null || username == null || role == null) { // 信息不完整
                log.error("刷新Access Token失败：无法从Refresh Token中提取用户信息"); // 错误日志
                return null; // 返回null
            }

            return generateAccessToken(userId, username, role); // 生成并返回新访问令牌
        } catch (Exception e) { // 异常
            log.error("刷新Access Token时发生异常: {}", e.getMessage()); // 错误日志
            return null; // 返回null
        }
    }

    /**
     * 判断Token是否因过期而无效（区分过期和其他错误）
     * 用于JwtAuthenticationFilter中判断是否需要返回401让前端刷新Token
     *
     * @param token JWT Token字符串
     * @return Boolean true表示Token已过期，false表示Token无效但不是因为过期（或解析成功未过期）
     */
    public Boolean isTokenExpiredException(String token) { // 判断是否因过期而无效
        try { // 尝试解析
            extractAllClaims(token); // 解析（能解析说明未过期）
            return false; // 未过期
        } catch (ExpiredJwtException e) { // 过期异常
            return true; // 是过期
        } catch (Exception e) { // 其他异常
            return false; // 非过期原因
        }
    }

    /**
     * 安全地解析Token中的Claims，即使Token已过期也能解析
     * 用于Token过期时仍能提取其中的用户信息（如判断Token类型）
     *
     * @param token JWT Token字符串
     * @return Claims 解析后的Claims对象，如果完全无法解析返回null
     */
    public Claims extractClaimsIgnoringExpiration(String token) { // 忽略过期解析声明
        try { // 尝试
            return extractAllClaims(token); // 正常解析
        } catch (ExpiredJwtException e) { // 过期异常
            log.debug("Token已过期，但仍提取Claims: {}", e.getMessage()); // 调试日志
            return e.getClaims(); // ExpiredJwtException仍携带过期前的Claims
        } catch (Exception e) { // 其他异常
            log.error("提取Claims失败: {}", e.getMessage()); // 错误日志
            return null; // 返回null
        }
    }
} // JwtUtil类结束
