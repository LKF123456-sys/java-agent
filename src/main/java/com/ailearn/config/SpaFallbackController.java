package com.ailearn.config; // 声明当前类所在的包：config（配置层）

// 导入@Controller注解，标记这是Spring MVC控制器（注意：不是@RestController，因为返回的是视图转发指令而非JSON）
import org.springframework.stereotype.Controller;
// 导入@RequestMapping注解，定义路径映射规则
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA单页应用回退控制器
 * 解决前端Vue Router使用History模式时，刷新非根路径页面导致404的问题。
 * 所有非API、非静态资源的请求统一转发到index.html，由前端路由处理。
 *
 * @author AiLearn Platform
 */
@Controller // 标记为MVC控制器（返回String会被当作视图名/转发指令处理）
public class SpaFallbackController { // 定义SPA回退控制器

    /**
     * 捕获所有非API、非静态资源的前端路由请求，统一转发到index.html
     * 匹配规则：不包含"."的路径（排除静态资源如.js、.css等），
     * 且不匹配/api/、/actuator/、/mcp/、/swagger-ui/等后端路径
     *
     * @return forward到index.html
     */
    @RequestMapping(value = "/{path:[^.]*}") // 映射所有"不含点号"的一级路径（正则[^.]*排除带扩展名的静态资源请求）
    public String forwardToIndex() { // 处理前端路由回退
        return "forward:/index.html"; // forward转发：服务器内部把请求转给index.html，浏览器地址栏不变，由Vue Router接管路由
    }
}
