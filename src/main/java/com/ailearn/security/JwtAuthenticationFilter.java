package com.ailearn.security;

import com.ailearn.common.ErrorCode;
import com.ailearn.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JWT认证过滤器
 * 继承OncePerRequestFilter确保每个请求只执行一次
 * 负责从HTTP请求中提取JWT Token，验证Token有效性，并设置Spring Security上下文
 * 白名单路径直接放行，不做Token验证
 * 当Access Token过期时，返回401状态码让前端使用Refresh Token刷新
 *
 * @author AiLearn Platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * JWT工具类
     */
    private final JwtUtil jwtUtil;

    /**
     * JSON对象映射器
     * 用于将Result对象序列化为JSON字符串返回给前端
     */
    private final ObjectMapper objectMapper;

    /**
     * 路径匹配器
     * 用于匹配白名单路径（支持Ant风格路径匹配）
     */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 白名单路径列表
     * 这些路径不需要JWT认证，直接放行
     * 包含：认证接口、监控端点、Swagger文档、静态资源、MCP接口等
     */
    private static final List<String> WHITE_LIST_PATHS = Arrays.asList(
            "/api/auth/**",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/doc.html",
            "/webjars/**",
            "/",
            "/index.html",
            "/favicon.ico",
            "/assets/**",
            "/mcp/**"
    );

    /**
     * 过滤器核心方法
     * 每个HTTP请求都会经过此方法处理
     * 处理流程：
     * 1. 判断请求路径是否在白名单中，如果是直接放行
     * 2. 从Authorization请求头中提取JWT Token
     * 3. 如果没有Token，继续过滤链（由后续Security配置处理）
     * 4. 解析Token中的Claims（即使过期也尝试提取，用于判断Token类型）
     * 5. 如果是Access Token且有效，设置SecurityContext认证信息
     * 6. 如果Access Token已过期，返回401状态码和错误信息，提示前端刷新Token
     * 7. 如果是其他情况（Token无效、Refresh Token等），继续过滤链
     *
     * @param request     HTTP请求对象
     * @param response    HTTP响应对象
     * @param filterChain 过滤器链
     * @throws ServletException Servlet异常
     * @throws IOException      IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        log.debug("JWT过滤器处理请求: {}", requestPath);

        if (isWhiteListPath(requestPath)) {
            log.debug("请求路径在白名单中，直接放行: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = getJwtFromRequest(request);

        if (!StringUtils.hasText(jwt)) {
            log.debug("请求中未携带JWT Token，继续过滤链");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtUtil.extractClaimsIgnoringExpiration(jwt);

            if (claims == null) {
                log.debug("无法解析Token，继续过滤链");
                filterChain.doFilter(request, response);
                return;
            }

            String tokenType = claims.get("tokenType", String.class);
            Long userId = claims.get("userId", Long.class);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                log.debug("Token类型不是Access Token（当前类型：{}），继续过滤链", tokenType);
                filterChain.doFilter(request, response);
                return;
            }

            if (jwtUtil.validateAccessToken(jwt)) {
                log.debug("Access Token验证成功，设置SecurityContext: userId={}, username={}, role={}", userId, username, role);
                UserPrincipal userPrincipal = UserPrincipal.create(userId, username, role);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal,
                        null,
                        userPrincipal.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                filterChain.doFilter(request, response);
            } else {
                boolean isExpired = jwtUtil.isTokenExpiredException(jwt);
                if (isExpired) {
                    log.debug("Access Token已过期，返回401让前端刷新Token");
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                            ErrorCode.AUTH_TOKEN_INVALID.getCode(),
                            "Access Token已过期，请使用Refresh Token刷新");
                } else {
                    log.debug("Access Token无效，继续过滤链");
                    filterChain.doFilter(request, response);
                }
            }
        } catch (Exception e) {
            log.error("JWT认证过程中发生异常: {}", e.getMessage(), e);
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 判断请求路径是否在白名单中
     * 使用AntPathMatcher进行路径匹配，支持通配符
     *
     * @param requestPath 请求路径
     * @return boolean true表示在白名单中，false表示不在
     */
    private boolean isWhiteListPath(String requestPath) {
        return WHITE_LIST_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestPath));
    }

    /**
     * 从HTTP请求中提取JWT Token
     * 从Authorization请求头中获取Bearer Token，格式为：Bearer <token>
     *
     * @param request HTTP请求对象
     * @return String 提取到的JWT Token，如果没有则返回null
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 发送JSON格式的错误响应
     * 设置HTTP状态码、响应类型和编码，将Result对象序列化为JSON写入响应
     *
     * @param response   HTTP响应对象
     * @param httpStatus HTTP状态码
     * @param code       业务错误码
     * @param message    错误消息
     * @throws IOException IO异常（写入响应时可能抛出）
     */
    private void sendErrorResponse(HttpServletResponse response, int httpStatus, int code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Result<Void> result = Result.error(code, message);
        String jsonResponse = objectMapper.writeValueAsString(result);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
