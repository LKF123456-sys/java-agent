package com.ailearn.common;

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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器
 * 为每个HTTP请求生成唯一的traceId（链路追踪ID），放入SLF4J的MDC（Mapped Diagnostic Context）中，
 * 方便在日志中追踪整个请求链路；请求结束后清理MDC避免内存泄漏；
 * 同时将traceId写入响应头X-Trace-Id，方便前端排查问题
 *
 * @author AiLearn Platform
 */
@Slf4j
@Component
@Order(1)
public class TraceIdFilter implements Filter {

    /**
     * MDC中traceId的键名
     */
    private static final String TRACE_ID_KEY = "traceId";

    /**
     * 响应头中traceId的键名
     */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 请求头中traceId的键名（用于接收上游服务传递的traceId）
     */
    private static final String TRACE_ID_REQUEST_HEADER = "X-Trace-Id";

    /**
     * 过滤器核心方法
     * 处理请求时生成或获取traceId，放入MDC和响应头，请求处理完成后清理MDC
     *
     * @param request  Servlet请求对象
     * @param response Servlet响应对象
     * @param chain    过滤器链，用于传递请求到下一个过滤器或目标资源
     * @throws IOException      IO异常
     * @throws ServletException Servlet异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String traceId = getOrGenerateTraceId(httpRequest);

        try {
            MDC.put(TRACE_ID_KEY, traceId);
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);

            log.debug("请求开始: method={}, uri={}, traceId={}",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    traceId);

            chain.doFilter(request, response);
        } finally {
            log.debug("请求结束: method={}, uri={}, traceId={}",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    traceId);

            MDC.remove(TRACE_ID_KEY);
        }
    }

    /**
     * 获取或生成traceId
     * 优先从请求头中获取上游服务传递的traceId，如果没有则生成新的UUID
     *
     * @param request HTTP请求对象
     * @return traceId字符串
     */
    private String getOrGenerateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_REQUEST_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
        }
        return traceId;
    }

    /**
     * 生成新的traceId
     * 使用UUID生成唯一标识，去除横线并转为小写，缩短长度便于使用
     *
     * @return 生成的traceId字符串
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase();
    }
}
