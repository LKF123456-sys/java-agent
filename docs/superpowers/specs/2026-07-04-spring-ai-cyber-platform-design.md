# Spring AI 赛博朋克学习平台 - 设计文档

## 1. 项目概述

将现有的 Spring AI 学习项目改造为前后端分离架构，采用赛博朋克科技感UI设计，使用 Ollama 本地模型，集成 MySQL 数据持久化，保留并增强所有 AI 能力（聊天、记忆、RAG、Agent、结构化输出、工具调用）。

### 1.1 技术栈

**后端：**
- Spring Boot 3.4.3（最新稳定版）
- Spring AI 1.0.0-M6
- MySQL 8.0+
- MyBatis-Plus 3.5.7（ORM框架）
- Spring Validation（参数校验）
- Lombok

**前端：**
- Vue 3.5+（Composition API）
- Vite 6.x（构建工具）
- Vue Router 4（路由）
- Pinia（状态管理）
- Element Plus（UI组件库，自定义赛博朋克主题）
- Axios（HTTP客户端）
- Marked + Highlight.js（Markdown渲染和代码高亮）

**AI模型：**
- Ollama（本地运行）
- 模型：qwen2.5:7b（中文对话）
- Embedding：nomic-embed-text（向量嵌入）

### 1.2 项目结构

```
javaAILearn/
├── src/main/java/com/ailearn/          # Spring Boot 后端
│   ├── common/                         # 通用组件
│   │   ├── Result.java                 # 统一响应格式
│   │   └── GlobalExceptionHandler.java # 全局异常处理
│   ├── config/                         # 配置类
│   │   ├── AiConfig.java
│   │   ├── WebConfig.java              # CORS配置
│   │   └── MyBatisPlusConfig.java
│   ├── controller/                     # 控制器层（移到controller包）
│   │   ├── ChatController.java
│   │   ├── MemoryChatController.java
│   │   ├── RagController.java
│   │   ├── AgentController.java
│   │   ├── StructuredOutputController.java
│   │   ├── ToolsController.java
│   │   └── ConversationController.java # 会话管理
│   ├── entity/                         # 数据库实体
│   │   ├── Conversation.java
│   │   └── ChatMessage.java
│   ├── mapper/                         # MyBatis Mapper
│   ├── service/                        # 服务层
│   │   ├── ChatService.java
│   │   ├── MemoryChatService.java
│   │   ├── RagService.java
│   │   ├── AgentService.java
│   │   ├── StructuredOutputService.java
│   │   └── ConversationService.java
│   ├── tools/                          # AI工具
│   │   ├── WeatherTool.java
│   │   └── CalculatorTool.java
│   └── AiLearnApplication.java
├── src/main/resources/
│   ├── mapper/                         # MyBatis XML映射
│   ├── db/                             # 数据库脚本
│   │   └── schema.sql
│   └── application.yml
├── frontend/                           # Vue 3 前端项目
│   ├── src/
│   │   ├── api/                        # API接口
│   │   ├── assets/                     # 静态资源
│   │   │   └── styles/                 # 全局样式
│   │   ├── components/                 # 公共组件
│   │   ├── router/                     # 路由配置
│   │   ├── stores/                     # Pinia状态
│   │   ├── views/                      # 页面组件
│   │   ├── App.vue
│   │   └── main.js
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
└── pom.xml
```

## 2. 数据库设计

### 2.1 会话表 (conversation)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| title | VARCHAR(200) | 会话标题 |
| type | VARCHAR(20) | 会话类型：chat/memory/rag/agent |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 2.2 聊天消息表 (chat_message)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| conversation_id | BIGINT | 会话ID |
| role | VARCHAR(20) | 角色：user/assistant/system |
| content | TEXT | 消息内容 |
| created_at | DATETIME | 创建时间 |

## 3. API设计

### 3.1 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 3.2 核心API接口

**聊天模块：**
- POST `/api/chat/send` - 发送消息（同步）
- GET `/api/chat/stream` - 流式聊天（SSE）

**记忆对话模块：**
- POST `/api/memory/chat/{conversationId}` - 记忆对话
- GET `/api/memory/stream/{conversationId}` - 流式记忆对话

**会话管理：**
- GET `/api/conversations` - 获取会话列表
- POST `/api/conversations` - 创建会话
- GET `/api/conversations/{id}/messages` - 获取会话消息
- DELETE `/api/conversations/{id}` - 删除会话

**RAG知识库：**
- POST `/api/rag/upload` - 上传文档
- POST `/api/rag/chat` - 知识库问答

**Agent模块：**
- POST `/api/agent/travel-plan` - 旅游规划
- POST `/api/agent/task` - 执行任务
- GET `/api/agent/stream/travel-plan` - 流式旅游规划

**结构化输出：**
- POST `/api/structured/book` - 图书信息提取
- POST `/api/structured/movie` - 电影信息提取

**工具模块：**
- GET `/api/tools/weather` - 天气查询
- POST `/api/tools/calculate` - 计算器

## 4. 前端设计

### 4.1 赛博朋克主题规范

**配色方案：**
- 背景色：#0a0a1f（深空黑蓝）、#0d0d2b（深蓝紫）、#0d0d25（暗蓝）
- 主色调：#00ffff（霓虹青）、#ff00ff（霓虹品红）、#6b00ff（霓虹紫）
- 文字色：#ffffff（纯白）、#cccccc（浅灰）、#888888（中灰）
- 成功色：#00ff88（霓虹绿）
- 警告色：#ffaa00（霓虹橙）

**视觉特效：**
- 网格背景（30px间距，0.03透明度）
- 扫描线动画（从上到下，3秒循环）
- 霓虹发光效果（text-shadow、box-shadow）
- 斜切按钮（clip-path实现）
- 边框渐变发光
- 消息气泡渐变背景

**页面路由：**
- `/` - 首页/聊天页
- `/memory` - 记忆对话
- `/rag` - RAG知识库
- `/agent` - Agent智能体
- `/structured` - 结构化输出
- `/tools` - 工具演示

### 4.2 组件结构

```
App.vue
├── CyberLayout.vue (布局容器)
│   ├── CyberHeader.vue (顶部导航)
│   ├── CyberSidebar.vue (左侧会话列表)
│   └── <router-view />
│       ├── ChatView.vue (聊天页面)
│       │   ├── ChatMessage.vue (消息气泡)
│       │   ├── ChatInput.vue (输入框)
│       │   └── TypingIndicator.vue (输入中动画)
│       ├── MemoryView.vue (记忆对话)
│       ├── RagView.vue (知识库)
│       │   ├── DocumentUpload.vue (文档上传)
│       │   └── KnowledgeList.vue (文档列表)
│       ├── AgentView.vue (Agent)
│       │   ├── TravelPlanForm.vue (旅游规划表单)
│       │   └── TaskForm.vue (任务表单)
│       ├── StructuredView.vue (结构化输出)
│       └── ToolsView.vue (工具演示)
```

### 4.3 状态管理（Pinia）

- `useChatStore` - 聊天状态（当前会话、消息列表、流式状态）
- `useConversationStore` - 会话列表管理
- `useAppStore` - 全局状态（侧边栏展开/收起、主题）

## 5. 后端架构设计

### 5.1 分层架构

- **Controller层**：接收请求，参数校验，返回统一响应
- **Service层**：业务逻辑，调用Spring AI
- **Mapper层**：数据库操作（MyBatis-Plus）
- **Entity层**：数据库实体
- **Common层**：通用工具、异常处理、响应封装

### 5.2 关键实现点

1. **SSE流式响应**：使用Spring的SseEmitter或Flux实现流式输出
2. **聊天记忆持久化**：将MessageChatMemory的内存实现替换为MySQL持久化
3. **跨域配置**：配置CORS允许前端dev server访问
4. **全局异常处理**：统一捕获异常，返回标准错误格式
5. **RAG文档存储**：上传文档后解析并生成向量存储

## 6. 开发环境配置

### 6.1 后端配置（application.yml）

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_learn?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen2.5:7b
          temperature: 0.7
      embedding:
        options:
          model: nomic-embed-text
```

### 6.2 前端配置（vite.config.js）

```javascript
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

## 7. 实施步骤

1. **后端基础改造**
   - 添加MySQL/MyBatis-Plus依赖
   - 创建数据库表和实体类
   - 实现统一响应格式和全局异常处理
   - 改造Controller返回Result对象

2. **会话持久化**
   - 实现Conversation和ChatMessage的CRUD
   - 改造MemoryChatService使用MySQL存储
   - 实现会话管理API

3. **前端项目初始化**
   - 创建Vue 3 + Vite项目
   - 配置Element Plus赛博朋克主题
   - 配置路由、Pinia、Axios
   - 实现布局组件（Header、Sidebar）

4. **聊天页面开发**
   - 实现基础聊天界面（赛博朋克风格）
   - 接入流式SSE接口
   - 实现Markdown渲染
   - 实现会话列表管理

5. **各功能页面开发**
   - 记忆对话页面
   - RAG知识库页面（含文档上传）
   - Agent智能体页面
   - 结构化输出页面
   - 工具演示页面

6. **联调测试**
   - 前后端联调
   - 所有AI功能测试
   - UI效果调整
