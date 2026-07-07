package com.ailearn.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@Order(1)
public class SpaFallbackFilter implements Filter {

    private static final String[] API_PREFIXES = {
            "/api/", "/actuator/", "/mcp/", "/swagger-ui/",
            "/v3/api-docs", "/webjars/", "/doc.html"
    };

    private static final String[] STATIC_PREFIXES = {
            "/assets/", "/favicon.", "/index.html"
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        if (shouldForwardToIndex(path)) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean shouldForwardToIndex(String path) {
        for (String prefix : API_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        for (String prefix : STATIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        if (path.contains(".")) {
            return false;
        }
        return true;
    }
}