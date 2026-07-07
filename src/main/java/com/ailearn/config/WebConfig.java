package com.ailearn.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类
 * 实现WebMvcConfigurer接口，自定义Spring MVC的行为配置，包括：
 * 1. 跨域资源共享（CORS）配置：虽然SecurityConfig已有Filter级别的CORS处理，
 *    这里做MVC级别的补充配置，作为双重保障
 * 2. 静态资源映射：将前端静态资源（/static/目录）正确映射到URL路径
 * 3. Swagger/Knife4j文档资源放行：确保API文档页面的静态资源可访问
 * 4. 视图控制器：配置根路径和前端路由的转发规则（SPA单页应用）
 *
 * @author AiLearn Platform
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置跨域资源共享（CORS）映射
     * 注意：SecurityConfig中已通过CorsFilter配置了全局CORS（允许所有来源），
     * 这里保留MVC级别的CORS配置作为补充，主要针对/api/**路径设置更精确的跨域规则。
     * Spring MVC的CORS配置在HandlerMapping级别生效，与Filter级别的CORS形成双重保障。
     *
     * 配置说明：
     * - 允许前端开发服务器（Vite默认端口5173）跨域访问
     * - 允许的HTTP方法：GET、POST、PUT、DELETE、OPTIONS
     * - 允许所有请求头
     * - 允许携带凭证（Cookie、Authorization头等）
     * - 预检请求缓存时间：3600秒（1小时），减少OPTIONS请求次数
     *
     * @param registry CORS注册表，用于注册跨域映射规则
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 为所有/api/**路径配置跨域访问规则
        registry.addMapping("/api/**")
                // 允许的前端来源地址（Vite开发服务器默认地址）
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                // 允许的HTTP请求方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                // 允许所有请求头（包括Authorization、Content-Type等自定义头）
                .allowedHeaders("*")
                // 允许浏览器携带凭证信息（Cookie、HTTP认证头、客户端SSL证书）
                .allowCredentials(true)
                // 预检请求（OPTIONS）的缓存时间，单位秒，在此时间内浏览器不会重复发送OPTIONS请求
                .maxAge(3600);
        // 为MCP SSE端点配置跨域访问
        registry.addMapping("/mcp/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * 配置静态资源处理器
     * 将URL路径映射到classpath下的静态资源目录，确保前端打包后的资源可以正确访问。
     * Spring Boot默认已配置/static/、/public/、/resources/、/META-INF/resources/目录，
     * 这里显式配置以确保Swagger UI和webjars资源路径正确映射，并添加自定义资源位置。
     *
     * 资源映射规则：
     * - /assets/** → classpath:/static/assets/  前端打包后的JS/CSS/图片等资源
     * - /favicon.ico → classpath:/static/  网站图标
     * - /swagger-ui/** → classpath:/META-INF/resources/webjars/swagger-ui/  Swagger UI页面资源
     * - /webjars/** → classpath:/META-INF/resources/webjars/  WebJars资源（Swagger UI依赖）
     *
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 前端静态资源映射：/assets/**路径映射到classpath:/static/assets/目录
        // 前端Vite构建后的产物包含assets目录，存放JS、CSS、图片等静态资源
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCachePeriod(3600);

        // 网站图标映射：favicon.ico
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/");

        // Swagger UI资源映射：确保springdoc-openapi的Swagger UI页面可正常访问
        // Swagger UI的静态资源打包在webjars中，需要显式映射资源路径
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/")
                .setCachePeriod(0);

        // WebJars资源映射：所有通过webjars引入的前端库资源
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .setCachePeriod(3600);

        // 记录静态资源映射配置完成日志
        log.info("Web MVC静态资源映射已配置完成");
    }

    /**
     * 配置视图控制器
     * 主要用于处理SPA（单页应用）的前端路由：
     * - 根路径"/"转发到index.html
     * - 前端路由（非API、非静态资源的路径）统一转发到index.html，由前端路由处理
     * 这解决了前端使用History路由模式时刷新页面404的问题
     *
     * 注意：这里不处理/api/**、/actuator/**、/mcp/**、/swagger-ui/**等后端路径，
     * 因为这些路径有对应的Controller处理或已在SecurityConfig中配置。
     *
     * @param registry 视图控制器注册表
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径重定向到index.html（前端入口页面）
        registry.addViewController("/")
                .setViewName("forward:/index.html");
        // Swagger UI根路径重定向到swagger-ui/index.html
        registry.addViewController("/swagger-ui/")
                .setViewName("forward:/swagger-ui/index.html");
        // SPA前端路由：所有Vue Router路由统一转发到index.html
        registry.addViewController("/chat")
                .setViewName("forward:/index.html");
        registry.addViewController("/memory")
                .setViewName("forward:/index.html");
        registry.addViewController("/rag")
                .setViewName("forward:/index.html");
        registry.addViewController("/agent")
                .setViewName("forward:/index.html");
        registry.addViewController("/search-agent")
                .setViewName("forward:/index.html");
        registry.addViewController("/structured")
                .setViewName("forward:/index.html");
        registry.addViewController("/tools")
                .setViewName("forward:/index.html");
        registry.addViewController("/multi-agent")
                .setViewName("forward:/index.html");
        registry.addViewController("/mcp")
                .setViewName("forward:/index.html");
    }
}