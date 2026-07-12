package com.ailearn.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Cyber AI Platform - 赛博AI平台 API文档")
                        .version("0.0.3")
                        .description("""
                                赛博AI平台 - 基于Spring AI 1.0.0构建的生产级企业级AI应用平台
                                
                                ## 功能特性
                                - **基础聊天**：支持流式SSE响应、会话记忆、多轮对话
                                - **智能Agent**：单Agent工具调用（天气/计算器/联网搜索）
                                - **联网搜索Agent**：集成Tavily搜索引擎，获取实时信息
                                - **多Agent协作**：Planner→Researcher→Coder→Critic→Executor团队协作
                                - **RAG知识库**：文档上传/分块/向量化/检索问答
                                - **记忆对话**：基于对话记忆的AI助手
                                - **结构化输出**：返回JSON等结构化格式
                                - **MCP协议**：模型上下文协议工具管理
                                
                                ## 认证方式
                                1. 调用 `/api/auth/login` 获取JWT Token
                                2. 点击右上角 **Authorize** 按钮，输入 `Bearer {your_token}`
                                3. 之后所有请求会自动携带Authorization头
                                """)
                        .contact(new Contact()
                                .name("Cyber AI Team")
                                .email("support@cyberai.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT认证，请输入Token（不需要加Bearer前缀）")));
    }
}
