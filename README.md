# Cyber AI Platform - 赛博AI平台

> 基于 Spring AI 1.0.0 构建的**生产就绪级**企业级AI应用平台，涵盖多Agent协作、RAG知识库、联网搜索、MCP工具协议、日志链路追踪、单元测试、CI/CD等完整能力。Element Plus企业级UI，支持Ollama本地模型一键部署。

---

## ✨ 核心功能

| 模块 | 功能说明 | 访问路径 |
|------|---------|---------|
| 💬 基础聊天 | 流式SSE对话、会话管理、历史记录持久化、用户数据隔离 | `/chat` |
| 🧠 记忆对话 | 基于ChatMemory的多轮上下文对话，跨会话记忆 | `/memory` |
| 🤖 Agent智能体 | 支持工具调用（天气/计算器/联网搜索）的ReAct模式Agent | `/agent` |
| 🔍 联网搜索Agent | 基于Tavily API的实时联网搜索，"先搜索后总结"高可靠模式 | `/search-agent` |
| 👥 多Agent协作 | Planner→Researcher→Coder→Critic→Executor五角色协作，支持Critic迭代优化（最多3轮）和动态路由 | `/multi-agent` |
| 📚 RAG检索增强 | 文档上传（PDF/Word/Excel/PPT/TXT）、PgVector向量存储、知识库问答、文档管理 | `/rag` |
| 🔧 MCP工具 | Model Context Protocol标准工具协议支持 | `/mcp` |
| 📊 结构化输出 | Java Bean自动映射JSON，支持对象/列表结构化返回 | `/structured` |
| 🛠️ 工具演示 | Function Calling示例：天气查询(Open-Meteo)、数学计算 | `/tools` |

### 🔧 内置工具

- **天气查询**：接入Open-Meteo实时气象API，支持全球城市天气查询和3天预报
- **计算器**：精确数学计算，避免大模型计算错误
- **联网搜索**：Tavily搜索引擎，获取实时新闻、数据、文档
- **系统工具**：获取系统时间、运行环境信息等

---

## 🛠️ 技术栈

### 后端（生产就绪）

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | Record、Text Block、Virtual Thread新特性 |
| Spring Boot | 3.4.7 | 企业级应用框架最新稳定版 |
| Spring AI | 1.0.0 | AI应用开发核心框架（GA正式版） |
| Spring Security + JWT | - | 用户认证授权，JJWT 0.12.6，Access/Refresh双Token |
| MyBatis-Plus | 3.5.9 | ORM框架，简化数据库操作 |
| MySQL / PostgreSQL+PgVector | 8.0 / 16 | 关系型数据库，Docker环境启用PgVector生产级向量存储 |
| Resilience4j | 2.2.0 | 限流熔断保护，防止AI接口被恶意调用 |
| SpringDoc OpenAPI | 2.8.6 | 自动生成Swagger API文档 |
| Logback + Logstash Encoder | - | 日志持久化（按天滚动、30天保留、错误日志分离）、JSON格式供ELK采集 |
| Micrometer Tracing + Brave | - | 分布式链路追踪，MDC自动注入traceId/spanId |
| Ollama | - | 本地大模型运行时，支持qwen2.5等开源模型 |
| JUnit 5 + Mockito + Testcontainers | - | 单元测试+集成测试，90+测试用例 |

### 前端（企业级UI）

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | 3.5.x | Composition API + `<script setup>` |
| Vite | 8.x | 下一代前端构建工具（Rolldown引擎） |
| Element Plus | 最新版 | 企业级UI组件库，默认蓝色主题 |
| @element-plus/icons-vue | 最新版 | Element Plus官方图标库 |
| Pinia | 3.x | 状态管理 |
| Vue Router | 4.x | 前端路由，含beforeEach路由守卫 |
| Axios | 1.x | HTTP客户端，统一请求/响应拦截 |
| marked + highlight.js + DOMPurify | - | Markdown渲染 + 代码高亮 + XSS防护 |

### DevOps（生产级运维）

- **Docker + Docker Compose**：多阶段构建，一键启动PostgreSQL+PgVector+Elasticsearch+Logstash+Kibana+Ollama+App全栈
- **ELK日志栈**：Elasticsearch+Logstash+Kibana，JSON日志采集、可视化查询
- **GitHub Actions CI/CD**：自动构建、测试、Docker镜像构建、Trivy漏洞扫描
- **多Profile支持**：dev（本地开发/MySQL）、docker（容器化/PgVector/ELK）
- **健康检查**：Spring Boot Actuator + Docker HEALTHCHECK
- **MDC链路追踪**：每个请求自动生成traceId/spanId，日志中可追踪完整调用链
- **测试覆盖率**：90+单元测试，覆盖JWT、UserService、ConversationService、ChatService等核心模块

---

## 🚀 快速开始

### 前置要求

- JDK 21+
- Maven 3.9+
- Node.js 20+
- MySQL 8.0+（本地开发）或 Docker（一键部署）
- [Ollama](https://ollama.ai)（本地模型，推荐）

### 第一步：启动Ollama本地模型

```bash
# 安装Ollama后，拉取模型
ollama pull qwen2.5:7b          # 中文对话模型（约4.7GB）
ollama pull nomic-embed-text    # 嵌入模型（约274MB，用于RAG）

# 启动Ollama服务（默认监听localhost:11434）
ollama serve
```

### 第二步：初始化数据库

创建MySQL数据库（应用启动时会自动建表，也可手动执行schema.sql）：

```sql
CREATE DATABASE IF NOT EXISTS ai_learn DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 第三步：启动后端

```bash
# 在项目根目录
mvn spring-boot:run
```

后端启动成功后访问：http://localhost:8080
- Swagger API文档：http://localhost:8080/swagger-ui.html
- 健康检查：http://localhost:8080/actuator/health

### 第四步：启动前端（开发模式）

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器（端口5173，自动代理到后端8080）
npm run dev
```

访问 http://localhost:5173 即可使用完整UI界面。

首次使用请先在首页注册账号，然后登录使用所有功能。

### 环境变量配置（可选）

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SERVER_PORT` | 8080 | 服务端口 |
| `SPRING_DATASOURCE_URL` | jdbc:mysql://localhost:3306/ai_learn | 数据库连接URL |
| `SPRING_DATASOURCE_USERNAME` | root | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | root | 数据库密码 |
| `JWT_SECRET` | (内置默认值，生产必须修改) | JWT签名密钥 |
| `TAVILY_API_KEY` | (内置开发Key) | Tavily联网搜索API Key |
| `OLLAMA_CHAT_MODEL` | qwen2.5:7b | Ollama聊天模型 |
| `OLLAMA_EMBEDDING_MODEL` | nomic-embed-text | Ollama嵌入模型 |
| `SPRING_AI_OLLAMA_BASE_URL` | http://localhost:11434 | Ollama服务地址 |
| `LOG_LEVEL` | DEBUG | 日志级别（DEBUG/INFO/WARN/ERROR） |
| `SWAGGER_ENABLED` | true（dev）/false（docker） | 是否启用Swagger文档 |

---

## 🐳 Docker Compose 一键部署（生产级全栈）

一键启动PostgreSQL+PgVector + Elasticsearch + Logstash + Kibana + Ollama + App：

```bash
# 启动所有服务
docker-compose up -d

# 查看应用日志
docker-compose logs -f app

# 停止服务
docker-compose down

# 停止并删除数据卷（重置）
docker-compose down -v
```

### Docker服务访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 应用API | http://localhost:8080 | 后端API和Swagger文档（生产默认关闭） |
| Kibana日志面板 | http://localhost:5601 | ELK日志可视化查询 |
| Elasticsearch | http://localhost:9200 | 日志存储和搜索 |
| Ollama API | http://localhost:11434 | 本地大模型服务 |
| PostgreSQL | localhost:5432 | 数据库（PgVector已启用） |

容器构建采用多阶段构建（Maven构建 → JRE运行时），最终应用镜像仅约300-400MB。

### 日志配置说明

- **控制台输出**：开发环境彩色Pattern，包含traceId/spanId
- **普通文件日志**：`logs/app.%d{yyyy-MM-dd}.%i.log`，按天+大小滚动，单文件100MB，保留30天，总上限10GB
- **JSON日志**：`logs/app-json.%d{yyyy-MM-dd}.%i.log`，Docker环境默认输出，Logstash自动采集到Elasticsearch
- **错误日志**：`logs/error.%d{yyyy-MM-dd}.log`，仅ERROR级别以上日志单独存储

---

## 📁 项目目录结构

```
javaAILearn/
├── pom.xml                                    # Maven配置
├── Dockerfile                                 # Docker多阶段构建
├── docker-compose.yml                         # Docker Compose全栈编排
├── .dockerignore                              # Docker构建忽略文件
├── .github/
│   └── workflows/
│       └── ci-cd.yml                          # GitHub Actions CI/CD流水线
├── docker/
│   ├── app/
│   │   └── Dockerfile                         # 应用Dockerfile
│   └── logstash/
│       ├── pipeline/logstash.conf             # Logstash日志采集管道
│       └── config/logstash.yml                # Logstash配置
├── docs/
│   └── DEPLOYMENT.md                          # 部署指南
├── README.md                                  # 项目文档
├── src/
│   ├── main/
│   │   ├── java/com/ailearn/
│   │   │   ├── AiLearnApplication.java       # 启动类
│   │   │   ├── common/                        # 通用组件
│   │   │   │   ├── Result.java               # 统一响应封装
│   │   │   │   ├── ErrorCode.java            # 错误码枚举
│   │   │   │   ├── BusinessException.java    # 业务异常
│   │   │   │   ├── GlobalExceptionHandler.java # 全局异常处理
│   │   │   │   └── StreamUtils.java          # 流式工具类
│   │   │   ├── config/                        # 配置类
│   │   │   │   ├── AiConfig.java             # AI模型/向量库配置
│   │   │   │   ├── McpServerConfig.java      # MCP工具注册
│   │   │   │   ├── OpenApiConfig.java        # Swagger/OpenAPI配置
│   │   │   │   ├── MdcTraceFilter.java       # MDC链路追踪过滤器
│   │   │   │   ├── SecurityConfig.java       # Spring Security配置
│   │   │   │   ├── RateLimiterConfig.java    # Resilience4j限流配置
│   │   │   │   ├── WebConfig.java            # Web MVC/静态资源配置
│   │   │   │   ├── MyBatisPlusConfig.java    # MyBatis-Plus配置
│   │   │   │   ├── SpaFallbackController.java # SPA路由转发
│   │   │   │   └── SpaFallbackFilter.java    # SPA路由过滤器
│   │   │   ├── security/                      # 安全认证
│   │   │   │   ├── JwtUtil.java              # JWT工具类
│   │   │   │   ├── JwtAuthenticationFilter.java # JWT过滤器
│   │   │   │   └── UserPrincipal.java        # 用户主体
│   │   │   ├── entity/                        # 实体类
│   │   │   │   ├── User.java                 # 用户
│   │   │   │   ├── Conversation.java         # 会话
│   │   │   │   ├── ChatMessage.java          # 聊天消息
│   │   │   │   └── RagDocument.java          # RAG文档
│   │   │   ├── dto/                           # 数据传输对象（含@Schema注解）
│   │   │   ├── mapper/                        # MyBatis Mapper
│   │   │   ├── service/                       # 业务服务
│   │   │   │   ├── ConversationService.java  # 会话管理（用户数据隔离）
│   │   │   │   └── UserService.java          # 用户服务
│   │   │   ├── chat/                          # 基础聊天
│   │   │   ├── memory/                        # 记忆对话
│   │   │   ├── agent/                         # Agent智能体
│   │   │   ├── rag/                           # RAG知识库
│   │   │   ├── tools/                         # 工具实现
│   │   │   ├── mcp/                           # MCP协议
│   │   │   ├── structured/                    # 结构化输出
│   │   │   └── controller/                    # 系统控制器
│   │   └── resources/
│   │       ├── application.yml               # 主配置文件（多profile）
│   │       ├── application-docker.yml        # Docker环境配置（PgVector/ELK）
│   │       ├── logback-spring.xml            # Logback日志配置（控制台/文件/JSON）
│   │       ├── schema.sql                    # MySQL初始化DDL
│   │       ├── schema-postgresql.sql         # PostgreSQL初始化DDL（PgVector）
│   │       └── static/                       # 前端静态资源（构建后生成）
│   └── test/                                 # 单元测试和集成测试
│       ├── java/com/ailearn/
│       │   ├── BaseTest.java                 # 测试基类
│       │   ├── security/JwtUtilTest.java     # JWT工具测试
│       │   ├── service/                      # Service层测试
│       │   ├── chat/ChatServiceTest.java     # 聊天服务测试
│       │   ├── common/                       # 通用组件测试
│       │   ├── tools/                        # 工具测试
│       │   └── controller/                   # Controller集成测试
│       └── resources/
│           └── application-test.yml          # 测试配置（H2内存数据库）
└── frontend/                                  # Vue 3 + Element Plus前端
    ├── package.json
    ├── vite.config.js                        # Vite配置（含API代理）
    └── src/
        ├── App.vue                           # 主布局（el-container）
        ├── main.js                           # 入口（注册Element Plus+图标）
        ├── router/index.js                   # 路由配置（含导航守卫）
        ├── stores/                           # Pinia状态管理（user/chat）
        ├── api/index.js                      # API统一封装
        ├── components/                       # Element Plus组件
        │   ├── ChatMessage.vue              # 消息组件（Markdown+代码高亮+XSS防护）
        │   └── ChatInput.vue                # 输入组件
        ├── views/                            # 页面视图（全Element Plus）
        │   ├── Home.vue                     # 首页（功能介绍+登录注册）
        │   ├── ChatView.vue                 # 基础聊天
        │   ├── MemoryView.vue               # 记忆对话
        │   ├── AgentView.vue                # Agent智能体
        │   ├── SearchAgentView.vue          # 联网搜索Agent
        │   ├── MultiAgentView.vue           # 多Agent协作（el-timeline步骤展示）
        │   ├── RagView.vue                  # RAG知识库（el-upload+el-table）
        │   ├── McpView.vue                  # MCP工具（el-table+el-dialog测试）
        │   ├── StructuredView.vue           # 结构化输出（el-select选择类型）
        │   └── ToolsView.vue                # 工具调用演示
        ├── utils/
        │   └── request.js                   # Axios封装+SSE连接工具（ElMessage提示）
        └── assets/styles/
            └── global.css                   # 全局样式
```

---

## 🏗️ 架构设计

### 多Agent协作流程

```
用户任务
  ↓
Planner（规划专家）
  ├─ 分析任务复杂度（simple/medium/complex）
  ├─ 制定执行计划
  └─ 动态路由决策
       ↓
    ┌──┴──────────────────────────┐
    │ 简单任务                     │ 需要研究
    ↓                             ↓
Executor                  Researcher（研究员）
  ↑                        ├─ 可调用工具（天气/搜索/计算）
  │                        └─ 收集信息（先同步后流式）
  │                             ↓
  │                        ┌─────┴─────────────┐
  │                        │ 需要代码           │ 无需代码
  │                        ↓                    ↓
  │                   Coder（编程专家）     Executor
  │                        ↓
  │                   Critic（审查专家）
  │                   ├─ 代码审查
  │                   ├─ 通过？→ Executor
  │                   └─ 不通过？→ Coder修改（最多3轮迭代）
  │                             ↓
  └────────────────────── Executor（执行专家）
                            └─ 整合所有输出，SSE流式返回最终答案
```

### RAG知识库流程

```
【文档入库】
PDF/Word/Excel/PPT/TXT/MD文件
  → TikaDocumentReader解析
  → TokenTextSplitter按Token切分
  → 添加元数据（documentId/documentName）
  → EmbeddingModel向量化（nomic-embed-text 768维）
  → VectorStore存储（SimpleVectorStore本地/PgVector生产）
  → 元数据持久化到数据库（rag_document表）

【问答流程】
用户问题
  → Query向量化
  → VectorStore相似度检索（Cosine，Top-K）
  → 相关片段作为上下文
  → Prompt组装（系统提示+上下文+问题）
  → LLM生成答案
  → SSE流式返回
```

### 日志链路追踪架构

```
用户请求 → MdcTraceFilter
            ├─ 生成/接收traceId（X-Trace-Id头）
            ├─ 生成spanId
            ├─ 从SecurityContext提取userId
            ├─ 记录requestMethod/requestPath到MDC
            └─ 写入X-Trace-Id响应头
              ↓
         Controller/Service/Repository
            └─ 所有日志自动携带[${traceId}] [${spanId}] [${userId}]
              ↓
         Logback Appender
            ├─ CONSOLE（彩色Pattern）
            ├─ FILE（普通文本，按天滚动）
            ├─ JSON_FILE（LogstashEncoder，供ELK采集）
            └─ ERROR_FILE（错误日志单独存储）
              ↓
         Docker环境：Logstash → Elasticsearch → Kibana可视化
```

### 关键技术方案

1. **流式工具调用兼容性**：针对Ollama qwen模型流式工具调用时`evalDuration is null`的bug，采用"先同步调用获取完整结果，再分块模拟流式输出"的兼容方案
2. **SSE异步认证**：重写JWT过滤器，支持ASYNC dispatch时从request attribute恢复SecurityContext，解决长连接认证丢失问题
3. **"先搜索后总结"模式**：联网搜索Agent绕过LLM工具调用决策，直接调用搜索API获取结果后喂给LLM总结，提升可靠性
4. **接口限流保护**：Resilience4j令牌桶算法，不同服务配置不同限流策略（Agent严格、聊天宽松）
5. **会话持久化与数据隔离**：DatabaseChatMemory实现ChatMemory接口，所有会话和消息表包含userId字段，查询时自动过滤，删除前验证所有权
6. **MDC链路追踪**：每个请求自动注入traceId/spanId/userId到MDC，日志可追踪完整调用链路
7. **多环境配置分离**：dev profile使用MySQL+SimpleVectorStore，docker profile使用PostgreSQL+PgVector+ELK，通过Spring Profile自动切换

---

## 📡 API接口文档

启动后访问 Swagger UI：http://localhost:8080/swagger-ui.html

Swagger UI支持：
- 10个API分组，90+接口
- 点击Authorize按钮输入JWT Token（不需要Bearer前缀）
- 在线调试所有接口
- SSE流式接口直接测试

主要接口分类：

| 分类 | 路径前缀 | 说明 |
|------|---------|------|
| 认证 | `/api/auth` | 登录、注册、Token刷新、用户信息 |
| 基础聊天 | `/api/chat` | 流式/同步对话 |
| 会话管理 | `/api/conversations` | 会话CRUD、历史消息查询 |
| 记忆对话 | `/api/memory` | 带持久化记忆的对话 |
| Agent | `/api/agent` | 单Agent流式工具调用 |
| 联网搜索 | `/api/search-agent` | 联网搜索Agent |
| 多Agent | `/api/multi-agent` | 多Agent协作流式执行 |
| RAG | `/api/rag` | 文档上传/删除/列表/问答 |
| MCP | `/api/mcp` | MCP工具管理、工具调用测试 |
| 结构化输出 | `/api/structured` | 结构化对象/列表输出 |
| 工具演示 | `/api/tools` | 直接调用天气/计算器工具 |
| 系统 | `/api/system` | 健康检查 |
| 监控 | `/actuator` | 健康检查、指标、MCP端点 |

所有SSE流式接口统一格式：纯文本token流，错误以`[ERROR]`前缀开头。

---

## 🧪 测试

### 运行单元测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=JwtUtilTest

# 生成测试报告
mvn test jacoco:report
```

### 测试覆盖范围

- **JwtUtilTest**：JWT令牌生成、验证、过期处理、用户名/过期时间提取（13个测试）
- **UserServiceTest**：用户注册、登录（成功/密码错误）、查询（16个测试）
- **ConversationServiceTest**：会话CRUD、消息保存、所有权验证（13个测试）
- **ChatServiceTest**：同步/流式对话、标题截断、异常处理（9个测试）
- **ResultTest**：统一响应格式（10个测试）
- **CalculatorToolTest**：数学计算（9个测试）
- **WeatherToolTest**：天气工具（6个测试，真实API调用标记@Disabled）
- **GlobalExceptionHandlerTest**：全局异常处理（5个测试）
- **UserControllerTest**：注册/登录/用户信息MockMvc集成测试（8个测试）

**总计：90个测试用例，0失败**

---

## 🔧 开发指南

### 添加新工具

1. 在`com.ailearn.tools`包下创建新类，使用`@Tool`和`@ToolParam`注解
2. 在`McpServerConfig.java`中注册为Spring Bean并加入toolObjects列表
3. Agent即可自动识别并调用新工具

```java
@Tool(description = "工具功能描述")
public String methodName(
    @ToolParam(description = "参数描述", required = false) String param
) {
    // 工具实现
    return result;
}
```

### 切换到云端模型（OpenAI/DeepSeek）

修改`application.yml`，将ollama配置替换为openai配置：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:sk-your-key}
      base-url: ${OPENAI_BASE_URL:https://api.deepseek.com}
      chat:
        options:
          model: ${OPENAI_MODEL:deepseek-chat}
```

### 切换到PgVector（生产级向量存储）

**Docker环境已自动启用**，本地开发如需使用：
1. 启动PostgreSQL + pgvector：`docker-compose up -d postgres-pgvector`
2. 设置环境变量`SPRING_PROFILES_ACTIVE=docker`
3. Spring AI会自动创建vector_store表和HNSW索引

### 运行CI/CD流水线

推送到master/main分支或创建Pull Request时自动触发：
- **backend-build-test**：JDK 21 + Maven构建测试 → Docker镜像构建 → Trivy漏洞扫描
- **frontend-build**：Node.js 20 + npm ci/build → 上传构建产物
- **code-quality**：代码质量检查

---

## ⚠️ 已知问题与注意事项

1. **Ollama流式工具调用bug**：qwen2.5:7b等模型在Spring AI流式模式下调用工具会触发NPE，已采用"先同步后流式"方案绕过
2. **默认JWT密钥**：生产环境务必通过环境变量`JWT_SECRET`设置强密钥（至少256位）
3. **Tavily API Key**：内置开发Key有速率限制，生产请申请自己的Key（免费额度足够个人使用）
4. **嵌入模型维度**：切换嵌入模型时需同步修改PgVector维度配置（nomic-embed-text为768维）
5. **Element Plus替换了赛博朋克主题**：当前使用Element Plus默认蓝色主题，如需定制主题可通过CSS变量覆盖
6. **Elasticsearch内存**：默认ES配置512MB堆内存，生产环境请根据服务器内存调整`ES_JAVA_OPTS`

---

## 📚 参考资料

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI GitHub](https://github.com/spring-projects/spring-ai)
- [Ollama 官方文档](https://github.com/ollama/ollama)
- [Tavily Search API](https://tavily.com/)
- [Open-Meteo Weather API](https://open-meteo.com/)
- [Element Plus 官方文档](https://element-plus.org/)
- [Vue 3 官方文档](https://cn.vuejs.org/)
- [PgVector 官方文档](https://github.com/pgvector/pgvector)
- [ELK Stack 官方文档](https://www.elastic.co/guide/)

---

## 📄 许可证

本项目仅用于学习和研究目的。
