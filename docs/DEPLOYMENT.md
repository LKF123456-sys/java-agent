# 部署指南 & 常见问题

## 快速启动（开发环境）

### 1. 环境准备

| 软件 | 最低版本 | 推荐版本 | 下载地址 |
|------|---------|---------|---------|
| JDK | 21 | 21+ | https://adoptium.net/ |
| Maven | 3.8 | 3.9+ | https://maven.apache.org/ |
| Node.js | 18 | 20+ LTS | https://nodejs.org/ |
| MySQL | 8.0 | 8.0+ | https://dev.mysql.com/ |
| Ollama | - | 最新版 | https://ollama.com/ |

### 2. 启动步骤（按顺序）

```bash
# ① 启动MySQL并创建数据库
mysql -u root -p
CREATE DATABASE IF NOT EXISTS ai_learn DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# ② 启动Ollama并拉取模型
ollama serve
# 另一个终端
ollama pull qwen2.5:7b
ollama pull nomic-embed-text

# ③ 启动后端（项目根目录）
mvn spring-boot:run

# ④ 启动前端（frontend目录）
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173 即可使用。

### 3. 默认端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 后端API | 8080 | http://localhost:8080 |
| 前端开发 | 5173 | http://localhost:5173 |
| MySQL | 3306 | 数据库 |
| Ollama | 11434 | 本地模型服务 |
| Swagger | 8080 | http://localhost:8080/swagger-ui.html |

---

## Docker部署（生产/测试）

### 最简启动（PostgreSQL + App）

```bash
# 1. 克隆项目
git clone <repo-url>
cd javaAILearn

# 2. 构建并启动
docker-compose up -d --build

# 3. 查看日志等待启动完成
docker-compose logs -f app

# 4. 访问 http://localhost:8080
```

> 注意：Docker部署默认使用PostgreSQL + PgVector，不包含Ollama。需要本地模型请使用`--profile ollama`。

### 包含Ollama（需要NVIDIA GPU）

```bash
# 需要先安装NVIDIA Container Toolkit
docker-compose --profile ollama up -d --build

# 等待Ollama就绪后，拉取模型（首次需要较长时间）
docker exec -it cyber-ai-ollama ollama pull qwen2.5:7b
docker exec -it cyber-ai-ollama ollama pull nomic-embed-text
```

### 常用Docker命令

```bash
# 查看服务状态
docker-compose ps

# 查看应用日志（最近100行）
docker-compose logs -f --tail=100 app

# 重启应用
docker-compose restart app

# 停止所有服务
docker-compose down

# 停止并删除数据卷（⚠️会删除所有数据！）
docker-compose down -v

# 重新构建应用（代码更新后）
docker-compose build --no-cache app
docker-compose up -d app
```

---

## 配置说明

### 主要配置项（application.yml）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 8080 | 服务端口 |
| `spring.datasource.url` | jdbc:mysql://localhost:3306/ai_learn | 数据库URL |
| `spring.ai.ollama.chat.options.model` | qwen2.5:7b | 对话模型 |
| `spring.ai.ollama.embedding.options.model` | nomic-embed-text | 嵌入模型 |
| `spring.ai.ollama.base-url` | http://localhost:11434 | Ollama地址 |
| `jwt.secret` | (内置) | JWT签名密钥 **生产必须修改** |
| `jwt.access-token-expiration` | 7200000 | Access Token过期（2小时，毫秒） |
| `jwt.refresh-token-expiration` | 604800000 | Refresh Token过期（7天，毫秒） |
| `tavily.api-key` | (内置开发Key) | 联网搜索API Key |
| `logging.level.com.ailearn` | DEBUG | 应用日志级别 |

### 使用DeepSeek/OpenAI云端模型

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  ai:
    # 注释掉ollama配置
    # ollama:
    #   ...
    openai:
      api-key: ${OPENAI_API_KEY:sk-your-key-here}
      base-url: ${OPENAI_BASE_URL:https://api.deepseek.com}
      chat:
        options:
          model: ${OPENAI_MODEL:deepseek-chat}
          temperature: 0.7
```

> 提示：通过环境变量设置API Key更安全：`set OPENAI_API_KEY=sk-xxx`（Windows）或 `export OPENAI_API_KEY=sk-xxx`（Linux/Mac）

### 使用其他Ollama模型

```bash
# 拉取其他中文模型
ollama pull qwen2.5:14b      # 更大更聪明，但需要更多显存
ollama pull llama3.1:8b     # Meta官方模型（中文能力较弱）
ollama pull glm4:9b         # 智谱GLM4
```

然后修改application.yml中的`spring.ai.ollama.chat.options.model`。

---

## 常见问题（FAQ）

### Q1: 启动后访问前端页面显示"连接失败"

**原因：** 后端未启动或端口配置错误。

**解决方案：**
1. 确认后端在8080端口正常运行（访问 http://localhost:8080/actuator/health 应返回`{"status":"UP"}`）
2. 检查前端`vite.config.js`中的代理配置是否正确
3. 检查防火墙设置

### Q2: Agent调用工具时无响应或报错

**原因：** Spring AI + Ollama qwen模型在流式工具调用时存在已知bug（evalDuration为null导致NPE）。

**解决方案：** 本项目已在代码中做了兼容处理（"先同步后流式"方案），确保使用的是最新代码。如果仍有问题：
1. 检查Ollama模型是否正确加载：`ollama list`
2. 查看后端日志是否有异常堆栈
3. 尝试切换到DeepSeek云端模型

### Q3: RAG问答时说"知识库中没有相关信息"

**原因：** 知识库为空，还没有上传文档。

**解决方案：**
1. 访问RAG页面（/rag）
2. 在"文档管理"区域上传PDF/Word/TXT/MD文件
3. 等待文档向量化完成（上传成功后文档列表会显示）
4. 再进行问答

### Q4: 登录后刷新页面/切换页面需要重新登录

**原因：** 旧版本的布局问题已修复。如果仍出现：
1. 清除浏览器localStorage
2. 确认使用的是最新前端代码
3. 检查JWT Token是否正确存储和发送

### Q5: 后端登录正常，但前端登录返回406

**原因：** Vite开发代理错误地把全部`/api`请求的`Accept`头改成`text/event-stream`，导致登录接口无法返回JSON。

**解决方案：**
1. 确认使用最新的`frontend/vite.config.js`
2. 仅对请求本身声明`Accept: text/event-stream`的SSE请求设置`Cache-Control: no-cache`
3. 修改配置后重启Vite开发服务器，并使用`Ctrl + F5`强制刷新页面
4. 分别验证直连后端和前端代理：两者调用`POST /api/auth/login`都应返回200和`application/json`

### Q6: 聊天请求完成，但助手气泡为空

**原因：** Vue页面持续修改插入响应式数组前保存的普通消息对象，SSE分片到达后没有触发视图更新。

**解决方案：**
1. 将流式内容写入响应式数组中的消息对象：`messages.value[messages.value.length - 1].content += token`
2. 正常回复和错误提示必须使用同一种响应式写入方式
3. 确认Ollama可访问，并检查SSE响应正文长度是否大于0
4. 修复后执行`npm run build`并强制刷新浏览器

### Q7: 工具演示返回系统内部错误

**原因：** 前端调用了不存在的通用`POST /api/tools/call`路由。

**解决方案：**
- 天气查询调用`GET /api/tools/weather?city=北京`
- 计算器调用`GET /api/tools/calculator?expression=2%2B3`
- 日期时间由前端本地生成

### Q8: Docker启动后App无法连接数据库

**原因：** 数据库未就绪就启动了应用，或健康检查失败。

**解决方案：**
1. 等待数据库完全启动（PostgreSQL首次启动需要初始化）
2. 手动重启app服务：`docker-compose restart app`
3. 检查数据库日志：`docker-compose logs postgres`

### Q6: Ollama模型下载速度慢

**解决方案：**
- 使用国内镜像源：设置`OLLAMA_HOST=https://ollama.ai-mirror.com`或其他镜像
- 手动下载模型文件后导入
- 使用云端API（DeepSeek等）替代本地模型

### Q7: Maven依赖下载慢

**解决方案：** 配置Maven国内镜像（settings.xml）：

```xml
<mirror>
  <id>aliyun</id>
  <mirrorOf>central</mirrorOf>
  <name>Aliyun Maven Mirror</name>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```

### Q8: npm install速度慢

**解决方案：**
```bash
# 使用淘宝镜像
npm config set registry https://registry.npmmirror.com
npm install
```

---

## API快速测试

### 无需认证的接口测试

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 注册用户
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123","nickname":"测试用户"}'
```

### 需要认证的接口测试

```bash
# 1. 登录获取Token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# 2. 使用Token调用基础聊天（流式）
curl -N "http://localhost:8080/api/chat/stream?message=你好" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 技术支持

- 查看后端日志：`logs/`目录或控制台输出
- Swagger API文档：http://localhost:8080/swagger-ui.html
- Actuator监控：http://localhost:8080/actuator
