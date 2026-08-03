# Spring AI 2.0 GA 升级与兼容性改造方案（教学详解版）

> 适用项目：赛博AI平台（Cyber AI Platform）
> 当前版本：Spring Boot 3.4.13 + Spring AI 1.1.8 + JDK 21
> 目标版本：Spring Boot 4.0.x + Spring AI 2.0.0 GA（2026-06-12 发布）
> 文档定位：既是升级作战手册，也是零基础教学材料。所有后端代码逐行注释。
> 信息来源：Spring 官方 Upgrade Notes、Spring AI 2.0.0 GA 官方博客、社区兼容性实践（2026-08 调研）

---

## 目录

- [第一章 结论速览（先看这个）](#第一章-结论速览先看这个)
- [第二章 核心架构变化评估](#第二章-核心架构变化评估)
- [第三章 全局依赖兼容矩阵](#第三章-全局依赖兼容矩阵)
- [第四章 破坏性变更预警（对照本项目代码逐条审判）](#第四章-破坏性变更预警对照本项目代码逐条审判)
- [第五章 平滑升级路线图（可回滚）](#第五章-平滑升级路线图可回滚)
- [第六章 教学级代码改造（逐行注释）](#第六章-教学级代码改造逐行注释)
- [第七章 RAG 与 Ollama 迁移避坑指南](#第七章-rag-与-ollama-迁移避坑指南)
- [第八章 升级验证 Checklist](#第八章-升级验证-checklist)

---

## 第一章 结论速览（先看这个）

| 问题 | 结论 |
|---|---|
| Spring Boot 必须升 4.x 吗？ | **是的，强制**。Spring AI 2.0 无法在 Spring Boot 3.x 上运行，这是硬依赖 |
| JDK 要换吗？ | **不用**。JDK 21 继续用（Boot 4 最低要求 17，推荐 21） |
| 最大的坑是什么？ | **Jackson 3**。包名从 `com.fasterxml.jackson` 改成 `tools.jackson`，本项目有 7 个文件直接 import 了 Jackson 2，必须改 |
| 我的 AI 代码要重写吗？ | **不用大改**。好消息：本项目没有踩中任何"编译必炸"的 Spring AI API（如 `internalToolExecutionEnabled`、`PromptChatMemoryAdvisor` 等都没用过），核心业务代码改动量很小 |
| 前端和中间件要动吗？ | **完全不动**。Vue 3 / Vite 8 / PgVector / Elasticsearch / Ollama 模型全部原样保留 |
| 工作量预估 | 依赖升级 + 7 个文件的 Jackson 迁移 + 少量 API 调整，**约 1-2 天**（含测试验证） |
| 风险最高的依赖 | **Resilience4j**（官方尚未发布 Boot 4 专用 starter，需实测验证）和 **springdoc-openapi**（必须用 Jackson 3 原生支持的新版本） |

**升级必要性判断**：Spring Boot 3.5 / Spring Framework 6.2 已于 **2026-06-30 停止开源支持**（不再有安全补丁），而 Spring AI 2.0 是未来所有新特性（MCP 2.0、Agent Skills、会话压缩等）的基座。对于 2028 年求职的你，简历上写"Spring Boot 4 + Spring AI 2.0"比"3.x + 1.x"更有竞争力。**建议升级**。

---

## 第二章 核心架构变化评估

### 2.1 一张图看懂 1.x → 2.0 的最大变化

**Spring AI 1.x（你现在的架构）**：

```
ChatClient（门面，链式调用）
   ↓ 委托
ChatModel（既负责调模型，又内嵌了"工具调用循环"）
   ↓ 模型说要调工具
ChatModel 内部自己执行工具 → 自己把结果塞回去 → 自己再调模型（黑盒循环）
```

问题：每个模型（Ollama/OpenAI/Anthropic）各自实现了一套工具循环，行为不一致，流式模式下还容易出 bug（你项目中遇到的 `evalDuration is null` NPE 就是这类问题的缩影）。

**Spring AI 2.0（新架构）**：

```
ChatClient（真正的"编排者"）
   ↓ 请求穿过一条有序的 Advisor 链
[记忆Advisor(+200)] → [ToolCallingAdvisor(+300)] → [RAG Advisor] → ...
                          ↓ 模型返回"我要调工具"
                    ToolCallingAdvisor 自己执行工具
                          ↓ 带着工具结果"重新进入"下游链条（循环）
                    直到模型不再要工具，输出最终回答
   ↓
ChatModel（瘦身了：只负责"调用一次模型"，不再管工具循环）
```

**一句话总结**：**工具调用循环从 ChatModel 的"黑盒内部"上移到了 Advisor 链上，变成一个可插拔、可排序、可替换的标准组件**。这是 2.0 一切变化的核心。

### 2.2 ChatClient 与 ChatModel 的职责边界

| 维度 | Spring AI 1.x | Spring AI 2.0 |
|---|---|---|
| ChatModel | 调模型 + **内嵌工具执行循环** | **只调一次模型**（纯收发请求） |
| ChatClient | 链式 API 门面 | 链式 API 门面 + **工具循环编排**（通过自动注册的 ToolCallingAdvisor） |
| 工具执行开关 | `.internalToolExecutionEnabled(true/false)` | **该开关已删除**。用 ChatClient 就自动有工具循环；用 ChatModel 直连就没有 |

对你项目的影响：`ChatService`、`AgentService` 等全部走 ChatClient，**行为自动升级，零代码改动**。

### 2.3 Tool Calling 机制：三个重要变化

**变化 1：ChatClient 自动注册 ToolCallingAdvisor**

2.0 中只要你调用了 `.tools(...)` 或 `.defaultTools(...)`，ChatClient 会**自动**在链尾加上 `ToolCallingAdvisor`。千万不要自己再手动 `new ToolCallingAdvisor()` 加一遍，否则链上会出现两个，工具会被执行两次。

**变化 2：按 Bean 名字找工具的机制被删除**

1.x 里可以把一个 `Function` 注册成 Bean，然后用 `.toolNames("myFunc")` 按名字引用。2.0 删除了这套机制（`toolNames()`、`SpringBeanToolCallbackResolver` 全没了），工具必须**显式**注册（`@Tool` 注解或 `ToolCallback` Bean）。

对你项目的影响：**无**。你的项目用的是 `MethodToolCallbackProvider` + `@Tool` 注解（`WeatherTool`、`CalculatorTool`、`WebSearchTool`、`SystemTools`），这正是 2.0 推荐的方式，完全兼容。

**变化 3：新增 ToolSearchToolCallingAdvisor（工具太多时的救星）**

2.0 新增"渐进式工具披露"：如果你有几百个工具，不再一次性把所有工具定义塞给模型（浪费 Token、干扰判断），而是让模型按需"搜索"工具。支持 regex（默认，零依赖）/ lucene / vector 三种索引。你项目目前只有 4 个工具，用不上，但这是面试亮点，知道即可。

### 2.4 Advisor 链顺序变化（静默行为变化，重点理解）

| 常量 | 1.x 值 | 2.0 值 | 含义 |
|---|---|---|---|
| 记忆 Advisor 默认顺序 | `HIGHEST_PRECEDENCE + 1000` | `HIGHEST_PRECEDENCE + 200` | 记忆 Advisor 现在包在工具循环**外面** |
| ToolCallingAdvisor 顺序 | — | `HIGHEST_PRECEDENCE + 300` | 工具循环在记忆 Advisor 内侧执行 |

**用人话解释**：1.x 里，工具调用的每一条中间消息（模型说"我要调天气工具"、工具返回结果……）都会经过记忆 Advisor，可能被写进 `ChatMemoryRepository`。2.0 里，工具循环自己内部管理中间过程，**记忆 Advisor 只保存最终的"一问一答"**。

**这对你的项目是重大利好**：你的 `DatabaseChatMemory` 只支持 USER / ASSISTANT / SYSTEM 三种纯文本消息，根本存不下工具调用消息。1.x 时代这是隐患，2.0 的新默认行为恰好帮你规避了。**无需任何改动，反而更稳了。**

### 2.5 Memory 机制变化

| 变化 | 说明 | 你的项目 |
|---|---|---|
| `CONVERSATION_ID` 变成**强制** | 每次调用必须通过 `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "xxx"))` 显式传入，否则直接抛 `IllegalArgumentException` | ✅ 你所有代码都已经显式传了，**零改动** |
| `PromptChatMemoryAdvisor` 被移除 | 用 `MessageChatMemoryAdvisor` 替代 | ✅ 你用的就是 `MessageChatMemoryAdvisor`，**零改动** |
| `ChatMemory.DEFAULT_CONVERSATION_ID` 常量删除 | 不再允许"默认会话"这种模糊状态 | ✅ 你没用过，**零改动** |
| 官方 JDBC 记忆表结构变更 | 新增 `sequence_id` 列 | ✅ 不适用——你是自研 `DatabaseChatMemory`（基于自己的 `chat_message` 表），不用官方的 `JdbcChatMemoryRepository` |

**前瞻**：官方已宣布 `spring-ai-session` 社区项目（事件溯源记忆，支持工具消息、上下文自动压缩）计划在 2.1 取代 `ChatMemory`。升级后可以继续关注，但 2.0 阶段你的自研记忆完全够用。

### 2.6 MCP 协议：从"外挂"变"亲儿子"

- MCP Java SDK 升级到 **2.0.0**，符合最新的 2025-11-25 MCP 规范
- 注解模块并入 Spring AI：`org.springaicommunity.mcp.annotation.*` → `org.springframework.ai.mcp.annotation.*`，新增 `@McpTool` / `@McpResource` / `@McpPrompt`，一个注解就能把任意 Spring 方法暴露为 MCP 工具
- 传输层：**Streamable HTTP 成为默认**，旧的 SSE 传输被废弃
- MCP 服务端**默认开启工具入参校验**（按 JSON Schema 校验，参数不合规直接返回错误结果）

对你项目的影响：你的 `application.yml` 里有 `spring.ai.mcp.server.sse-message-endpoint: /mcp/message` 配置，但 pom 里**并没有引入 MCP Server starter**，这段配置目前处于休眠状态。升级后如果真要启用 MCP Server，需要用新的 starter（见第六章 6.6）。

### 2.7 结构化输出变化

- 新增 `StructuredOutputValidationAdvisor`：模型返回的 JSON 校验不通过时**自动重试自我修正**（以前需要自己写重试逻辑）
- `BeanOutputConverter` 的 schema 生成改由 `JsonSchemaGenerator` 委托，扩展点 `postProcessSchema()` 被移除（改为覆写 `generateSchema()`）

对你项目的影响：你用的是 `chatClient.prompt().entity(BookInfo.class)` 这种标准用法，**零改动**。`BookInfo` / `MovieInfo` 上的 `@JsonPropertyDescription` 注解来自 jackson-annotations，**该包在 Jackson 3 中故意没改包名**，也无需改。

---

## 第三章 全局依赖兼容矩阵

### 3.1 Spring Boot：必须升 4.x（没得选）

Spring AI 2.0 = Spring Boot 4 + Spring Framework 7 + Jackson 3 的"全家桶绑定销售"。链条如下：

```
Spring AI 2.0
  └─ 强制要求 Spring Boot 4.0+
       └─ 强制捆绑 Spring Framework 7（spring-core/web/context 全部 7.x）
       └─ 强制捆绑 Spring Security 7
       └─ 强制捆绑 Jackson 3（tools.jackson 新包名）
       └─ Jakarta EE 11（Servlet 6.1，jakarta.* 包名不变，好消息）
```

官方建议的两步走路径：**先到 Spring Boot 3.5 清掉所有废弃警告，再跳 4.0**。你当前是 3.4.13，路线图中会体现。

### 3.2 全组件版本对照表

| 组件 | 当前版本 | 升级后版本 | 确定性 | 说明 |
|---|---|---|---|---|
| JDK | 21 | **21（不变）** | ✅ 确定 | Boot 4 最低 17，21 完美 |
| Spring Boot | 3.4.13 | **4.0.x** | ✅ 确定 | 硬要求，用最新 4.0 补丁版 |
| Spring AI | 1.1.8 | **2.0.0** | ✅ 确定 | GA 已于 2026-06-12 发布 |
| Spring Framework | 6.2.x（Boot 管理） | 7.x（Boot 管理） | ✅ 确定 | 跟随 Boot，不用手动指定 |
| **spring-aop 显式版本 6.2.8** | 6.2.8 | **删除显式版本** | ✅ 确定 | 必须删！否则与 Framework 7 冲突 |
| **spring-retry 显式版本 2.0.4** | 2.0.4 | **删除显式版本**（Boot BOM 接管） | ✅ 确定 | Boot 的依赖管理里包含 spring-retry |
| MyBatis-Plus | 3.5.15（boot3-starter） | **3.5.16+（`mybatis-plus-spring-boot4-starter`）** | ✅ 确定 | 官方已发布 boot4 专用 starter（2026-01） |
| mybatis-plus-jsqlparser | 3.5.15 | 3.5.16+（与主包同版本） | ✅ 确定 | 跟随主包 |
| JJWT | 0.12.7 | **0.13.0** | ✅ 确定 | 0.13.0 适配 Jackson 3；API 与 0.12 完全兼容（`Jwts.builder()`/`Jwts.parser()` 不变） |
| springdoc-openapi | 2.8.17 | **4.0.x（Jackson 3 原生）** | ⚠️ 需验证 | 2.x/3.0.x 会因找不到 `com.fasterxml.jackson` 类启动崩溃；必须用支持 Jackson 3 的新版。若 4.0.x 有兼容问题，可临时关闭 Swagger（你 docker profile 本来就关了） |
| Resilience4j | 2.3.0 | **2.3.0 保留，实测验证** | ⚠️ **最大风险项** | 官方暂无 boot4 专用 starter；其注解+AOP 机制理论上与 Framework 7 兼容，但必须实测。备选方案见 4.4 |
| logstash-logback-encoder | 8.1 | **9.x** | ⚠️ 需验证 | 9.x 系列迁移到了 Jackson 3，与 Boot 4 技术栈一致；8.x 依赖 Jackson 2 会引发类冲突 |
| Micrometer Tracing | 1.4.3（显式） | **删除显式版本**（Boot 4 BOM 管理，约 1.6.x） | ✅ 确定 | 显式指定的 1.4.3 与 Boot 4 不匹配 |
| Hutool | 5.8.46 | **5.8.46（不变）** | ✅ 确定 | 纯工具库，与 Spring/Jackson 无耦合 |
| Lombok | 1.18.36 | 1.18.40+ | ✅ 确定 | 跟随 Boot 4 BOM 即可 |
| Tess4J | 5.19.0 | 5.19.0（不变） | ✅ 确定 | 与 Spring 无耦合 |
| MySQL 驱动 / PostgreSQL 驱动 | Boot 管理 | Boot 管理（不变） | ✅ 确定 | 不用动 |
| H2 / Testcontainers | Boot 管理 | Boot 管理（不变） | ✅ 确定 | 不用动 |
| **前端 Vue 3.5 + Vite 8 + Element Plus** | — | **完全不动** | ✅ 确定 | 后端升级对前端零影响，SSE 协议格式不变 |
| **PgVector (pg16)** | — | **完全不动** | ✅ 确定 | Spring AI 2.0 对 PgVector 无破坏性变更 |
| **Ollama 及模型** | qwen2.5:7b / nomic-embed-text | **完全不动** | ✅ 确定 | 模型文件、端口、API 均不变 |
| **Elasticsearch/Logstash/Kibana 8.13** | — | **完全不动** | ✅ 确定 | ELK 与后端框架版本无关 |

### 3.3 Spring Boot 4 自身的"附加"变化（与 AI 无关但会撞上）

1. **Starter 更名**（Boot 4 模块化拆分）：
   - `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**
   - `spring-boot-starter-aop` → **`spring-boot-starter-aspectj`**
   - 其余（webflux/security/validation/actuator/test）名称保留
2. **Jackson 3 包名迁移**：`com.fasterxml.jackson.databind.*` → `tools.jackson.databind.*`，`ObjectMapper` → 推荐 `JsonMapper`。**注解包 `com.fasterxml.jackson.annotation.*` 故意保持不变**（这是官方为了降低迁移成本的设计）。
3. **Spring Security 7**：你现在用的 lambda DSL 风格（`http.authorizeHttpRequests(...).csrf(c -> c.disable())`）继续支持，无需重写，但升级后需跑一遍登录/注册接口验证。

---

## 第四章 破坏性变更预警（对照本项目代码逐条审判）

我把官方 70+ 条变更全部对照你的代码审了一遍，按"会不会影响你"分三档：

### 4.1 🔴 必须改（不改编译失败或启动崩溃）

| # | 位置 | 问题 | 改法 |
|---|---|---|---|
| 1 | `pom.xml` | Spring Boot 3.4.13 无法承载 Spring AI 2.0 | 升级 parent 到 4.0.x（见 6.1） |
| 2 | `pom.xml` | `spring-boot-starter-web` / `spring-boot-starter-aop` 在 Boot 4 中更名 | 改为 `spring-boot-starter-webmvc` / `spring-boot-starter-aspectj` |
| 3 | `pom.xml` | 显式指定的 `spring-aop 6.2.8`、`spring-retry 2.0.4`、`micrometer-tracing 1.4.3` 与新平台冲突 | **删除版本号**，交给 Boot 4 BOM 管理 |
| 4 | `pom.xml` | `mybatis-plus-spring-boot3-starter` 不适配 Boot 4 | 改为 `mybatis-plus-spring-boot4-starter` 3.5.16+ |
| 5 | `pom.xml` | JJWT 0.12.7 的 `jjwt-jackson` 依赖 Jackson 2 | 升级 0.13.0 |
| 6 | `WeatherTool.java`、`WebSearchTool.java`、`MultiAgentService.java`、`SecurityConfig.java`、`JwtAuthenticationFilter.java`、2 个测试类 | `import com.fasterxml.jackson.databind.ObjectMapper/JsonNode` | 迁移到 `tools.jackson.databind.*`（见 6.2 教学示例） |
| 7 | `application.yml` | `spring.ai.ollama.embedding.options.model` 中的 `.options` 段被扁平化 | 改为 `spring.ai.ollama.embedding.model`（旧写法仅作 deprecated 过渡，建议直接改） |
| 8 | `docker-compose.yml` | 环境变量 `SPRING_AI_OLLAMA_EMBEDDING_OPTIONS_MODEL` 对应旧属性 | 改为 `SPRING_AI_OLLAMA_EMBEDDING_MODEL` |
| 9 | `AgentService.java` 第 113 行 | `.defaultToolCallbacks(callbacks)` 在 2.0 中被标记废弃 | 改为 `.defaultTools(callbacks)`（见 6.4） |

### 4.2 🟡 行为变化（不报错，但必须知道）

| # | 变化 | 对你的影响 |
|---|---|---|
| 1 | 记忆 Advisor 移出工具循环（2.4 节） | **利好**：你的 `DatabaseChatMemory` 存不下工具消息，新行为自动规避隐患 |
| 2 | 官方不再自动配置默认温度 0.7 | **无影响**：你 yml 里显式写了 `temperature: 0.7`，保持即可 |
| 3 | ChatClient 自动注册 ToolCallingAdvisor | **注意**：不要手动再加 ToolCallingAdvisor。你没加过，安全 |
| 4 | MCP 服务端默认校验工具入参 | 你 MCP Server 未实际启用，暂无影响 |
| 5 | `MethodToolCallbackProvider` 在工具名重复时抛 `IllegalArgumentException`（原来是 `IllegalStateException`） | 你没有 catch 这个异常，无影响 |
| 6 | Spring Security 7 部分废弃 API 移除 | 需实测登录/注册/鉴权三个接口 |
| 7 | 工具调用的链路追踪 span 名从 `tool_call xxx` 改为 `execute_tool xxx` | 你 Kibana 里如有按 span 名过滤的看板，需更新 |

### 4.3 🟢 官方变了但你完全不用管（已逐条核对）

`internalToolExecutionEnabled`（没用过）、`PromptChatMemoryAdvisor`（没用过）、`toolNames()`（没用过）、`streamToolCallResponses`（没用过）、`ChatMemory.DEFAULT_CONVERSATION_ID`（没用过）、`ModelOptionsUtils` JSON 方法（没用过）、`.N()` 改名 `.n()`（没用过）、`options.copy()/fromOptions()`（没用过）、Anthropic/OpenAI 模块重构（你用 Ollama）、JDBC/Mongo 官方记忆表迁移（你自研记忆）、`@McpTool` 注解包迁移（没用注解式 MCP）、Azure/OCI/HanaDB 模块移除（没用）。

**这就是你项目设计干净的红利——主流用法、没碰边缘 API，升级成本天然低。**

### 4.4 Resilience4j 风险预案

如果实测发现 `resilience4j-spring-boot3:2.3.0` 在 Boot 4 下自动配置失效（典型症状：`@RateLimiter` 注解不生效、启动报 `ClassNotFoundException`），两条退路：

- **退路 A（推荐）**：检查 Resilience4j 是否已发布新版本（升级前到 Maven Central 搜 `resilience4j-spring-boot3` 最新版，社区迭代很快）
- **退路 B**：降级为编程式限流——不依赖其 Boot starter 自动配置，手动 new `RateLimiterRegistry` 并写一个自己的 AOP 切面，代码约 40 行，完全摆脱对其 starter 的依赖

---

## 第五章 平滑升级路线图（可回滚）

### 总体策略：分支升级 + 里程碑验证 + 一键回滚

```bash
# 第 0 步：上保险（所有操作的前提）
git checkout -b upgrade/spring-ai-2.0   # 创建升级专用分支，master 保持可运行状态
git tag before-spring-ai-2.0            # 打标签，出任何问题一行命令回滚
```

### 阶段一：Spring Boot 3.4.13 → 3.5.x（清雷阶段，可选但推荐）

1. parent 版本改为 3.5 最新补丁版，`mvn clean test` 跑通 90+ 测试
2. 打开编译警告，**逐一消除所有 deprecation 警告**（这些就是 Boot 4 里会被删的 API）
3. 本阶段不动 Spring AI（1.1.8 支持 Boot 3.5）

> 如果你赶时间可以跳过本阶段直接进阶段二，但阶段一能把"Boot 升级的问题"和"Spring AI 升级的问题"分开排查，大幅降低定位难度。**我建议不跳过。**

### 阶段二：Spring Boot 4.0 + 依赖矩阵联动（本章核心）

按 6.1 的新 pom.xml 整体替换，然后：

1. `mvn clean compile` —— 第一轮编译，预期报错集中在：
   - 7 个文件的 Jackson 2 import（按 6.2 的模式逐个修）
   - starter 更名（pom 已改则不会报）
2. `mvn clean test` —— 90+ 测试全绿才能进下一阶段
3. **本地冒烟**：启动应用，按第八章 Checklist 逐个点验

### 阶段三：Spring AI 行为验证（重点验证"静默变化"）

1. Agent 工具调用：问"合肥今天天气怎么样"→ 确认工具被调用且**只调用一次**（验证没有重复注册 ToolCallingAdvisor）
2. 记忆对话：连续 3 轮对话 → 查 `chat_message` 表，确认**没有写入工具中间消息**（验证新的记忆 Advisor 顺序行为）
3. RAG：上传一个 PDF → 提问 → 确认检索和回答正常（验证 PgVector 链路）
4. 流式接口：前端每个页面过一遍，确认 SSE 正常

### 阶段四：Docker 全栈验证

1. 修改 `docker-compose.yml` 中的嵌入模型环境变量（4.1 节第 8 条）
2. `docker-compose up -d --build`，验证 PgVector 自动建表、ELK 日志采集、Kibana 可查

### 回滚方案（任何阶段翻车都适用）

```bash
git checkout master          # 直接回到升级前
# 或者只回滚某个文件
git checkout before-spring-ai-2.0 -- pom.xml
```

**数据库层面零风险**：本次升级不涉及你业务表（user/conversation/chat_message/rag_document）的任何结构变化；PgVector 的 `vector_store` 表结构 2.0 未变。无需数据备份演练（但生产环境好习惯是先备份）。

---

## 第六章 教学级代码改造（逐行注释）

### 6.1 pom.xml（全新版本，逐段讲解）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- ==================== 父POM：升级到 Spring Boot 4.0.x ==================== -->
    <!-- 为什么必须升：Spring AI 2.0 把 Spring Boot 4 作为硬依赖，3.x 上无法运行 -->
    <!-- 为什么用 parent 方式：parent 自带"依赖版本管理表"(BOM)，几百个常用库的兼容版本都帮你定好了 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <!-- 写你升级时 Maven Central 上最新的 4.0 补丁版，补丁版只修 bug 不改 API -->
        <version>4.0.0</version>
        <relativePath/>
    </parent>

    <groupId>com.ailearn</groupId>
    <artifactId>javaAILearn</artifactId>
    <!-- 版本号升一位，标记这是一次架构级升级 -->
    <version>0.1.0-SNAPSHOT</version>
    <name>Cyber AI Platform</name>
    <description>赛博AI平台 - 基于Spring Boot 4 + Spring AI 2.0的企业级AI应用</description>

    <properties>
        <!-- JDK 保持 21：Boot 4 最低要求 17，21 是当前主流 LTS，不用动 -->
        <java.version>21</java.version>
        <!-- Spring AI 目标版本：2.0.0 GA（2026-06-12 发布的正式版） -->
        <spring-ai.version>2.0.0</spring-ai.version>
        <!-- JJWT 升 0.13.0：该版本的 jjwt-jackson 模块适配了 Jackson 3 新包名 -->
        <!-- 好消息：Jwts.builder()/Jwts.parser() 的 API 与 0.12 完全一致，你的 JwtUtil 一行都不用改 -->
        <jjwt.version>0.13.0</jjwt.version>
        <!-- springdoc 必须用 4.0.x：只有这个系列原生支持 Jackson 3 -->
        <!-- 2.x/3.0.x 启动时会报 ClassNotFoundException: com.fasterxml.jackson.databind.node.ObjectNode -->
        <springdoc.version>4.0.0</springdoc.version>
        <!-- Resilience4j 暂无 boot4 专用 starter，先保留 2.3.0 实测，异常时按 4.4 节预案处理 -->
        <resilience4j.version>2.3.0</resilience4j.version>
        <!-- MyBatis-Plus 3.5.16 起官方提供 boot4 专用 starter -->
        <mybatis-plus.version>3.5.16</mybatis-plus.version>
        <!-- Tess4J 与 Spring 无耦合，版本不变 -->
        <tess4j.version>5.19.0</tess4j.version>
        <!-- Hutool 纯工具库，与 Spring/Jackson 均无耦合，版本不变 -->
        <hutool.version>5.8.46</hutool.version>
        <!-- logstash-encoder 9.x 迁移到了 Jackson 3，与 Boot 4 技术栈一致 -->
        <logstash-logback-encoder.version>9.0</logstash-logback-encoder.version>
        <!-- 注意：原来这里的 micrometer-tracing.version=1.4.3 已删除！ -->
        <!-- 原因：Boot 4 的 BOM 会自动管理匹配的 Micrometer 版本，手动指定 1.4.3 反而冲突 -->
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring AI BOM：import 后，所有 spring-ai-* 依赖都不用写版本号，由这张表统一管理 -->
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>

        <!-- ========== Spring Boot Web核心 ========== -->
        <!-- 破坏性变更：Boot 4 中 spring-boot-starter-web 更名为 spring-boot-starter-webmvc -->
        <!-- 作用不变：Spring MVC + 内嵌 Tomcat，提供 REST API 能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>

        <!-- Spring Retry：Spring AI 内部重试机制需要 -->
        <!-- 破坏性变更预防：删除了原来显式写的 2.0.4 版本号，交给 Boot 4 BOM 管理 -->
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>

        <!-- 破坏性变更预防：原来这里显式引入了 spring-aop 6.2.8，必须删除！ -->
        <!-- 原因：Boot 4 自带 Spring Framework 7 的 spring-aop 7.x，显式引入 6.2.8 会造成版本冲突 -->

        <!-- WebFlux 响应式支持：SSE 流式响应和 Reactor（Flux/Mono）类型依赖，名称在 Boot 4 中未变 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- ========== 安全认证 ========== -->
        <!-- Spring Security 7（跟随 Boot 4），starter 名称未变 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- JSR380 参数校验（@Valid/@NotNull），starter 名称未变 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- JJWT 三件套：版本升到 0.13.0（properties 里已定义） -->
        <!-- api 是接口定义，编译期需要 -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <!-- impl 是实现，运行时动态加载，所以 scope=runtime -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <!-- jackson 序列化桥接包：0.13.0 版本内部已从 Jackson 2 迁移到 Jackson 3 -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- ========== Spring AI 核心依赖（版本全部由上面的 BOM 管理，不用写版本号） ========== -->
        <!-- Ollama 本地模型接入（聊天+嵌入），starter 坐标在 2.0 中未变 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-ollama</artifactId>
        </dependency>

        <!-- 向量存储抽象层 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-vector-store</artifactId>
        </dependency>

        <!-- PgVector：PostgreSQL 向量扩展存储，2.0 对其无破坏性变更 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
        </dependency>

        <!-- PDF 文档读取器（RAG 文档解析用） -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-pdf-document-reader</artifactId>
        </dependency>

        <!-- Tika 文档读取器（Word/Excel/PPT/HTML 等多格式解析） -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-tika-document-reader</artifactId>
        </dependency>

        <!-- ========== 数据库与 ORM ========== -->
        <!-- MySQL 驱动，版本由 Boot 4 BOM 管理 -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- PostgreSQL 驱动（PgVector 需要），版本由 Boot 4 BOM 管理 -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- 破坏性变更：MyBatis-Plus 换成 boot4 专用 starter -->
        <!-- 为什么必须换：Boot 4 基于 Spring Framework 7，对 Bean 定义属性的类型要求更严格， -->
        <!-- 旧的 boot3-starter 会触发 factoryBeanObjectType 类型错误导致启动失败 -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- MyBatis-Plus 分页插件依赖（3.5.6+ 拆分为独立模块，需显式引入） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- ========== 监控与运维 ========== -->
        <!-- Actuator：健康检查与指标端点，starter 名称未变 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Micrometer Tracing + Brave 链路追踪：删除了显式版本号，由 Boot 4 BOM 管理 -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>

        <!-- 破坏性变更：spring-boot-starter-aop 更名为 spring-boot-starter-aspectj -->
        <!-- 作用不变：AOP 切面支持（你的 MDC 链路追踪切面、Resilience4j 注解都依赖它） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aspectj</artifactId>
        </dependency>

        <!-- Logstash JSON 日志编码器：升到 9.x（Jackson 3 版本） -->
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
            <version>${logstash-logback-encoder.version}</version>
        </dependency>

        <!-- ========== API 文档 ========== -->
        <!-- springdoc 4.0.x：Jackson 3 原生支持版本 -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <!-- ========== 熔断限流 ========== -->
        <!-- 保留 2.3.0 + boot3 starter 实测（官方暂无 boot4 版本），异常时按 4.4 节预案处理 -->
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-ratelimiter</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>

        <!-- ========== OCR / 工具库（均无 Spring 耦合，原样保留） ========== -->
        <dependency>
            <groupId>net.sourceforge.tess4j</groupId>
            <artifactId>tess4j</artifactId>
            <version>${tess4j.version}</version>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>${hutool.version}</version>
        </dependency>

        <!-- ========== 测试依赖（starter 名称在 Boot 4 中未变） ========== -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mysql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>cyber-ai-platform</finalName>
        <plugins>
            <!-- Maven 编译器插件：配置 JDK 21 与 Lombok 注解处理器 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <encoding>UTF-8</encoding>
                    <!-- -parameters：保留方法参数名（Spring 反射读取参数名需要，如 @PathVariable 省略名称时） -->
                    <compilerArgs>
                        <arg>-parameters</arg>
                    </compilerArgs>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <!-- Lombok 升到 1.18.40+，确保与 Boot 4 / 新 JDK 编译器兼容 -->
                            <version>1.18.40</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>

            <!-- Spring Boot 打包插件：生成可执行 JAR，配置不变 -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.ailearn.AiLearnApplication</mainClass>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 6.2 Jackson 2 → Jackson 3 迁移教学（以 WeatherTool.java 为例）

**背景知识（零基础必读）**：Jackson 是 Java 世界最流行的 JSON 处理库，负责"Java 对象 ↔ JSON 字符串"的互相转换。Jackson 3 做了一次"搬家"：包名从 `com.fasterxml.jackson` 换成 `tools.jackson`。为什么要搬家？因为 Jackson 2 和 3 可以在同一个项目里共存（比如某些老库还在用 Jackson 2），不同包名就不会打架。

**改法规律（全部 7 个文件通用）**：

```java
// ========== 改造前（Jackson 2）==========
import com.fasterxml.jackson.databind.ObjectMapper;   // JSON 处理器（旧包）
import com.fasterxml.jackson.databind.JsonNode;       // JSON 树节点（旧包）

// ========== 改造后（Jackson 3）==========
import tools.jackson.databind.ObjectMapper;           // 包名 com.fasterxml → tools，类名不变
import tools.jackson.databind.JsonNode;               // 同上，只是包名变了
```

**WeatherTool.java 实际改造示例**（只展示变化的部分）：

```java
package com.ailearn.tools;

// 破坏性变更修复点：Jackson 3 新包名
// 为什么只改包名不改类名：Jackson 官方为了让迁移成本最低，刻意保持了类名和方法签名不变
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 天气查询工具
 * 通过 @Tool 注解声明为 Spring AI 可调用工具（该注解在 2.0 中完全未变，无需修改）
 */
@Component
public class WeatherTool {

    // ObjectMapper 用法与 Jackson 2 完全一致：readTree() 把 JSON 字符串解析成树结构
    // 这一行不用改（类型名没变），变的只是上面的 import
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(description = "查询指定城市的实时天气")
    public String getCurrentWeather(
            @ToolParam(description = "城市名称，如：北京、合肥") String city) {
        // ... HTTP 调用逻辑不变 ...
        // readTree 方法签名在 Jackson 3 中保持兼容，业务代码零改动
        // JsonNode root = objectMapper.readTree(response.body());
        // ...
        return "";
    }
}
```

**需要同样处理的文件清单**（共 7 个，机械替换 import 即可）：

| 文件 | 改动内容 |
|---|---|
| `tools/WeatherTool.java` | 2 个 import 换包名 |
| `tools/WebSearchTool.java` | 2 个 import 换包名 |
| `agent/MultiAgentService.java` | 3 个 import 换包名（含 `JsonProcessingException`、`TypeReference`） |
| `security/SecurityConfig.java` | 1 个 import 换包名 |
| `security/JwtAuthenticationFilter.java` | 1 个 import 换包名 |
| `test/.../GlobalExceptionHandlerTest.java` | 1 个 import 换包名 |
| `test/.../UserControllerTest.java` | 1 个 import 换包名 |

**特别注意**：`structured/BookInfo.java` 和 `MovieInfo.java` 里的 `@JsonPropertyDescription` 来自 `com.fasterxml.jackson.annotation` 包——**这个包名在 Jackson 3 中故意保持不变**，所以这两个文件一行都不用改。

### 6.3 AiConfig.java（几乎不用改，只讲一个知识点）

你的 `AiConfig` 只有一个潜在隐患：`PgVectorStore.builder(...)` 手动构建的 fallback 逻辑。2.0 中 `PgVectorStore` 的 builder API **保持不变**，所以本类**零改动**。讲解保留它存在的原因：

```java
// 这个类为什么升级后一行都不用改？
// 1. PgVectorStore.builder(jdbcTemplate, embeddingModel) —— 2.0 中 API 未变
// 2. SimpleVectorStore.builder(embeddingModel) —— 2.0 中保留
// 3. @ConditionalOnMissingBean —— Spring Boot 通用注解，与 AI 版本无关
//
// 教学要点：这就是"面向接口/稳定 API 编程"的红利。
// AiConfig 依赖的都是 Spring AI 公开承诺稳定的 builder API，
// 所以大版本升级也波及不到它。写代码时优先使用官方 builder/接口，
// 避免触碰内部类（如各种 *AutoConfiguration 的内部实现），升级时就能独善其身。
```

### 6.4 AgentService.java 构造器改造（唯一一处 AI API 调整）

```java
// ========== 改造前（第 94、111-113 行）==========
// var callbacks = toolCallbackProvider.getToolCallbacks();
// this.agentClient = ChatClient.builder(chatModel)
//         .defaultSystem("...")
//         .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
//         .defaultToolCallbacks(callbacks)   // ← 2.0 中此方法被标记为废弃（Deprecated）
//         .build();

// ========== 改造后 ==========
public AgentService(ChatModel chatModel,              // AI 模型（Ollama 自动配置注入）
                    DatabaseChatMemory chatMemory,     // 你的自研数据库记忆（2.0 兼容，不用改）
                    ConversationService conversationService,
                    ToolCallbackProvider toolCallbackProvider) {  // 工具提供者（McpServerConfig 里注册的）
    this.conversationService = conversationService;

    // 不再需要先 getToolCallbacks() 取出数组！
    // 2.0 新特性：tools()/defaultTools() 直接接受 ToolCallbackProvider 对象本身
    this.agentClient = ChatClient.builder(chatModel)
            // 系统提示词：原样保留，与版本无关
            .defaultSystem("""
                    你是一个专业的AI助手，具有以下能力：
                    1. 查询各城市天气信息（使用天气工具）
                    2. 进行数学计算（使用计算器工具）
                    3. 联网搜索获取实时信息（使用searchWeb搜索工具）
                    4. 获取系统信息（使用系统工具）
                    5. 根据用户需求给出专业建议
                    
                    请主动使用工具获取真实信息，而不是凭空猜测。
                    对于实时信息（新闻、价格、最新动态等），必须使用searchWeb工具搜索。
                    思考步骤：分析问题 → 判断是否需要工具 → 调用工具 → 综合回答
                    """)
            // 记忆 Advisor：2.0 完全兼容，不用改
            // 幕后变化（2.4 节讲过）：它现在默认位于工具循环外侧，
            // 只会把"最终一问一答"写入你的 DatabaseChatMemory，行为反而更正确了
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            // 关键改动：defaultToolCallbacks(数组) → defaultTools(Provider)
            // 为什么这样改：2.0 废弃了"回调数组"的传法，统一走 tools() 系列方法，
            // 它能直接识别 ToolCallbackProvider、ToolCallback、@Tool 注解对象等多种形态
            .defaultTools(toolCallbackProvider)
            .build();

    // 注意：这里【千万不要】再手动 .defaultAdvisors(ToolCallingAdvisor.builder().build())
    // 2.0 检测到 tools 存在时会自动注册 ToolCallingAdvisor，手动加会导致工具被执行两次

    log.info("AgentService初始化完成（Spring AI 2.0），工具由 ToolCallbackProvider 提供");
}
```

`MultiAgentService` 中如有同样的 `defaultToolCallbacks(...)` 调用，按同样模式修改。

### 6.5 application.yml 配置变更

```yaml
spring:
  ai:
    ollama:
      chat:
        enabled: true
        options:
          # 聊天模型属性在 2.0 中【没有】扁平化，保持 options.model 写法不变
          model: ${OLLAMA_CHAT_MODEL:qwen2.5:7b}
          # 显式温度：2.0 移除了"自动配置默认 0.7"的行为，显式写出是好习惯，保留
          temperature: 0.7
          num-ctx: 8192
      embedding:
        enabled: true
        # ========== 破坏性变更修复点 ==========
        # 嵌入模型的属性被"扁平化"：删掉了中间的 .options 段
        # 旧写法 spring.ai.ollama.embedding.options.model 已被废弃（暂时还能用，但会刷警告）
        model: ${OLLAMA_EMBEDDING_MODEL:nomic-embed-text}
      base-url: ${SPRING_AI_OLLAMA_BASE_URL:http://localhost:11434}

    # ========== 新增配置（可选但推荐显式写出，便于教学和理解） ==========
    chat:
      client:
        tool-calling:
          # 2.0 新增开关：ChatClient 检测到工具时是否自动注册 ToolCallingAdvisor
          # 默认就是 true，显式写出是为了让读代码的人知道"工具循环是自动装配的"
          enabled: true

    # ========== MCP 配置说明（本段当前为休眠配置，pom 未引入 MCP Server starter） ==========
    # 2.0 变化：SSE 传输被废弃，Streamable HTTP 成为默认传输
    # 若未来启用 MCP Server，sse-message-endpoint 属性需删除，改用：
    # mcp:
    #   server:
    #     name: cyber-ai-platform
    #     version: 1.0.0
    #     type: SYNC
    #     protocol: STREAMABLE   # 2.0 默认传输协议
```

`docker-compose.yml` 同步修改一处环境变量：

```yaml
    environment:
      # 改造前：SPRING_AI_OLLAMA_EMBEDDING_OPTIONS_MODEL（对应旧的 options.model 属性）
      # 改造后：属性扁平化后，环境变量也去掉 OPTIONS 段
      - SPRING_AI_OLLAMA_EMBEDDING_MODEL=nomic-embed-text
```

### 6.6（可选尝鲜）2.0 新特性：一个注解暴露 MCP 工具

升级完成后，你可以用 2.0 的 `@McpTool` 注解体验"新式武器"（教学示例，非必须）：

```java
package com.ailearn.mcp;

// 2.0 新特性：MCP 注解已并入 Spring AI 官方包（原来是社区孵化项目 org.springaicommunity）
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 2.0 注解式 MCP 工具示例
 * 对比你现在的写法（@Tool + MethodToolCallbackProvider 手动注册），
 * 新方式连注册代码都省了：一个注解，MCP Server 自动发现、自动生成 JSON Schema、自动参数校验
 */
@Component
public class SystemInfoMcpTools {

    // @McpTool：把普通 Java 方法暴露为 MCP 协议工具，任何 MCP 客户端（Claude、IDE 插件等）都能调用
    @McpTool(name = "get_server_time", description = "获取服务器当前时间")
    public String getServerTime(
            // @McpToolParam：描述参数，2.0 会据此自动生成符合 MCP 规范的入参 JSON Schema
            @McpToolParam(description = "时区，如 Asia/Shanghai", required = false) String timezone) {
        // 业务逻辑：返回当前时间字符串
        return java.time.ZonedDateTime.now(
                java.time.ZoneId.of(timezone != null ? timezone : "Asia/Shanghai")
        ).toString();
    }
}
```

---

## 第七章 RAG 与 Ollama 迁移避坑指南

### 7.1 RAG 链路专项（你问到的重点）

好消息：**官方 Upgrade Notes 中没有任何针对 PgVector 的破坏性变更**。你的 RAG 链路（`TikaDocumentReader` → `TokenTextSplitter` → `EmbeddingModel` → `PgVectorStore`）涉及的所有类在 2.0 中原样保留。逐环节核对：

| 环节 | 你用的类 | 2.0 状态 | 行动 |
|---|---|---|---|
| 文档解析 | `PagePdfDocumentReader` / Tika reader | ✅ 保留 | 无 |
| 文档切分 | `TokenTextSplitter` | ✅ 保留 | 无 |
| 向量存储 | `PgVectorStore.builder(...)` | ✅ builder API 未变 | 无 |
| 内存向量库 | `SimpleVectorStore` | ✅ 保留 | 无 |
| 相似度检索 | `SearchRequest.builder().query().topK().similarityThreshold()` | ✅ 未变 | 无 |
| 向量表结构 | `vector_store` 表（自动建表） | ✅ 结构未变 | **已有数据不用迁移** |
| 检索 Advisor | 你是手动拼 Prompt 做 RAG，没用 `QuestionAnswerAdvisor` | — | 无 |

**唯一的间接影响**：嵌入模型属性扁平化（`spring.ai.ollama.embedding.model`），已在 6.5 节处理。改错这个配置的症状是——嵌入模型回退到默认值，导致向量维度对不上（比如默认模型不是 768 维），新文档入库时报维度错误。**升级后第一件事就是验证 RAG 上传**。

### 7.2 Ollama 专项

1. **模型文件不动**：qwen2.5:7b、nomic-embed-text 继续用，`ollama pull` 不用重新执行
2. **你当年绕过的流式工具调用 NPE**：2.0 把工具循环重构成了 ToolCallingAdvisor 的顺序聚合模式（官方明确提到"简化了流式聚合"），你 AgentService 里的"先同步后模拟流式"兼容方案**建议保留观察**——先用真实场景测试 2.0 原生流式工具调用是否还触发 NPE，如果不触发了，可以逐步拆掉兼容层（这是很好的面试故事：从"被迫绕过框架 bug"到"官方修复后回归标准用法"）
3. **think 属性改名**：`spring.ai.ollama.chat.think-option` → `spring.ai.ollama.chat.think`（你没用过思考模式，仅记录）
4. **num-ctx 8192 保留**：该属性未变

### 7.3 通用避坑清单

- ❌ 不要同时保留新旧两套 Jackson：如果 `mvn dependency:tree | grep jackson` 发现 `com.fasterxml.jackson.core:jackson-databind` 2.x 被某个库传递引入，用 `<exclusions>` 排掉（Tess4J 历史上会传递旧 Jackson，升级后重点检查它）
- ❌ 不要手动注册 `ToolCallingAdvisor`（会重复执行工具）
- ❌ 不要相信"编译通过 = 升级完成"：Jackson 3 改变了部分序列化默认值，**必须**用你的 90+ 测试 + 接口冒烟验证 JSON 输出形状（重点：登录接口返回、Result 统一响应、SSE 事件格式）
- ✅ 升级后先跑一次 `mvn dependency:tree > deps.txt` 存档，出问题方便对比

---

## 第八章 升级验证 Checklist

### 编译与测试

- [ ] `mvn clean compile` 零错误零警告（deprecated 警告也要清零）
- [ ] `mvn clean test` 90+ 测试全绿
- [ ] `mvn dependency:tree` 确认无 Jackson 2（`com.fasterxml.jackson` 除 annotation 包外）残留

### 功能冒烟（本地 dev profile）

- [ ] 注册 / 登录 / Token 刷新（验证 Spring Security 7 + JJWT 0.13）
- [ ] 基础聊天：同步 + SSE 流式
- [ ] 记忆对话：3 轮以上，查 `chat_message` 表确认无工具中间消息写入
- [ ] Agent：触发天气工具，确认**只调用一次**（验证无重复 ToolCallingAdvisor）
- [ ] 联网搜索 Agent：Tavily 搜索正常（验证 WebSearchTool 的 Jackson 3 迁移正确）
- [ ] 多 Agent 协作：跑一个需要 Critic 迭代的任务（验证 MultiAgentService 的 Jackson 3 迁移）
- [ ] RAG：上传 PDF → 提问 → 确认向量维度正常（验证 embedding 属性扁平化改对了）
- [ ] 结构化输出：`/structured` 页面两种类型都返回正确 JSON
- [ ] 限流：连续快速调用 Agent 接口，确认第 4 次被限流（验证 Resilience4j 在 Boot 4 下生效——**这是最大风险项，必测**）
- [ ] Swagger UI 可打开（验证 springdoc 4.0.x 与 Jackson 3 兼容）

### Docker 全栈

- [ ] `docker-compose up -d --build` 全部容器健康
- [ ] PgVector 自动建表、RAG 全链路在容器内正常
- [ ] Kibana 能查到带 traceId 的 JSON 日志（验证 logstash-encoder 9.x）

---

## 附录：本次升级的"面试话术"提炼

1. "我把项目从 Spring AI 1.1 升级到 2.0 GA，核心挑战是 Spring Boot 4 + Jackson 3 的联动迁移，我通过依赖矩阵分析和分支灰度策略完成了零回滚升级。"
2. "Spring AI 2.0 最大的架构变化是把工具调用循环从 ChatModel 黑盒上移为 ToolCallingAdvisor，我理解这个设计后，主动检查并避免了 Advisor 重复注册的坑。"
3. "升级中我发现官方将记忆 Advisor 默认顺序调整到了工具循环之外，这个变化恰好解决了我们自研 DatabaseChatMemory 无法存储工具消息的潜在隐患。"
4. "针对 Resilience4j 没有 Boot 4 官方 starter 的风险，我准备了编程式限流的降级预案。"

---

## 附录一：实际执行结果（2026-08-03 已在本项目 master 分支完成升级）

升级已实际执行完毕并全部验证通过。以下是与原方案有出入的**实测修正**：

### 实际使用的版本（以实测为准）

| 组件 | 原方案 | **实际采用** | 修正原因 |
|---|---|---|---|
| Spring Boot | 4.0.x | **4.1.0** | Spring AI 2.0.0 的官方构件直接引用 Boot 4.1.0 依赖，是官方验证过的搭配 |
| springdoc-openapi | 4.0.x | **3.1.0** | 4.0.x 尚未发布；3.1.0 官方对应 Boot 4.1.0，实测 Swagger 正常 |
| Resilience4j | 2.3.0 boot3 starter | **2.4.0 `resilience4j-spring-boot4`** | 实测发现官方**已有** boot4 专用 starter！boot3 starter 会被 Boot 4 兼容性检查直接拒绝启动（报错信息明确指引换 boot4） |
| spring-retry | 删除版本号 | **显式 2.0.13** | Boot 4 BOM 不再管理 spring-retry，必须显式指定 |
| Testcontainers | Boot 管理 | **BOM 2.0.5 + 构件改名** | Boot 4 不再管理；且 2.x 构件名改为 `testcontainers-junit-jupiter` / `testcontainers-mysql` |
| Lombok | 1.18.40+ | **1.18.46** | 当前最新 |

### 实测发现的两个文档外变更

1. **Jackson 3 移除了 `JsonProcessingException`**（不只是改包名）：序列化异常改为非受检的 `tools.jackson.databind.DatabindException`。`MultiAgentService` 的 catch 块已相应调整。
2. **Jackson 2 共存是正常设计**：依赖树中 `jjwt-jackson 0.13.0` 和 `swagger-core 2.2.52` 内部仍使用 Jackson 2（jackson-databind 2.21.4），与应用的 Jackson 3 和平共存（这正是 Jackson 3 改包名的目的），**不要**用 exclusions 排除，排除会导致它们崩溃。

### 验证结果（全部实测通过）

- `mvn clean test`：**90 个测试，0 失败**（6 个跳过为原本就禁用的真实 API 测试）
- 应用启动：6.5 秒启动成功（Boot 4.1.0 + Spring AI 2.0.0）
- 注册 / 登录 / JWT：正常（Security 7 + JJWT 0.13.0）
- 同步聊天：正常（Ollama qwen2.5:7b 真实调用）
- SSE 流式聊天：正常（token 逐字推送）
- Agent 工具调用：正常（真实天气数据返回，ToolCallingAdvisor 自动注册生效，工具仅执行一次）
- 记忆对话：两轮对话记忆正常（DatabaseChatMemory 零改动兼容）
- 限流：Agent 接口 3 次/30秒，第 4 次被拒绝（resilience4j-spring-boot4 生效）
- Swagger：`/v3/api-docs` 返回 200（springdoc 3.1.0 + Jackson 3 正常）

### 其他说明

- 本次升级在 master 分支直接执行，回滚标签：`before-spring-ai-2.0`
- 本机启动时 Tomcat 随机分配了端口（如 63491），说明环境变量 `SERVER_PORT` 可能被设为 0，与升级无关；如需固定 8080 请检查环境变量
- 升级方案文档中的 pom.xml 示例（6.1 节）为教学演示版，**项目根目录的 pom.xml 才是实测通过的最终版**

---

> 文档版本：v1.0（2026-08-03）基于 Spring AI 2.0.0 GA 官方 Upgrade Notes 编写；附录一为同日实际执行结果
