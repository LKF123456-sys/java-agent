---
kind: configuration_system
name: Spring Boot 多环境配置与条件装配体系
category: configuration_system
scope:
    - '**'
source_files:
    - src/main/resources/application.yml
    - src/test/resources/application-test.yml
    - src/main/java/com/ailearn/config/AiConfig.java
    - src/main/java/com/ailearn/config/RateLimiterConfig.java
    - src/main/resources/logback-spring.xml
---

## 系统概述
本项目采用 Spring Boot 原生配置体系，以 `application.yml` 为核心配置文件，结合 YAML Profile 分段、环境变量覆盖、Java 注解条件装配三层机制，实现开发/测试/生产/Docker 多环境差异化配置。

## 核心文件与包
- **主配置**：`src/main/resources/application.yml`（343行，含 docker profile 分段）
- **测试配置**：`src/test/resources/application-test.yml`（H2内存库 + Mock 依赖）
- **配置类**：`src/main/java/com/ailearn/config/` 下按能力拆分：
  - `AiConfig.java` — VectorStore 条件装配（PgVector vs SimpleVectorStore）
  - `RateLimiterConfig.java` — Resilience4j 限流事件监听
  - `McpServerConfig.java` / `MyBatisPlusConfig.java` / `OpenApiConfig.java` / `WebConfig.java` 等
- **日志**：`logback-spring.xml`（配合 logback 的 profile 支持）
- **SQL初始化**：`schema.sql`（MySQL）、`schema-postgresql.sql`（PostgreSQL/PgVector）

## 架构与约定
1. **配置优先级**：环境变量 > application.yml > 默认值。所有外部化参数均使用 `${VAR:default}` 语法，如 `SERVER_PORT`、`SPRING_DATASOURCE_URL`、`JWT_SECRET`、`TAVILY_API_KEY` 等。
2. **Profile 分层**：通过 `---` 分隔符在单个文件中定义 `docker` profile，覆盖数据源（MySQL→PostgreSQL+PgVector）、向量存储开关、日志路径、API文档开关等差异项；测试环境独立 `application-test.yml`，完全隔离外部依赖。
3. **条件装配模式**：大量使用 `@ConditionalOnProperty`、`@ConditionalOnMissingBean`、`@Primary` 组合，在运行时根据配置动态选择 Bean 实现（如 VectorStore 在 PostgreSQL 环境下自动切换 PgVectorStore，否则回退到内存 SimpleVectorStore）。
4. **安全敏感配置外置**：JWT密钥、第三方 API Key 强制要求通过环境变量注入，禁止硬编码到配置文件。
5. **功能开关**：通过 `spring.ai.vectorstore.pgvector.enabled`、`springdoc.*.enabled`、`resilience4j.*` 等布尔开关控制特性启用/禁用，便于灰度与降级。

## 开发者规范
- 新增可配置项时，统一在 `application.yml` 中声明并给出合理默认值，同时为关键参数提供环境变量占位符。
- 需要按环境切换实现的组件，优先使用 `@ConditionalOnProperty` + `@ConditionalOnMissingBean` 组合进行条件装配，避免在业务代码中写 if-else 分支。
- 测试配置放在 `src/test/resources/application-test.yml`，并通过 `@ActiveProfiles("test")` 激活，确保单元测试不依赖外部服务。
- 生产部署时，必须通过环境变量或启动参数 `-Dspring.profiles.active=docker` 指定运行环境，不得直接修改 `application.yml`。