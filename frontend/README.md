# Cyber AI Platform - 前端

赛博AI平台的Vue 3前端应用，基于Element Plus提供登录认证、SSE流式聊天、多Agent执行过程、RAG知识库和工具演示界面。

---

## 技术栈

- **Vue 3** - Composition API + `<script setup>`
- **Vite 8** - 基于Rolldown的构建工具
- **Element Plus** - 企业级UI组件库
- **Pinia 3** - 状态管理
- **Vue Router 4** - 前端路由
- **Axios 1** - HTTP客户端与Token拦截器
- **Fetch Streams** - 携带JWT的SSE流式响应
- **marked + highlight.js + DOMPurify** - Markdown渲染、代码高亮和XSS防护

---

## 快速开始

### 安装依赖

```bash
npm install
```

### 开发模式

```bash
npm run dev
```

启动后访问 http://localhost:5173，开发服务器已配置代理，所有 `/api` 请求自动转发到 `http://localhost:8080`。

### 生产构建

```bash
npm run build
```

构建产物输出到 `dist/` 目录，Maven构建时会自动复制到后端 `static/` 目录。

### 预览构建产物

```bash
npm run preview
```

---

## 目录结构

```
frontend/
├── public/               # 静态资源
│   ├── favicon.svg
│   └── icons.svg
├── src/
│   ├── App.vue           # 根组件
│   ├── main.js           # 应用入口
│   ├── assets/           # 资源文件与全局样式
│   ├── components/       # 公共组件
│   │   ├── CyberLayout.vue      # Element Plus全局布局（导航栏/侧边栏/登出）
│   │   ├── ChatMessage.vue      # 聊天消息（Markdown+代码高亮+复制）
│   │   ├── ChatInput.vue        # 消息输入框（自适应高度/Enter发送）
│   │   └── LoginModal.vue       # 登录/注册弹窗
│   ├── views/            # 页面视图
│   │   ├── Home.vue              # 首页
│   │   ├── ChatView.vue          # 基础聊天
│   │   ├── MemoryView.vue        # 记忆对话
│   │   ├── AgentView.vue         # Agent智能体
│   │   ├── SearchAgentView.vue   # 联网搜索Agent
│   │   ├── MultiAgentView.vue    # 多Agent协作（实时事件流展示）
│   │   ├── RagView.vue           # RAG知识库（文档上传/管理/问答）
│   │   ├── McpView.vue           # MCP工具
│   │   ├── StructuredView.vue    # 结构化输出
│   │   └── ToolsView.vue         # 工具调用演示
│   ├── router/
│   │   └── index.js      # 路由配置
│   ├── stores/
│   │   ├── chat.js       # 聊天状态（会话/消息/SSE连接）
│   │   └── user.js       # 用户状态（登录/Token/用户信息）
│   └── utils/
│       └── request.js    # Axios封装 + SSE连接工具
├── index.html
├── vite.config.js        # Vite配置（含API代理）
└── package.json
```

---

## 核心功能

### SSE流式响应

通过 `createSSEConnection` 工具函数处理Server-Sent Events：

```javascript
import { createSSEConnection } from '@/utils/request'

const connection = createSSEConnection('/api/chat/stream?message=你好', {
  onMessage: (token) => {
    // 处理每个token（追加到消息）
  },
  onError: (error) => {
    // 处理错误
  },
  onClose: () => {
    // 连接关闭
  }
})

// 关闭连接
connection.close()
```

流式消息必须更新响应式数组中的消息对象，例如：

```javascript
messages.value[messages.value.length - 1].content += token
```

不要持续修改插入数组前保存的普通对象引用，否则Vue不会触发视图更新，页面会出现空白助手气泡。

### 认证机制

- JWT Token存储在localStorage
- Axios拦截器自动添加Authorization头
- 401响应自动尝试Refresh Token
- 刷新失败则跳转登录
- 全局布局只在应用初始化时获取一次用户信息，避免页面切换重复请求

### Markdown渲染

ChatMessage组件使用marked + highlight.js：

- 支持GFM（GitHub Flavored Markdown）
- 代码块语法高亮
- 代码块一键复制按钮
- 赛博朋克风格代码块样式

---

## UI与消息渲染

- 页面布局、表单、菜单、卡片和反馈提示统一使用Element Plus
- `ChatMessage`负责Markdown渲染、代码高亮、复制和内容净化
- Agent、联网搜索、多Agent、记忆对话、RAG及结构化输出统一将异步结果写入响应式消息数组
- 登录、注册和Token刷新使用JSON内容协商，SSE聊天使用`text/event-stream`

---

## Vite代理配置

开发环境API代理配置（vite.config.js）：

```javascript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
    configure: (proxy) => {
      proxy.on('proxyReq', (proxyReq, req) => {
        if (req.headers.accept?.includes('text/event-stream')) {
          proxyReq.setHeader('Cache-Control', 'no-cache')
        }
      })
    }
  }
}
```

所有以 `/api` 开头的请求自动代理到后端8080端口，无需配置CORS。代理不得把所有请求的`Accept`强制改为`text/event-stream`，否则登录、注册等JSON接口会因内容协商失败返回406。
