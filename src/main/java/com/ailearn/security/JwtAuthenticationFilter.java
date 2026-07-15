package com.ailearn.security;

import com.ailearn.common.ErrorCode;
import com.ailearn.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
 * 继承OncePerRequestFilter确保每个请求只执行一次过滤
 * 负责从HTTP请求中提取JWT Token、验证Token有效性、设置Spring Security认证上下文
 *
 * 过滤流程：
 * 1. 白名单路径直接放行（登录接口、Swagger、静态资源等）
 * 2. 非REQUEST类型dispatch（ASYNC/FORWARD/ERROR/INCLUDE）恢复已保存的认证信息
 * 3. 从Authorization头提取Bearer Token
 * 4. 解析Token并验证类型必须为access token
 * 5. 验证Token签名和过期时间，有效则设置SecurityContext
 * 6. Token过期时返回401提示前端刷新Token，无效Token继续过滤链（由后续权限控制处理）
 *
 * 关键设计：
 * - 支持异步请求（SSE流式响应）的SecurityContext恢复
 * - 白名单路径匹配使用AntPathMatcher，支持通配符
 * - 区分Token过期和Token无效两种场景，过期返回明确提示
 * - Token解析失败不直接返回401，而是继续过滤链，由Spring Security权限控制处理
 *
 * @author AiLearn Platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 请求属性键：用于在异步dispatch中保存已认证的Authentication对象
     * ASYNC dispatch时会新建SecurityContext，需要从request attribute中恢复
     */
    private static final String SAVED_AUTH_ATTR = "JWT_SAVED_AUTHENTICATION";

    /**
     * JWT工具类
     * 用于Token解析、验证、提取Claims等操作
     */
    private final JwtUtil jwtUtil;

    /**
     * JSON对象映射器
     * 用于将Result错误响应序列化为JSON字符串写入HTTP响应
     */
    private final ObjectMapper objectMapper;

    /**
     * Ant风格路径匹配器
     * 用于匹配白名单路径，支持/**、?等通配符模式
     */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 白名单路径列表
     * 这些路径不需要JWT认证，直接放行
     *
     * 包含：
     * - /api/auth/**：认证相关接口（登录、注册、刷新Token等）
     * - /api/health、/api/：健康检查和根路径
     * - /error：Spring Boot错误端点
     * - /actuator/**：Spring Boot监控端点
     * - /swagger-ui/**、/v3/api-docs/**、/doc.html、/webjars/**：API文档相关资源
     * - /、/index.html、/favicon.ico、/assets/**：前端静态资源
     * - /mcp/**：MCP协议端点
     */
    private static final List<String> WHITE_LIST_PATHS = Arrays.asList(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/health",
            "/api/",
            "/error",
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
     * 是否在异步dispatch中不执行过滤
     * 返回false表示异步dispatch（如SSE流式响应）也需要经过此过滤器，
     * 以便在ASYNC dispatch中恢复SecurityContext，保证流式响应中能获取到用户信息
     *
     * @return boolean false表示异步dispatch也要过滤
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * 执行JWT认证过滤的核心方法
     * Each请求只会执行一次，由OncePerRequestFilter保证
     *
     * 处理逻辑：
     * 1. 获取请求URI和dispatch类型
     * 2. 白名单路径直接放行
     * 3. 非REQUEST dispatch恢复认证信息
     * 4. 提取并验证JWT Token
     * 5. 设置SecurityContext或返回错误
     *
     * @param request     HTTP请求对象
     * @param response    HTTP响应对象
     * @param filterChain 过滤器链，用于传递请求到下一个过滤器
     * @throws ServletException Servlet异常
     * @throws IOException      IO异常（写入响应时可能抛出）
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 获取请求URI，用于白名单匹配和日志记录
        String requestPath = request.getRequestURI();
        // 获取请求分发类型：REQUEST(首次请求)、ASYNC(异步)、FORWARD(转发)、ERROR(错误)、INCLUDE(包含)
        DispatcherType dispatcherType = request.getDispatcherType();
        log.debug("JWT过滤器处理请求: {} (dispatchType={})", requestPath, dispatcherType);

        // 白名单路径直接放行，不做JWT认证
        if (isWhiteListPath(requestPath)) {
            log.debug("请求路径在白名单中，直接放行: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        // 非首次REQUEST dispatch（ASYNC/FORWARD/ERROR/INCLUDE）的处理
        // 主要用于支持SSE流式响应等异步场景，需要恢复之前保存的认证信息
        if (dispatcherType != DispatcherType.REQUEST) {
            // 尝试从request attribute中获取之前REQUEST阶段保存的Authentication
            Authentication savedAuth = (Authentication) request.getAttribute(SAVED_AUTH_ATTR);
            if (savedAuth != null) {
                // 如果当前SecurityContext中没有认证信息，则恢复保存的认证信息
                // 异步dispatch时SecurityContext可能是新创建的空context
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    log.debug("{} dispatch中恢复SecurityContext: userId={}",
                        dispatcherType, ((UserPrincipal) savedAuth.getPrincipal()).getUserId());
                    SecurityContextHolder.getContext().setAuthentication(savedAuth);
                }
            } else if (dispatcherType == DispatcherType.ASYNC) {
                // ASYNC dispatch中未找到保存的认证信息，记录调试日志
                log.debug("ASYNC dispatch中未找到保存的Authentication，uri={}", requestPath);
            }
            // 继续过滤链，非REQUEST dispatch不重复解析Token
            filterChain.doFilter(request, response);
            return;
        }

        // ========== 以下是首次REQUEST dispatch的处理 ==========

        // 从HTTP请求的Authorization头中提取JWT Token
        String jwt = getJwtFromRequest(request);

        // 请求中未携带Token，继续过滤链（后续权限控制会决定是否返回403）
        if (!StringUtils.hasText(jwt)) {
            log.debug("请求中未携带JWT Token，继续过滤链");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 解析Token中的Claims，即使Token过期也尝试解析（用于判断Token类型）
            // 这样可以区分"过期的access token"和"无效的token/refresh token"
            Claims claims = jwtUtil.extractClaimsIgnoringExpiration(jwt);

            // Token完全无法解析（格式错误、签名错误等），继续过滤链
            if (claims == null) {
                log.debug("无法解析Token，继续过滤链");
                filterChain.doFilter(request, response);
                return;
            }

            // 从Claims中提取用户信息和Token类型
            String tokenType = claims.get("tokenType", String.class);
            Long userId = claims.get("userId", Long.class);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            // Token类型不是access token（可能是refresh token），不设置认证
            // refresh token只用于/auth/refresh接口，其他接口不接受
            if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                log.debug("Token类型不是Access Token（当前类型：{}），继续过滤链", tokenType);
                filterChain.doFilter(request, response);
                return;
            }

            // 完整验证access token的有效性（签名正确、未过期）
            if (jwtUtil.validateAccessToken(jwt)) {
                // Token验证成功，构建用户认证主体
                log.debug("Access Token验证成功，设置SecurityContext: userId={}, username={}, role={}", userId, username, role);
                UserPrincipal userPrincipal = UserPrincipal.create(userId, username, role);

                // 创建Spring Security认证令牌
                // 参数：用户主体、凭证（密码，JWT认证时为null）、权限列表
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal,
                        null,
                        userPrincipal.getAuthorities()
                );
                // 设置请求详情（如IP地址、Session ID等）
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 将认证信息设置到SecurityContextHolder，后续Controller/Service可通过
                // SecurityContextHolder.getContext().getAuthentication()获取当前用户
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 将认证信息保存到request attribute，供后续ASYNC等dispatch恢复使用
                request.setAttribute(SAVED_AUTH_ATTR, authentication);

                // 继续过滤链
                filterChain.doFilter(request, response);
            } else {
                // Token验证失败，区分是过期还是其他原因
                boolean isExpired = jwtUtil.isTokenExpiredException(jwt);
                if (isExpired) {
                    // Token已过期，返回401状态码和明确提示，让前端知道需要用refresh token刷新
                    log.debug("Access Token已过期，返回401让前端刷新Token");
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                            ErrorCode.AUTH_TOKEN_INVALID.getCode(),
                            "Access Token已过期，请使用Refresh Token刷新");
                } else {
                    // Token无效（签名错误、格式错误等），继续过滤链
                    // 由Spring Security的权限控制返回403，避免信息泄露
                    log.debug("Access Token无效，继续过滤链");
                    filterChain.doFilter(request, response);
                }
            }
        } catch (Exception e) {
            // 捕获所有异常，确保过滤器不会因为异常导致请求中断
            // 记录错误日志后继续过滤链，由后续安全机制处理
            log.error("JWT认证过程中发生异常: {}", e.getMessage(), e);
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 判断请求路径是否在白名单中
     * 使用AntPathMatcher进行路径匹配，支持/**、?等Ant风格通配符
     *
     * @param requestPath 请求URI路径
     * @return boolean true表示在白名单中，false表示需要认证
     */
    private boolean isWhiteListPath(String requestPath) {
        return WHITE_LIST_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestPath));
    }

    /**
     * 从HTTP请求中提取JWT Token
     * 从Authorization请求头中提取Bearer Token格式的JWT
     * Authorization头格式：Bearer {jwt-token}
     *
     * @param request HTTP请求对象
     * @return String JWT Token字符串，如果未找到或格式错误返回null
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        // 获取Authorization请求头
        String bearerToken = request.getHeader("Authorization");
        // 检查Authorization头是否存在且以"Bearer "开头
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // "Bearer "前缀长度为7，截取后面的部分作为Token
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 发送JSON格式的错误响应
     * 将Result对象序列化为JSON写入HTTP响应，设置正确的Content-Type和字符编码
     *
     * @param response   HTTP响应对象
     * @param httpStatus HTTP状态码（如401 Unauthorized）
     * @param code       业务错误码
     * @param message    错误提示消息
     * @throws IOException 写入响应时可能抛出的IO异常
     */
    private void sendErrorResponse(HttpServletResponse response, int httpStatus, int code, String message) throws IOException {
        // 设置HTTP状态码
        response.setStatus(httpStatus);
        // 设置响应内容类型为JSON
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 设置字符编码为UTF-8，防止中文乱码
        response.setCharacterEncoding("UTF-8");

        // 构建标准错误响应对象
        Result<Void> result = Result.error(code, message);
        // 将Result对象序列化为JSON字符串
        String jsonResponse = objectMapper.writeValueAsString(result);
        // 写入响应体
        response.getWriter().write(jsonResponse);
        // 刷新输出流，确保响应立即发送
        response.getWriter().flush();
    }
}
