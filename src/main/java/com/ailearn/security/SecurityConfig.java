package com.ailearn.security;

import com.ailearn.common.ErrorCode;
import com.ailearn.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security安全配置类
 * 使用@Configuration标记为配置类，@EnableWebSecurity启用Web安全功能
 * 配置JWT认证、CORS跨域、CSRF禁用、无状态Session、路径权限控制、异常处理等
 *
 * @author AiLearn Platform
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * JWT认证过滤器
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * JSON对象映射器
     * 用于将Result对象序列化为JSON字符串返回给前端
     */
    private final ObjectMapper objectMapper;

    /**
     * 配置SecurityFilterChain安全过滤链
     * 这是Spring Security的核心配置方法，定义了所有安全相关的规则
     *
     * 配置项包括：
     * 1. 禁用CSRF（跨站请求伪造）：因为使用JWT无状态认证，不需要CSRF保护
     * 2. 配置CORS（跨域资源共享）：允许所有来源、所有方法、所有头，暴露Authorization头
     * 3. 禁用formLogin（表单登录）：使用JWT认证，不需要默认的表单登录
     * 4. 禁用httpBasic（HTTP Basic认证）：使用JWT认证，不需要HTTP Basic
     * 5. Session管理：设置为STATELESS（无状态），不创建和使用Session
     * 6. 路径权限控制：配置公开路径和需要认证的路径
     * 7. 异常处理：统一处理401未认证和403权限不足，返回JSON格式Result
     * 8. 添加JWT过滤器：在UsernamePasswordAuthenticationFilter之前添加自定义JWT过滤器
     * 9. 禁用缓存头：防止浏览器缓存敏感资源
     *
     * @param http HttpSecurity配置对象
     * @return SecurityFilterChain 安全过滤链
     * @throws Exception 配置过程中可能抛出的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/health", "/api/").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/doc.html").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/", "/index.html", "/favicon.ico").permitAll()
                        .requestMatchers("/assets/**").permitAll()
                        .requestMatchers("/mcp/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            sendJsonErrorResponse(response,
                                    HttpServletResponse.SC_UNAUTHORIZED,
                                    ErrorCode.AUTH_TOKEN_INVALID.getCode(),
                                    ErrorCode.AUTH_TOKEN_INVALID.getMessage());
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            sendJsonErrorResponse(response,
                                    HttpServletResponse.SC_FORBIDDEN,
                                    ErrorCode.AUTH_ACCESS_DENIED.getCode(),
                                    ErrorCode.AUTH_ACCESS_DENIED.getMessage());
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .cacheControl(cache -> cache.disable())
                );

        return http.build();
    }

    /**
     * 配置CORS（跨域资源共享）
     * 允许所有来源、所有HTTP方法、所有请求头，支持凭证，暴露Authorization响应头
     * 用于前后端分离架构中允许前端跨域访问后端API
     *
     * @return CorsConfigurationSource CORS配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 配置密码编码器
     * 使用BCryptPasswordEncoder进行密码加密，这是Spring Security推荐的强哈希算法
     * BCrypt会自动生成盐值，且可以配置强度参数（默认10）
     *
     * @return PasswordEncoder BCrypt密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 发送JSON格式的错误响应
     * 用于统一处理401和403异常，返回标准格式的JSON错误响应
     *
     * @param response   HTTP响应对象
     * @param httpStatus HTTP状态码
     * @param code       业务错误码
     * @param message    错误消息
     * @throws IOException IO异常（写入响应时可能抛出）
     */
    private void sendJsonErrorResponse(HttpServletResponse response,
                                       int httpStatus,
                                       int code,
                                       String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Result<Void> result = Result.error(code, message);
        String jsonResponse = objectMapper.writeValueAsString(result);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
