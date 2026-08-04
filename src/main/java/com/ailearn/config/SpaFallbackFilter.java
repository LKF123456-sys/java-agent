package com.ailearn.config; // 声明包名，属于配置模块

// 以下导入的是Java Servlet规范中的过滤器相关接口（jakarta.*是Spring Boot 3+/Jakarta EE的新包名）
import jakarta.servlet.Filter; // 过滤器接口，所有请求进入Servlet前都会先经过它
import jakarta.servlet.FilterChain; // 过滤器链，调用doFilter把请求传给下一个过滤器或最终Servlet
import jakarta.servlet.FilterConfig; // 过滤器初始化配置（本类未用到，但接口要求实现init时可访问）
import jakarta.servlet.ServletException; // Servlet相关异常
import jakarta.servlet.ServletRequest; // 通用请求抽象（不限定HTTP）
import jakarta.servlet.ServletResponse; // 通用响应抽象（不限定HTTP）
import jakarta.servlet.http.HttpServletRequest; // HTTP请求对象，可获取URI、Header等
import lombok.extern.slf4j.Slf4j; // Lombok日志注解，自动生成log对象
import org.springframework.core.annotation.Order; // Spring注解，指定过滤器执行顺序（数字越小越先执行）
import org.springframework.stereotype.Component; // Spring组件注解，让容器扫描并注册此过滤器

import java.io.IOException; // IO异常

/**
 * SPA（单页应用）路由回退过滤器
 *
 * <p>【为什么需要这个过滤器】
 * 前端用Vue Router的"history模式"做路由时，刷新页面或直接访问 /chat /agent 等
 * 深层路径时，浏览器会向服务器请求这些路径。但服务器上并没有 /chat.html 这样的文件，
 * 后端又用Spring MVC接了 /api/* 这些真API。为了既不干扰真API、又能正确返回前端首页，
 * 需要一个"回退规则"：
 * <ul>
 *   <li>真API路径（/api/、/actuator/ 等）：原样放行，交给后端Controller处理</li>
 *   <li>静态资源路径（/assets/、/favicon.、/index.html）：原样放行，交给静态资源处理器</li>
 *   <li>带后缀的请求（如 /logo.png）：原样放行（可能是静态文件）</li>
 *   <li>其它所有路径（如 /chat、/agent）：统一转发到 /index.html，由前端Vue Router接管路由</li>
 * </ul>
 *
 * <p>这就是"history模式路由的SPA回退"标准做法。
 *
 * @author AiLearn Platform
 */
@Slf4j // Lombok注解，自动注入log对象用于日志记录
@Component // 把此过滤器注册为Spring Bean，会被Spring自动加入过滤器链
@Order(1) // 执行顺序设为1（靠前），确保在Spring Security等过滤器之前就把静态/路由规则处理掉
public class SpaFallbackFilter implements Filter { // 实现Servlet规范Filter接口

    // 需要放行给后端Controller处理的"真API"路径前缀清单
    // 这些路径是后端REST API、监控端点、Swagger文档、MCP端点等，绝不能被回退到首页
    private static final String[] API_PREFIXES = { // 常量数组，不可变
            "/api/", // 后端REST API入口
            "/actuator/", // Spring Boot Actuator监控端点
            "/mcp/", // MCP协议端点
            "/swagger-ui/", // Swagger UI静态资源
            "/v3/api-docs", // OpenAPI 3文档JSON
            "/webjars/", // WebJars（Swagger等第三方库的静态资源）
            "/doc.html" // Knife4j等文档UI
    };

    // 需要放行给静态资源处理器的前端"静态文件"路径前缀
    // 这些路径对应真实的磁盘文件，交给Spring静态资源服务即可
    private static final String[] STATIC_PREFIXES = { // 常量数组
            "/assets/", // Vite打包后的JS/CSS资源
            "/favicon.", // 网站图标
            "/index.html" // 前端首页本身
    };

    /**
     * 过滤器核心方法：每个HTTP请求都会经过这里
     *
     * @param request  请求对象
     * @param response 响应对象
     * @param chain    过滤器链（调用chain.doFilter把请求传给下一站）
     * @throws IOException      IO异常（读请求/写响应时可能抛）
     * @throws ServletException Servlet异常
     */
    @Override // 标记为接口方法实现
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException { // 声明可能抛出的异常
        // 把通用的ServletRequest强转为HttpServletRequest，才能取URI等HTTP专属信息
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        // 获取本次请求的URI路径，例如 /chat 或 /api/chat/send
        String path = httpRequest.getRequestURI();

        // 判断这个路径是否应该回退到首页（即不是API、不是静态文件、不是带后缀的文件）
        if (shouldForwardToIndex(path)) { // 如果需要回退
            // 用请求转发器把请求内部转发到 /index.html（浏览器URL不变，但服务器返回首页）
            // 这样Vue Router在前端拿到首页后，会根据URL自己渲染对应路由
            request.getRequestDispatcher("/index.html").forward(request, response);
            return; // 转发后直接返回，不再往下传，本次请求处理结束
        }

        // 不需要回退的路径（真API/静态文件/带后缀），原样放行给后续过滤器和Servlet
        chain.doFilter(request, response);
    }

    /**
     * 判断一个路径是否应该被回退到 /index.html
     *
     * <p>判断逻辑（只要命中任一"不回退"条件就返回false）：
     * 1. 以API前缀开头 → 不回退（后端要处理）
     * 2. 以静态资源前缀开头 → 不回退（静态资源服务要处理）
     * 3. 路径中含"."（带文件后缀） → 不回退（可能是 .js .css .png 等静态文件）
     * 4. 以上都不满足 → 回退到首页（SPA路由）
     *
     * @param path 请求URI路径
     * @return true=应回退到首页；false=原样放行
     */
    private boolean shouldForwardToIndex(String path) { // 私有判断方法
        // 遍历所有API前缀，检查路径是否以其中任一开头
        for (String prefix : API_PREFIXES) { // 增强for循环遍历前缀数组
            if (path.startsWith(prefix)) { // 路径以该前缀开头
                return false; // 是真API路径，不回退，原样放行
            }
        }
        // 遍历所有静态资源前缀，检查路径是否以其中任一开头
        for (String prefix : STATIC_PREFIXES) { // 遍历静态前缀数组
            if (path.startsWith(prefix)) { // 路径以静态前缀开头
                return false; // 是静态资源路径，不回退，原样放行
            }
        }
        // 处理"带后缀的文件"情况：如 /logo.png、/app.js
        // 简单判定：路径中包含"."就当作文件请求，不回退
        if (path.contains(".")) { // 路径含点号
            return false; // 当作静态文件，不回退
        }
        // 既不是API、也不是静态资源、也不带后缀 → 判定为SPA前端路由，需要回退到首页
        return true;
    } // shouldForwardToIndex方法结束
} // SpaFallbackFilter类结束
