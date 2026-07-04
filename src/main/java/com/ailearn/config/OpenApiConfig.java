package com.ailearn.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI配置类
 * 基于springdoc-openapi-starter-webmvc-ui自动生成OpenAPI 3.0规范的API文档，
 * 并提供Swagger UI交互式文档页面。
 *
 * 访问地址：
 * - Swagger UI页面：http://localhost:8080/swagger-ui/index.html
 * - OpenAPI JSON规范：http://localhost:8080/v3/api-docs
 * - 分组API文档：http://localhost:8080/v3/api-docs/public
 *
 * @author AiLearn Platform
 */
@Slf4j
@Configuration
public class OpenApiConfig {

    /**
     * API安全方案名称常量
     * 用于在OpenAPI文档中标识JWT Bearer Token认证方式
     */
    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

    /**
     * 配置公开API分组
     * 将所有业务Controller归入"public"分组，扫描指定的包路径，
     * 可以通过分组过滤不需要暴露的内部接口。
     *
     * 扫描的包路径包括：
     * - com.ailearn.controller：用户认证、用户管理等基础接口
     * - com.ailearn.chat：AI聊天对话接口（基础聊天、流式输出）
     * - com.ailearn.agent：AI Agent智能体接口（ReAct模式、多Agent协作）
     * - com.ailearn.mcp：MCP（Model Context Protocol）协议接口
     * - com.ailearn.rag：RAG检索增强生成接口（知识库问答）
     * - com.ailearn.memory：对话记忆管理接口（多轮对话历史）
     * - com.ailearn.structured：结构化输出接口（Bean/JSON模式输出）
     * - com.ailearn.tools：工具调用接口（Function Calling演示）
     *
     * @return GroupedOpenApi 分组API配置
     */
    @Bean
    public GroupedOpenApi publicApi() {
        // 构建public分组，设置分组名称和扫描规则
        return GroupedOpenApi.builder()
                // 分组名称，对应/v3/api-docs/{group}路径
                .group("public")
                // 设置分组显示名称（在Swagger UI顶部下拉选择中显示）
                .displayName("赛博AI平台 - 公开接口")
                // 扫描指定包下的所有Controller类
                .packagesToScan(
                        "com.ailearn.controller",
                        "com.ailearn.chat",
                        "com.ailearn.agent",
                        "com.ailearn.mcp",
                        "com.ailearn.rag",
                        "com.ailearn.memory",
                        "com.ailearn.structured",
                        "com.ailearn.tools"
                )
                // 匹配所有/api/**路径的接口（可按需添加路径过滤规则）
                .pathsToMatch("/api/**", "/mcp/**")
                // 构建分组配置
                .build();
    }

    /**
     * 配置OpenAPI基础信息
     * 定义API文档的元数据，包括标题、描述、版本号、联系人信息、许可证等，
     * 同时配置JWT Bearer Token认证方案，使Swagger UI支持Authorize按钮进行接口调试。
     *
     * @return OpenAPI OpenAPI规范配置对象
     */
    @Bean
    public OpenAPI customOpenAPI() {
        // 记录OpenAPI配置初始化日志
        log.info("SpringDoc OpenAPI文档配置已初始化");
        // 构建并返回OpenAPI配置对象
        return new OpenAPI()
                // 添加全局安全要求：所有接口默认需要Bearer JWT认证
                // 在Swagger UI中点击"Authorize"按钮输入Token后，所有请求会自动携带Authorization头
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                // 配置安全组件：定义JWT Bearer Token认证方案
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        // 认证方案类型：HTTP认证
                                        .type(SecurityScheme.Type.HTTP)
                                        // 认证方案：Bearer Token
                                        .scheme("bearer")
                                        // Bearer Token格式：JWT
                                        .bearerFormat("JWT")
                                        // 安全方案描述，在Swagger UI的Authorize弹窗中显示
                                        .description("请输入JWT令牌（登录接口/api/auth/login获取），格式：Bearer {token}")
                        )
                )
                // 配置API文档基本信息
                .info(new Info()
                        // API标题
                        .title("赛博AI平台 API")
                        // API版本号
                        .version("1.0.0")
                        // API详细描述，支持Markdown格式
                        .description("""
                                ## 赛博AI平台 - 生产级Spring AI企业级应用
                                
                                基于Spring Boot 3.4 + Spring AI 1.0构建的AI应用平台，提供以下核心能力：
                                
                                ### 功能模块
                                - **🤖 基础聊天**：支持同步/流式（SSE）对话输出
                                - **🧠 对话记忆**：基于数据库的持久化多轮对话管理
                                - **📋 结构化输出**：AI输出直接映射为Java对象（Bean/JSON模式）
                                - **🔧 工具调用**：Function Calling支持天气查询、计算器、系统工具
                                - **📚 RAG知识库**：PgVector向量存储 + 文档解析（PDF/Word/PPT）
                                - **🤝 Agent模式**：ReAct推理框架 + 多Agent协作
                                - **🔌 MCP协议**：Model Context Protocol服务端接入
                                - **🔐 JWT认证**：基于Spring Security的无状态认证体系
                                
                                ### 认证说明
                                1. 先调用 `/api/auth/login` 接口获取JWT令牌
                                2. 点击页面右上角 **Authorize** 按钮
                                3. 输入令牌值（不需要加Bearer前缀，系统会自动添加）
                                4. 之后所有需要认证的接口请求会自动携带Authorization头
                                """)
                        // 联系人信息
                        .contact(new Contact()
                                // 联系人名称
                                .name("赛博AI平台开发团队")
                                // 联系邮箱
                                .email("s******@*********")
                                // 项目主页/Git仓库地址
                                .url("https://github.com/example/cyber-ai-platform")
                        )
                        // 开源许可证信息
                        .license(new License()
                                // 许可证名称
                                .name("Apache License 2.0")
                                // 许可证全文URL
                                .url("https://www.apache.org/licenses/LICENSE-2.0")
                        )
                );
    }
}
