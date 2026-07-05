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

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String SAVED_AUTH_ATTR = "JWT_SAVED_AUTHENTICATION";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> WHITE_LIST_PATHS = Arrays.asList(
            "/api/auth/**",
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

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        DispatcherType dispatcherType = request.getDispatcherType();
        log.debug("JWT过滤器处理请求: {} (dispatchType={})", requestPath, dispatcherType);

        if (isWhiteListPath(requestPath)) {
            log.debug("请求路径在白名单中，直接放行: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        if (dispatcherType != DispatcherType.REQUEST) {
            Authentication savedAuth = (Authentication) request.getAttribute(SAVED_AUTH_ATTR);
            if (savedAuth != null) {
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    log.debug("{} dispatch中恢复SecurityContext: userId={}", 
                        dispatcherType, ((UserPrincipal) savedAuth.getPrincipal()).getUserId());
                    SecurityContextHolder.getContext().setAuthentication(savedAuth);
                }
            } else if (dispatcherType == DispatcherType.ASYNC) {
                log.debug("ASYNC dispatch中未找到保存的Authentication，uri={}", requestPath);
            }
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
                request.setAttribute(SAVED_AUTH_ATTR, authentication);

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

    private boolean isWhiteListPath(String requestPath) {
        return WHITE_LIST_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestPath));
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

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
