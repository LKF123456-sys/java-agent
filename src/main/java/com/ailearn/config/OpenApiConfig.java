package com.ailearn.config; // 声明包名，属于配置模块

// 以下导入用于构建Swagger/OpenAPI文档的元数据对象（来自swagger-core-jakarta库）
import io.swagger.v3.oas.models.Components; // OpenAPI组件容器，用于集中定义可复用的安全方案等
import io.swagger.v3.oas.models.OpenAPI; // OpenAPI文档根对象，所有文档信息挂在这上面
import io.swagger.v3.oas.models.info.Contact; // 联系人信息（作者/邮箱）
import io.swagger.v3.oas.models.info.Info; // 文档基本信息（标题/版本/描述）
import io.swagger.v3.oas.models.info.License; // 开源许可证信息
import io.swagger.v3.oas.models.security.SecurityRequirement; // 全局安全要求（声明整个API需要哪种认证）
import io.swagger.v3.oas.models.security.SecurityScheme; // 安全方案定义（HTTP Bearer / API Key 等）
import org.springframework.context.annotation.Bean; // Spring的@Bean注解，把方法返回值注册为容器管理的Bean
import org.springframework.context.annotation.Configuration; // 标记此类为配置类

/**
 * OpenAPI/Swagger文档配置类
 *
 * <p>作用：告诉springdoc-openapi"我的API文档长什么样"，包括：
 * <ul>
 *   <li>文档标题、版本、描述、联系方式、许可证</li>
 *   <li>全局认证方式（本项目使用JWT Bearer Token）</li>
 * </ul>
 *
 * <p>启动后访问 http://localhost:8080/swagger-ui.html 即可看到生成的交互式文档。
 * 这个配置类本身不写业务代码，只负责"美化"Swagger界面的展示信息。
 *
 * @author AiLearn Platform
 */
@Configuration // 标记为Spring配置类，其中的@Bean方法会被容器扫描
public class OpenApiConfig { // 配置类定义

    /**
     * 自定义OpenAPI文档Bean
     *
     * <p>springdoc-openapi启动时会自动寻找容器中的OpenAPI Bean，
     * 找到后就用它来生成文档；如果找不到，会用一个最简默认配置。
     * 本方法返回一个"定制版"，让文档带上我们的项目信息和JWT认证说明。
     *
     * @return OpenAPI 文档根对象
     */
    @Bean // 把方法返回值注册为Spring Bean（容器中只此一个，springdoc会自动捡来用）
    public OpenAPI customOpenAPI() { // 方法名随意，Spring按返回类型OpenAPI装配
        // 定义安全方案的名称（这个名字会作为Swagger右上角"Authorize"按钮的标识）
        // 取名bearerAuth是社区惯例：bearer代表Bearer Token，Auth代表认证
        final String securitySchemeName = "bearerAuth";
        // 用链式调用构建OpenAPI根对象：.info()设文档信息，.addSecurityItem()设全局安全要求，.components()设安全方案定义
        return new OpenAPI()
                // ===== 第一部分：文档基本信息 =====
                .info(new Info() // 创建Info对象，承载标题/版本/描述
                        .title("Cyber AI Platform - 赛博AI平台 API文档") // 文档标题（Swagger页面最上方显示）
                        .version("0.1.0") // API版本号（跟随项目版本，升级Spring AI后已升至0.1.0）
                        // 文档长描述（Java文本块多行字符串，注意起始"""后不能有任何注释）
                        .description("""
                                赛博AI平台 - 基于Spring AI 2.0.0构建的生产级企业级AI应用平台

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
                                """) // 描述结束
                        .contact(new Contact() // 联系人信息
                                .name("Cyber AI Team") // 团队名
                                .email("support@cyberai.com")) // 联系邮箱
                        .license(new License() // 许可证信息
                                .name("Apache 2.0") // 许可证名称
                                .url("https://www.apache.org/licenses/LICENSE-2.0"))) // 许可证全文链接
                // ===== 第二部分：全局安全要求 =====
                // 声明"整个API默认都需要bearerAuth这种认证"，Swagger界面会在每个接口旁显示小锁图标
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                // ===== 第三部分：安全方案定义 =====
                // 告诉Swagger"bearerAuth具体是什么"——HTTP协议+Bearer方案+JWT格式
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, // 方案名（与上面SecurityRequirement对应）
                                new SecurityScheme()
                                        .name(securitySchemeName) // 方案显示名
                                        .type(SecurityScheme.Type.HTTP) // 类型：HTTP认证
                                        .scheme("bearer") // HTTP认证方案：bearer（即RFC6750 Bearer Token）
                                        .bearerFormat("JWT") // Token格式标识：JWT（提示这是JSON Web Token）
                                        .description("JWT认证，请输入Token（不需要加Bearer前缀）"))); // 给使用者的说明
    } // customOpenAPI方法结束
} // OpenApiConfig类结束
