package com.ailearn.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA单页应用回退控制器
 * 解决前端Vue Router使用History模式时，刷新非根路径页面导致404的问题。
 * 所有非API、非静态资源的请求统一转发到index.html，由前端路由处理。
 *
 * @author AiLearn Platform
 */
@Controller
public class SpaFallbackController {

    /**
     * 捕获所有非API、非静态资源的前端路由请求，统一转发到index.html
     * 匹配规则：不包含"."的路径（排除静态资源如.js、.css等），
     * 且不匹配/api/、/actuator/、/mcp/、/swagger-ui/等后端路径
     *
     * @return forward到index.html
     */
    @RequestMapping(value = "/{path:[^.]*}")
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}