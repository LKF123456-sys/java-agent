package com.ailearn.security; // 声明包名，属于安全认证模块

import com.ailearn.common.ErrorCode; // 导入错误码枚举，用于返回标准化错误码
import com.ailearn.common.Result; // 导入统一响应包装类，用于构建JSON错误响应
import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson对象映射器，将Result对象序列化为JSON
import jakarta.servlet.http.HttpServletResponse; // 导入HTTP响应对象，用于设置状态码和写入响应体
import lombok.RequiredArgsConstructor; // 导入Lombok注解，为final字段自动生成构造器注入
import org.springframework.context.annotation.Bean; // 导入Bean注解，将方法返回值注册为Spring容器管理的Bean
import org.springframework.context.annotation.Configuration; // 导入配置类注解，标记此类为Spring配置类
import org.springframework.http.HttpMethod; // 导入HTTP方法枚举，用于匹配OPTIONS等特定HTTP方法
import org.springframework.http.MediaType; // 导入媒体类型常量，用于设置响应Content-Type为JSON
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // 导入HttpSecurity构建器，配置安全规则
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // 导入启用Web安全注解
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // 导入抽象配置器，用于禁用默认安全功能
import org.springframework.security.config.http.SessionCreationPolicy; // 导入会话创建策略，配置无状态会话
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // 导入BCrypt密码编码器，用于密码加密和验证
import org.springframework.security.crypto.password.PasswordEncoder; // 导入密码编码器接口
import org.springframework.security.web.SecurityFilterChain; // 导入安全过滤链，Spring Security的核心配置对象
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // 导入用户名密码认证过滤器
import org.springframework.web.cors.CorsConfiguration; // 导入CORS配置对象，配置跨域资源共享规则
import org.springframework.web.cors.CorsConfigurationSource; // 导入CORS配置源接口
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; // 导入基于URL的CORS配置源实现

import java.io.IOException; // 导入IO异常，写入响应时可能抛出
import java.util.Arrays; // 导入Arrays工具类，用于创建HTTP方法列表
import java.util.List; // 导入List接口，用于配置允许的HTTP方法和来源

@Configuration // 标记为Spring配置类，类中的@Bean方法会被Spring容器扫描并注册
@EnableWebSecurity // 启用Spring Security的Web安全功能，自动配置安全过滤链
@RequiredArgsConstructor // Lombok注解，为所有final字段生成构造器参数，实现依赖注入
public class SecurityConfig { // 安全配置类定义

    private final JwtAuthenticationFilter jwtAuthenticationFilter; // JWT认证过滤器，在UsernamePasswordAuthenticationFilter之前执行

    private final ObjectMapper objectMapper; // JSON对象映射器，用于将错误响应序列化为JSON字符串

    @Bean // 将方法返回值注册为Spring Bean，Spring Security会自动使用此过滤链
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception { // 配置安全过滤链
        http // 开始配置HttpSecurity
                .csrf(AbstractHttpConfigurer::disable) // 禁用CSRF保护，因为API使用JWT认证不需要CSRF Token
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // 启用CORS跨域支持，使用自定义CORS配置
                .formLogin(AbstractHttpConfigurer::disable) // 禁用表单登录，API项目不使用表单认证
                .httpBasic(AbstractHttpConfigurer::disable) // 禁用HTTP Basic认证，仅使用JWT认证
                .sessionManagement(session -> session // 配置会话管理策略
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 无状态会话，不创建HttpSession
                ) // 会话管理配置结束
                .authorizeHttpRequests(auth -> auth // 配置URL访问控制规则
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll() // 认证接口无需登录
                        .requestMatchers("/api/health", "/api/").permitAll() // 健康检查和根路径无需登录
                        .requestMatchers("/error").permitAll() // Spring Boot错误端点无需登录
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 所有OPTIONS预检请求无需登录
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll() // 监控健康端点无需登录
                        .requestMatchers("/swagger-ui/**").permitAll() // Swagger UI页面无需登录
                        .requestMatchers("/v3/api-docs/**").permitAll() // OpenAPI文档接口无需登录
                        .requestMatchers("/doc.html").permitAll() // 文档页面无需登录
                        .requestMatchers("/webjars/**").permitAll() // WebJars静态资源无需登录
                        .requestMatchers("/", "/index.html", "/favicon.ico").permitAll() // 前端入口页和图标无需登录
                        .requestMatchers("/assets/**").permitAll() // 前端静态资源无需登录
                        .anyRequest().authenticated() // 其余所有请求都需要JWT认证
                ) // 访问控制配置结束
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
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class) // 在用户名密码过滤器之前插入JWT过滤器
                .headers(headers -> headers // 配置HTTP响应头安全设置
                        .cacheControl(cache -> cache.disable()) // 禁用缓存控制，允许前端缓存响应
                ); // 响应头配置结束

        return http.build(); // 构建并返回SecurityFilterChain安全过滤链对象
    } // securityFilterChain方法结束

    @Bean // 将CORS配置源注册为Spring Bean，被SecurityConfig中的cors()引用
    public CorsConfigurationSource corsConfigurationSource() { // 创建CORS跨域配置源
        CorsConfiguration configuration = new CorsConfiguration(); // 创建CORS配置对象
        configuration.setAllowedOriginPatterns(List.of("*")); // 允许所有来源地址跨域访问（使用模式匹配而非精确匹配）
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")); // 允许所有常用HTTP方法
        configuration.setAllowedHeaders(List.of("*")); // 允许所有请求头（包括Authorization等自定义头）
        configuration.setAllowCredentials(true); // 允许携带凭证信息（Cookie、Authorization头）
        configuration.setExposedHeaders(List.of("Authorization")); // 暴露Authorization响应头给前端JavaScript读取
        configuration.setMaxAge(3600L); // 预检请求缓存1小时，减少OPTIONS请求次数

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); // 创建基于URL的CORS配置源
        source.registerCorsConfiguration("/**", configuration); // 将CORS配置应用到所有URL路径
        return source; // 返回配置好的CORS配置源
    } // corsConfigurationSource方法结束

    @Bean // 将密码编码器注册为Spring Bean，供UserService等需要密码加密的地方注入使用
    public PasswordEncoder passwordEncoder() { // 创建密码编码器Bean
        return new BCryptPasswordEncoder(); // 返回BCrypt密码编码器，自动加盐加密，安全性高
    } // passwordEncoder方法结束

    /**
     * 发送JSON格式的错误响应
     * 将Result对象序列化为JSON写入HTTP响应体
     *
     * @param response   HTTP响应对象
     * @param httpStatus HTTP状态码
     * @param code       业务错误码
     * @param message    错误消息
     * @throws IOException 写入响应时可能抛出的IO异常
     */
    private void sendJsonErrorResponse(HttpServletResponse response, // 发送JSON错误响应的私有方法
                                       int httpStatus, // HTTP状态码参数
                                       int code, // 业务错误码参数
                                       String message) throws IOException { // 可能抛出IO异常
        response.setStatus(httpStatus); // 设置HTTP响应状态码（如401、403）
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // 设置响应内容类型为application/json
        response.setCharacterEncoding("UTF-8"); // 设置字符编码为UTF-8，防止中文乱码

        Result<Void> result = Result.error(code, message); // 构建统一错误响应对象
        String jsonResponse = objectMapper.writeValueAsString(result); // 将Result对象序列化为JSON字符串
        response.getWriter().write(jsonResponse); // 将JSON字符串写入HTTP响应体
        response.getWriter().flush(); // 刷新输出流，确保响应立即发送给客户端
    } // sendJsonErrorResponse方法结束
} // SecurityConfig类结束
