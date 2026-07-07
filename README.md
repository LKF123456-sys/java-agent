# Cyber AI Platform - 赛博AI平台

> 基于 Spring AI 1.0.0 构建的生产级企业级AI应用平台，涵盖多Agent协作、RAG知识库、联网搜索、MCP工具协议等核心能力。赛博朋克风格UI，支持Ollama本地模型一键部署。

---

## ✨ 核心功能

| 模块 | 功能说明 | 访问路径 |
|------|---------|---------|
| 💬 基础聊天 | 流式SSE对话、会话管理、历史记录持久化 | `/chat` |
| 🧠 记忆对话 | 基于ChatMemory的多轮上下文对话 | `/memory` |
| 🤖 Agent智能体 | 支持工具调用（天气/计算器/联网搜索）的ReAct模式Agent | `/agent` |
| 🔍 联网搜索Agent | 基于Tavily API的实时联网搜索，获取最新信息 | `/search` |
| 👥 多Agent协作 | Planner→Researcher→Coder→Critic→Executor五角色协作，支持Critic迭代优化和动态路由 | `/multi-agent` |
| 📚 RAG检索增强 | 文档上传（PDF/Word/TXT）、向量存储、知识库问答、文档管理 | `/rag` |
| 🔧 MCP工具 | Model Context Protocol标准工具协议支持 | `/mcp` |
| 📊 结构化输出 | Java Bean自动映射JSON，支持对象/列表结构化返回 | `/structured` |
| 🛠️ 工具调用 | Function Calling示例：天气查询、数学计算 | `/tools` |

### 🔧 内置工具

- **天气查询**：接入Open-Meteo实时气象API，支持全球城市天气查询和3天预报
- **计算器**：精确数学计算，避免大模型计算错误
- **联网搜索**：Tavily搜索引擎，获取实时新闻、数据、文档
- **系统工具**：获取系统时间、运行环境信息等

---

## 🛠️ 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 使用Record、Text Block、Virtual Thread等新特性 |
| Spring Boot | 3.4.7 | 企业级应用框架 |
| Spring AI | 1.0.0 | AI应用开发核心框架（正式版） |
| Spring Security + JWT | - | 用户认证授权，JJWT 0.12.6 |
| MyBatis-Plus | - | ORM框架，简化数据库操作 |
| MySQL / PostgreSQL | 8.0 / 16 | 关系型数据库，PgVector支持向量存储 |
| Resilience4j | 2.2.0 | 限流熔断保护，防止AI接口被恶意调用 |
| SpringDoc OpenAPI | 2.8.6 | 自动生成Swagger API文档 |
| Ollama | - | 本地大模型运行时，支持qwen2.5等开源模型 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | 3.x | Composition API + `<script setup>` |
| Vite | 5.x | 下一代前端构建工具 |
| Pinia | - | 状态管理 |
| Vue Router | 4.x | 前端路由 |
| Axios | - | HTTP客户端，支持请求/响应拦截 |
| marked + highlight.js | - | Markdown渲染 + 代码高亮 |
| 赛博朋克主题 | - | 霓虹风格UI，自研CSS设计系统 |

### DevOps

- **Docker + Docker Compose**：多阶段构建，一键启动全栈服务
- **多Profile支持**：dev（本地开发/MySQL）、docker（容器化/PgVector）
- **健康检查**：Spring Boot Actuator + Docker HEALTHCHECK
- **非root用户运行**：容器安全最佳实践

---

## 🚀 快速开始

### 前置要求

- JDK 21+
- Maven 3.9+
- Node.js 20+
- MySQL 8.0+ 或 PostgreSQL 16+（带pgvector插件）
- [Ollama](https://ollama.ai) （本地模型，推荐）

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

### 第四步：启动前端（开发模式）

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器（端口5173，自动代理到后端8080）
npm run dev
```

访问 http://localhost:5173 即可使用完整UI界面。

默认账号：可通过注册接口创建新用户，首次启动建议先访问 `/register` 注册。

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

---

## 🐳 Docker Compose 一键部署

使用Docker Compose一键启动PostgreSQL + PgVector + 应用：

```bash
# 基础启动（PostgreSQL + 后端应用）
docker-compose up -d

# 包含Ollama本地模型（需要较大内存和GPU支持）
docker-compose --profile ollama up -d

# 全栈启动（MySQL + PostgreSQL + Ollama + App）
docker-compose --profile mysql --profile ollama up -d

# 查看日志
docker-compose logs -f app

# 停止服务
docker-compose down
```

容器构建采用多阶段构建（前端Node构建 + Maven后端构建 → JRE运行时），最终镜像仅约300-400MB。

---

## 📁 项目目录结构

```
javaAILearn/
├── pom.xml                                    # Maven配置
├── Dockerfile                                 # Docker多阶段构建
├── docker-compose.yml                         # Docker Compose编排
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
│   │   │   │   └── TraceIdFilter.java        # 链路追踪过滤器
│   │   │   ├── config/                        # 配置类
│   │   │   │   ├── AiConfig.java             # AI模型/向量库配置
│   │   │   │   ├── McpServerConfig.java      # MCP工具注册
│   │   │   │   ├── SecurityConfig.java       # Spring Security配置
│   │   │   │   ├── RateLimiterConfig.java    # Resilience4j限流配置
│   │   │   │   ├── WebConfig.java            # Web MVC/静态资源配置
│   │   │   │   └── MyBatisPlusConfig.java    # MyBatis-Plus配置
│   │   │   ├── security/                      # 安全认证
│   │   │   │   ├── JwtUtil.java              # JWT工具类
│   │   │   │   ├── JwtAuthenticationFilter.java # JWT过滤器
│   │   │   │   └── UserPrincipal.java        # 用户主体
│   │   │   ├── entity/                        # 实体类
│   │   │   │   ├── User.java                 # 用户
│   │   │   │   ├── Conversation.java         # 会话
│   │   │   │   ├── ChatMessage.java          # 聊天消息
│   │   │   │   └── RagDocument.java          # RAG文档
│   │   │   ├── dto/                           # 数据传输对象
│   │   │   ├── mapper/                        # MyBatis Mapper
│   │   │   ├── service/                       # 业务服务
│   │   │   │   ├── ConversationService.java  # 会话管理
│   │   │   │   └── UserService.java          # 用户服务
│   │   │   ├── chat/                          # 基础聊天
│   │   │   │   ├── ChatController.java
│   │   │   │   └── ChatService.java
│   │   │   ├── memory/                        # 记忆对话
│   │   │   │   ├── DatabaseChatMemory.java   # 数据库持久化ChatMemory
│   │   │   │   ├── MemoryChatController.java
│   │   │   │   └── MemoryChatService.java
│   │   │   ├── agent/                         # Agent智能体
│   │   │   │   ├── AgentService.java         # 单Agent服务（工具调用）
│   │   │   │   ├── SearchAgentService.java   # 联网搜索Agent
│   │   │   │   ├── MultiAgentService.java    # 多Agent协作服务
│   │   │   │   └── *Controller.java          # 各Agent控制器
│   │   │   ├── rag/                           # RAG知识库
│   │   │   │   ├── RagService.java           # RAG核心服务
│   │   │   │   └── RagController.java
│   │   │   ├── tools/                         # 工具实现
│   │   │   │   ├── WeatherTool.java          # 天气工具（Open-Meteo）
│   │   │   │   ├── CalculatorTool.java       # 计算器工具
│   │   │   │   ├── WebSearchTool.java        # 联网搜索工具（Tavily）
│   │   │   │   └── ToolsController.java
│   │   │   ├── mcp/                           # MCP协议
│   │   │   ├── structured/                    # 结构化输出
│   │   │   └── controller/                    # 系统控制器
│   │   └── resources/
│   │       ├── application.yml               # 主配置文件
│   │       ├── schema.sql                    # 数据库初始化DDL
│   │       └── static/                       # 前端静态资源（构建后生成）
│   └── test/                                 # 单元测试
└── frontend/                                  # Vue 3前端
    ├── package.json
    ├── vite.config.js                        # Vite配置（含API代理）
    └── src/
        ├── App.vue
        ├── main.js
        ├── router/                           # 路由配置
        ├── stores/                           # Pinia状态管理
        ├── components/                       # 组件
        │   ├── CyberLayout.vue              # 全局赛博布局
        │   ├── ChatMessage.vue              # 消息组件（Markdown+代码高亮）
        │   ├── ChatInput.vue                # 输入组件
        │   └── LoginModal.vue               # 登录弹窗
        ├── views/                            # 页面视图
        │   ├── Home.vue                     # 首页
        │   ├── ChatView.vue                 # 基础聊天
        │   ├── MemoryView.vue               # 记忆对话
        │   ├── AgentView.vue                # Agent智能体
        │   ├── SearchAgentView.vue          # 联网搜索Agent
        │   ├── MultiAgentView.vue           # 多Agent协作
        │   ├── RagView.vue                  # RAG知识库
        │   ├── McpView.vue                  # MCP工具
        │   ├── StructuredView.vue           # 结构化输出
        │   └── ToolsView.vue                # 工具调用演示
        ├── utils/
        │   └── request.js                   # Axios封装+SSE连接工具
        └── assets/styles/
            └── cyber-theme.css              # 赛博朋克主题样式
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
  │                        └─ 收集信息
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
                            └─ 整合所有输出，生成最终答案
```

### RAG知识库流程

```
【文档入库】
PDF/Word/TXT/MD文件
  → TikaDocumentReader解析
  → TokenTextSplitter按Token切分
  → 添加元数据（documentId/documentName）
  → EmbeddingModel向量化
  → VectorStore存储（Simple/PgVector）
  → 元数据持久化到MySQL（rag_document表）

【问答流程】
用户问题
  → Query向量化
  → VectorStore相似度检索（Cosine）
  → Top-K相关片段
  → Prompt组装（系统提示+上下文+问题）
  → LLM生成答案
  → SSE流式返回
```

### 关键技术方案

1. **流式工具调用兼容性**：针对Ollama qwen模型流式工具调用时`evalDuration is null`的bug，采用"先同步调用获取完整结果，再分块模拟流式输出"的兼容方案
2. **SSE异步认证**：重写JWT过滤器，支持ASYNC dispatch时从request attribute恢复SecurityContext，解决长连接认证丢失问题
3. **"先搜索后总结"模式**：联网搜索Agent绕过LLM工具调用决策，直接调用搜索API获取结果后喂给LLM总结，提升可靠性
4. **接口限流保护**：Resilience4j令牌桶算法，不同服务配置不同限流策略（Agent严格、聊天宽松）
5. **会话持久化**：DatabaseChatMemory实现ChatMemory接口，将对话历史存储到MySQL，支持跨会话记忆

---

## 📡 API接口文档

启动后访问 Swagger UI：http://localhost:8080/swagger-ui.html

主要接口分类：

| 分类 | 路径前缀 | 说明 |
|------|---------|------|
| 认证 | `/api/auth` | 登录、注册、Token刷新 |
| 基础聊天 | `/api/chat` | 流式/同步对话 |
| 会话管理 | `/api/conversations` | 会话CRUD、历史消息查询 |
| 记忆对话 | `/api/memory` | 带持久化记忆的对话 |
| Agent | `/api/agent` | 单Agent流式工具调用 |
| 联网搜索 | `/api/search-agent` | 联网搜索Agent |
| 多Agent | `/api/multi-agent` | 多Agent协作流式执行 |
| RAG | `/api/rag` | 文档上传/删除/列表/问答 |
| MCP | `/api/mcp` | MCP工具管理 |
| 结构化输出 | `/api/structured` | 结构化对象/列表输出 |
| 监控 | `/actuator` | 健康检查、指标、MCP端点 |

所有SSE流式接口统一格式：纯文本token流，错误以`[ERROR]`前缀开头。

---

## 🔧 开发指南

### 添加新工具

1. 在`com.ailearn.tools`包下创建新类，使用`@Tool`和`@ToolParam`注解
2. 在`McpServerConfig.java`中注册为Spring Bean并加入toolObjects列表
3. Agent即可自动识别并调用新工具

```java
@Tool(description = "工具功能描述")
public String methodName(
    @ToolParam(description = "参数描述") String param
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

1. 启动PostgreSQL + pgvector：使用docker-compose默认启动即可
2. 激活docker profile或配置PgVector连接参数
3. Spring AI会自动创建vector_store表和HNSW索引

---

## ⚠️ 已知问题与注意事项

1. **Ollama流式工具调用bug**：qwen2.5:7b等模型在Spring AI流式模式下调用工具会触发NPE，已采用"先同步后流式"方案绕过
2. **默认JWT密钥**：生产环境务必通过环境变量`JWT_SECRET`设置强密钥
3. **Tavily API Key**：内置开发Key有速率限制，生产请申请自己的Key（免费额度足够个人使用）
4. **嵌入模型维度**：切换嵌入模型时需同步修改PgVector维度配置（nomic-embed-text为768维）

---

## 📚 参考资料

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI GitHub](https://github.com/spring-projects/spring-ai)
- [Ollama 官方文档](https://github.com/ollama/ollama)
- [Tavily Search API](https://tavily.com/)
- [Open-Meteo Weather API](https://open-meteo.com/)
- [Vue 3 官方文档](https://cn.vuejs.org/)

---

## 📄 许可证

本项目仅用于学习和研究目的。
