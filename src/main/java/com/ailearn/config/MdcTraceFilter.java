package com.ailearn.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * MDC链路追踪过滤器
 * 功能说明：
 * - 为每个HTTP请求生成或接收traceId并放入MDC（Mapped Diagnostic Context）
 * - 支持从请求头X-Trace-Id接收上游服务传递的traceId
 * - 自动生成spanId标识当前请求跨度
 * - 提取用户ID、请求路径、请求方法等信息放入MDC供日志使用
 * - 记录请求开始/结束日志，包含请求方法、URI、响应状态、耗时等信息
 * - 请求结束后清理MDC，防止内存泄漏
 * - 使用UUID去掉横线后取前16位作为traceId格式
 *
 * @author AiLearn Platform
 */
@Slf4j
@Component
@Order(1)
public class MdcTraceFilter implements Filter {

    /**
     * MDC中存储traceId的键名（链路追踪ID，标识整个请求链路）
     */
    private static final String TRACE_ID_KEY = "traceId";

    /**
     * MDC中存储spanId的键名（跨度ID，标识当前服务内的请求跨度）
     */
    private static final String SPAN_ID_KEY = "spanId";

    /**
     * MDC中存储用户ID的键名（当前登录用户ID）
     */
    private static final String USER_ID_KEY = "userId";

    /**
     * MDC中存储请求路径的键名
     */
    private static final String REQUEST_PATH_KEY = "requestPath";

    /**
     * MDC中存储请求方法的键名
     */
    private static final String REQUEST_METHOD_KEY = "requestMethod";

    /**
     * 请求头中traceId的键名（用于接收上游服务传递的traceId）
     */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 响应头中traceId的键名（用于向下游传递或返回给前端）
     */
    private static final String TRACE_ID_RESPONSE_HEADER = "X-Trace-Id";

    /**
     * 存储请求开始时间的属性键名（用于计算请求耗时）
     */
    private static final String START_TIME_ATTRIBUTE = "mdcTraceFilter.startTime";

    /**
     * 过滤器核心方法
     * 处理HTTP请求，设置MDC上下文，记录请求日志，处理完成后清理MDC
     *
     * @param request  Servlet请求对象
     * @param response Servlet响应对象
     * @param chain    过滤器链，用于将请求传递给下一个过滤器或目标资源
     * @throws IOException      IO异常，当读取或写入数据失败时抛出
     * @throws ServletException Servlet异常，当过滤器处理失败时抛出
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // 将ServletRequest和ServletResponse转换为HTTP特定的类型
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 记录请求开始时间（使用纳秒级时间戳，用于计算准确的请求耗时）
        long startTime = System.nanoTime();
        // 将开始时间存入请求属性中，供后续计算耗时使用
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);

        // 获取或生成traceId（优先使用请求头中传递的traceId）
        String traceId = getOrGenerateTraceId(httpRequest);
        // 生成新的spanId（标识当前服务内的请求跨度）
        String spanId = generateSpanId();
        // 获取当前登录用户ID（从Spring Security上下文中获取）
        String userId = getCurrentUserId();
        // 获取请求URI
        String requestPath = httpRequest.getRequestURI();
        // 获取请求方法（GET、POST、PUT、DELETE等）
        String requestMethod = httpRequest.getMethod();

        try {
            // 将traceId放入MDC上下文，供后续日志输出使用
            MDC.put(TRACE_ID_KEY, traceId);
            // 将spanId放入MDC上下文
            MDC.put(SPAN_ID_KEY, spanId);
            // 将用户ID放入MDC上下文（如果用户已登录）
            if (userId != null) {
                MDC.put(USER_ID_KEY, userId);
            }
            // 将请求路径放入MDC上下文
            MDC.put(REQUEST_PATH_KEY, requestPath);
            // 将请求方法放入MDC上下文
            MDC.put(REQUEST_METHOD_KEY, requestMethod);

            // 将traceId设置到响应头中，方便前端和下游服务获取
            httpResponse.setHeader(TRACE_ID_RESPONSE_HEADER, traceId);

            // 记录请求开始日志，包含请求方法、请求路径等信息
            log.info("请求开始: method={}, uri={}", requestMethod, requestPath);

            // 继续执行过滤器链，将请求传递给下一个过滤器或目标Controller
            chain.doFilter(request, response);

            // 计算请求耗时（将纳秒转换为毫秒）
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            // 获取HTTP响应状态码
            int status = httpResponse.getStatus();

            // 记录请求结束日志，包含请求方法、URI、响应状态码、耗时等信息
            log.info("请求结束: method={}, uri={}, status={}, duration={}ms",
                    requestMethod, requestPath, status, durationMs);

        } finally {
            // 请求处理完成后，清理MDC上下文中的所有相关字段
            // 必须清理MDC，否则会导致内存泄漏（因为MDC使用ThreadLocal存储）
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(SPAN_ID_KEY);
            MDC.remove(USER_ID_KEY);
            MDC.remove(REQUEST_PATH_KEY);
            MDC.remove(REQUEST_METHOD_KEY);
        }
    }

    /**
     * 获取或生成traceId
     * 优先从请求头X-Trace-Id中获取上游服务传递的traceId，如果不存在则生成新的traceId
     *
     * @param request HTTP请求对象，用于读取请求头
     * @return traceId字符串（16位，无横线）
     */
    private String getOrGenerateTraceId(HttpServletRequest request) {
        // 从请求头中获取traceId
        String traceId = request.getHeader(TRACE_ID_HEADER);
        // 判断请求头中是否存在有效的traceId
        if (traceId == null || traceId.trim().isEmpty()) {
            // 如果请求头中没有traceId，则生成新的traceId
            traceId = generateTraceId();
        } else {
            // 如果请求头中有traceId，去除前后空白字符
            traceId = traceId.trim();
            // 如果传入的traceId长度超过16位，截取前16位使用
            if (traceId.length() > 16) {
                traceId = traceId.substring(0, 16);
            }
        }
        return traceId;
    }

    /**
     * 生成新的traceId
     * 使用UUID生成唯一标识，去除横线后取前16位，生成较短的traceId便于日志展示
     *
     * @return 生成的traceId字符串（16位小写十六进制字符）
     */
    private String generateTraceId() {
        // 生成随机UUID，转换为字符串，去除横线，转换为小写
        String fullUuid = UUID.randomUUID().toString().replace("-", "").toLowerCase();
        // 取前16位作为traceId
        return fullUuid.substring(0, 16);
    }

    /**
     * 生成新的spanId
     * spanId用于标识当前服务内的请求跨度，格式与traceId类似（16位）
     *
     * @return 生成的spanId字符串（16位小写十六进制字符）
     */
    private String generateSpanId() {
        // 生成随机UUID，转换为字符串，去除横线，转换为小写，取前16位
        return UUID.randomUUID().toString().replace("-", "").toLowerCase().substring(0, 16);
    }

    /**
     * 获取当前登录用户ID
     * 从Spring Security上下文中获取认证信息，提取用户ID
     * 如果用户未登录或无法获取用户ID，则返回null
     *
     * @return 用户ID字符串，如果用户未登录则返回null
     */
    private String getCurrentUserId() {
        try {
            // 从Spring Security上下文中获取认证信息
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            // 判断认证信息是否存在且用户已认证
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                // 获取认证主体（用户信息）
                Object principal = authentication.getPrincipal();
                // 如果主体是UserPrincipal类型（自定义用户详情类），则获取用户ID
                if (principal instanceof com.ailearn.security.UserPrincipal userPrincipal) {
                    // 将用户ID转换为字符串返回
                    return String.valueOf(userPrincipal.getUserId());
                }
                // 如果主体是字符串类型（通常是用户名），直接返回
                if (principal instanceof String username) {
                    return username;
                }
            }
        } catch (Exception e) {
            // 获取用户ID失败时不抛出异常，仅记录调试日志（避免影响正常请求处理）
            log.debug("获取当前用户ID失败: {}", e.getMessage());
        }
        // 用户未登录或获取失败，返回null
        return null;
    }
}
