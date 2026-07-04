# Spring AI 赛博朋克学习平台 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有Spring AI学习项目改造为前后端分离架构，赛博朋克UI风格，使用Ollama+MySQL+Vue3技术栈

**Architecture:** 后端Spring Boot 3.4提供REST API和SSE流式接口，MyBatis-Plus操作MySQL存储会话历史；前端Vue3+Vite+Element Plus构建赛博朋克风格界面，通过Axios调用后端API，SSE接收流式响应。

**Tech Stack:** Spring Boot 3.4.3, Spring AI 1.0.0-M6, MyBatis-Plus 3.5.7, MySQL 8.0, Vue 3.5, Vite 6, Element Plus, Pinia, Vue Router, Ollama

---

## 第一阶段：后端基础改造

### Task 1: 更新pom.xml添加MySQL和MyBatis-Plus依赖

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 在pom.xml中添加MySQL和MyBatis-Plus依赖**

在`<dependencies>`中添加以下依赖（在Lombok之后）：

```xml
        <!-- MySQL Driver -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.7</version>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
```

- [ ] **Step 2: 验证依赖下载**

Run: `mvn dependency:resolve`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "feat: 添加MySQL、MyBatis-Plus和Validation依赖"
```

---

### Task 2: 创建统一响应Result类

**Files:**
- Create: `src/main/java/com/ailearn/common/Result.java`

- [ ] **Step 1: 创建Result通用响应类**

```java
package com.ailearn.common;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/ailearn/common/Result.java
git commit -m "feat: 添加统一响应Result类"
```

---

### Task 3: 重构包结构 - 移动Controller到controller包

**Files:**
- Move: `src/main/java/com/ailearn/chat/ChatController.java` → `src/main/java/com/ailearn/controller/ChatController.java`
- Move: `src/main/java/com/ailearn/memory/MemoryChatController.java` → `src/main/java/com/ailearn/controller/MemoryChatController.java`
- Move: `src/main/java/com/ailearn/rag/RagController.java` → `src/main/java/com/ailearn/controller/RagController.java`
- Move: `src/main/java/com/ailearn/agent/AgentController.java` → `src/main/java/com/ailearn/controller/AgentController.java`
- Move: `src/main/java/com/ailearn/structured/StructuredOutputController.java` → `src/main/java/com/ailearn/controller/StructuredOutputController.java`
- Move: `src/main/java/com/ailearn/tools/ToolsController.java` → `src/main/java/com/ailearn/controller/ToolsController.java`

- [ ] **Step 1: 创建controller目录并移动所有Controller文件**

创建目录 `src/main/java/com/ailearn/controller/`，将所有Controller移动过去，并更新package声明为 `package com.ailearn.controller;`，更新import路径。

- [ ] **Step 2: 更新所有移动后Controller的package和import**

每个文件第一行改为：
```java
package com.ailearn.controller;
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ailearn/controller/ src/main/java/com/ailearn/chat/ src/main/java/com/ailearn/memory/ src/main/java/com/ailearn/rag/ src/main/java/com/ailearn/agent/ src/main/java/com/ailearn/structured/ src/main/java/com/ailearn/tools/
git commit -m "refactor: 移动Controller到统一controller包"
```

---

### Task 4: 更新application.yml配置MySQL和MyBatis-Plus

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 更新application.yml完整配置**

```yaml
server:
  port: 8080

spring:
  application:
    name: java-ai-learn
  autoconfigure:
    exclude:
      - org.springframework.ai.autoconfigure.transformers.TransformersEmbeddingModelAutoConfiguration
      - org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration

  datasource:
    url: jdbc:mysql://localhost:3306/ai_learn?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

  ai:
    ollama:
      chat:
        enabled: true
        options:
          model: qwen2.5:7b
          temperature: 0.7
      embedding:
        enabled: true
        options:
          model: nomic-embed-text
      base-url: http://localhost:11434

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

management:
  endpoints:
    web:
      exposure:
        include: health,info

logging:
  level:
    org.springframework.ai: DEBUG
    com.ailearn: DEBUG
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "feat: 配置MySQL数据源和MyBatis-Plus"
```

---

### Task 5: 创建MyBatis-Plus配置类

**Files:**
- Create: `src/main/java/com/ailearn/config/MyBatisPlusConfig.java`

- [ ] **Step 1: 创建MyBatisPlusConfig配置类**

```java
package com.ailearn.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.ailearn.mapper")
public class MyBatisPlusConfig {
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/ailearn/config/MyBatisPlusConfig.java
git commit -m "feat: 添加MyBatis-Plus配置类"
```

---

## 第二阶段：数据库层

### Task 6: 创建数据库实体类

**Files:**
- Create: `src/main/java/com/ailearn/entity/Conversation.java`
- Create: `src/main/java/com/ailearn/entity/ChatMessage.java`

- [ ] **Step 1: 创建Conversation实体类**

```java
package com.ailearn.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String type;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建ChatMessage实体类**

```java
package com.ailearn.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 创建自动填充处理器**

Create: `src/main/java/com/ailearn/config/MyMetaObjectHandler.java`

```java
package com.ailearn.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

- [ ] **Step 4: 创建数据库初始化SQL脚本**

Create: `src/main/resources/db/schema.sql`

```sql
CREATE DATABASE IF NOT EXISTS ai_learn DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_learn;

CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '会话标题',
    type VARCHAR(20) NOT NULL DEFAULT 'chat' COMMENT '会话类型: chat/memory/rag/agent',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type(type),
    INDEX idx_created_at(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(20) NOT NULL COMMENT '角色: user/assistant/system',
    content TEXT NOT NULL COMMENT '消息内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_id(conversation_id),
    INDEX idx_created_at(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';
```

- [ ] **Step 5: 创建mapper目录**

Create: `src/main/resources/mapper/` (empty directory, can put a .gitkeep file)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ailearn/entity/ src/main/java/com/ailearn/config/MyMetaObjectHandler.java src/main/resources/db/ src/main/resources/mapper/
git commit -m "feat: 添加数据库实体类、自动填充处理器和SQL脚本"
```

---

### Task 7: 创建Mapper接口

**Files:**
- Create: `src/main/java/com/ailearn/mapper/ConversationMapper.java`
- Create: `src/main/java/com/ailearn/mapper/ChatMessageMapper.java`

- [ ] **Step 1: 创建ConversationMapper**

```java
package com.ailearn.mapper;

import com.ailearn.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
```

- [ ] **Step 2: 创建ChatMessageMapper**

```java
package com.ailearn.mapper;

import com.ailearn.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ailearn/mapper/
git commit -m "feat: 添加MyBatis-Plus Mapper接口"
```

---

## 第三阶段：后端服务层与Controller改造

### Task 8: 创建ConversationService会话管理服务

**Files:**
- Create: `src/main/java/com/ailearn/service/ConversationService.java`

- [ ] **Step 1: 创建ConversationService**

```java
package com.ailearn.service;

import com.ailearn.entity.ChatMessage;
import com.ailearn.entity.Conversation;
import com.ailearn.mapper.ChatMessageMapper;
import com.ailearn.mapper.ConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;

    public List<Conversation> listByType(String type) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Conversation::getType, type);
        }
        wrapper.orderByDesc(Conversation::getUpdatedAt);
        return conversationMapper.selectList(wrapper);
    }

    public Conversation create(String title, String type) {
        Conversation conversation = new Conversation();
        conversation.setTitle(title);
        conversation.setType(type);
        conversationMapper.insert(conversation);
        return conversation;
    }

    public List<ChatMessage> getMessages(Long conversationId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
               .orderByAsc(ChatMessage::getCreatedAt);
        return chatMessageMapper.selectList(wrapper);
    }

    public void addMessage(Long conversationId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        chatMessageMapper.insert(message);

        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            conv.setUpdatedAt(java.time.LocalDateTime.now());
            conversationMapper.updateById(conv);
        }
    }

    public void delete(Long id) {
        LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(ChatMessage::getConversationId, id);
        chatMessageMapper.delete(msgWrapper);
        conversationMapper.deleteById(id);
    }

    public Conversation getById(Long id) {
        return conversationMapper.selectById(id);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/ailearn/service/ConversationService.java
git commit -m "feat: 添加ConversationService会话管理服务"
```

---

### Task 9: 改造ChatController支持统一响应和API前缀

**Files:**
- Read first: `src/main/java/com/ailearn/controller/ChatController.java`
- Modify: `src/main/java/com/ailearn/controller/ChatController.java`
- Read first: `src/main/java/com/ailearn/chat/ChatService.java`

- [ ] **Step 1: 更新ChatService添加更好的流式支持**

Modify `src/main/java/com/ailearn/chat/ChatService.java`:

```java
package com.ailearn.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("你是一个专业的AI助手，请用简洁清晰的中文回答问题。")
                .build();
    }

    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }

    public Flux<String> streamChat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .stream()
                .content();
    }

    public String chatWithSystem(String userMessage, String systemPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }
}
```

- [ ] **Step 2: 重写ChatController使用Result统一响应和/api前缀**

```java
package com.ailearn.controller;

import com.ailearn.chat.ChatService;
import com.ailearn.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    public Result<String> send(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String systemPrompt = request.get("systemPrompt");
        String response;
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            response = chatService.chatWithSystem(message, systemPrompt);
        } else {
            response = chatService.chat(message);
        }
        return Result.success(response);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String message) {
        return chatService.streamChat(message);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ailearn/chat/ChatService.java src/main/java/com/ailearn/controller/ChatController.java
git commit -m "feat: 改造ChatController支持统一响应和API前缀"
```

---

### Task 10: 改造MemoryChatService支持MySQL持久化和MemoryChatController

**Files:**
- Read first: `src/main/java/com/ailearn/memory/MemoryChatService.java`
- Modify: `src/main/java/com/ailearn/memory/MemoryChatService.java`
- Read first: `src/main/java/com/ailearn/controller/MemoryChatController.java`
- Modify: `src/main/java/com/ailearn/controller/MemoryChatController.java`

- [ ] **Step 1: 重写MemoryChatService使用MySQL持久化记忆**

```java
package com.ailearn.memory;

import com.ailearn.entity.ChatMessage;
import com.ailearn.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryChatService {

    private final ChatClient.Builder chatClientBuilder;
    private final ConversationService conversationService;

    public String chat(Long conversationId, String userMessage) {
        conversationService.addMessage(conversationId, "user", userMessage);

        List<Message> messages = loadHistory(conversationId);
        messages.add(new UserMessage(userMessage));

        ChatClient chatClient = chatClientBuilder
                .defaultSystem("你是一个有记忆的AI助手，会记住之前的对话内容。请用中文回答。")
                .build();

        String response = chatClient.prompt()
                .messages(messages.toArray(new Message[0]))
                .call()
                .content();

        conversationService.addMessage(conversationId, "assistant", response);
        return response;
    }

    public Flux<String> streamChat(Long conversationId, String userMessage) {
        conversationService.addMessage(conversationId, "user", userMessage);

        List<Message> messages = loadHistory(conversationId);
        messages.add(new UserMessage(userMessage));

        ChatClient chatClient = chatClientBuilder
                .defaultSystem("你是一个有记忆的AI助手，会记住之前的对话内容。请用中文回答。")
                .build();

        StringBuilder responseBuilder = new StringBuilder();

        return chatClient.prompt()
                .messages(messages.toArray(new Message[0]))
                .stream()
                .content()
                .doOnNext(chunk -> responseBuilder.append(chunk))
                .doOnComplete(() -> conversationService.addMessage(conversationId, "assistant", responseBuilder.toString()));
    }

    private List<Message> loadHistory(Long conversationId) {
        List<ChatMessage> history = conversationService.getMessages(conversationId);
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        return messages;
    }
}
```

- [ ] **Step 2: 重写MemoryChatController**

```java
package com.ailearn.controller;

import com.ailearn.common.Result;
import com.ailearn.memory.MemoryChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MemoryChatController {

    private final MemoryChatService memoryChatService;

    @PostMapping("/chat/{conversationId}")
    public Result<String> chat(@PathVariable Long conversationId, @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String response = memoryChatService.chat(conversationId, message);
        return Result.success(response);
    }

    @GetMapping(value = "/stream/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@PathVariable Long conversationId, @RequestParam String message) {
        return memoryChatService.streamChat(conversationId, message);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ailearn/memory/MemoryChatService.java src/main/java/com/ailearn/controller/MemoryChatController.java
git commit -m "feat: 改造MemoryChat使用MySQL持久化和统一响应"
```

---

### Task 11: 改造其他Controller返回Result并添加API前缀

**Files:**
- Modify: `src/main/java/com/ailearn/controller/RagController.java`
- Modify: `src/main/java/com/ailearn/controller/AgentController.java`
- Modify: `src/main/java/com/ailearn/controller/StructuredOutputController.java`
- Modify: `src/main/java/com/ailearn/controller/ToolsController.java`

- [ ] **Step 1: 改造RagController**

先读取现有RagService.java和RagController.java，然后修改Controller：

```java
package com.ailearn.controller;

import com.ailearn.common.Result;
import com.ailearn.rag.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RagController {

    private final RagService ragService;

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody java.util.Map<String, String> request) {
        String message = request.get("message");
        return Result.success(ragService.chat(message));
    }

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        try {
            ragService.uploadDocument(file);
            return Result.success("文档上传成功");
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 改造AgentController**

```java
package com.ailearn.controller;

import com.ailearn.agent.AgentService;
import com.ailearn.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/travel-plan")
    public Result<String> travelPlan(@RequestBody Map<String, Object> request) {
        String destination = (String) request.get("destination");
        Integer days = (Integer) request.get("days");
        return Result.success(agentService.planTravel(destination, days));
    }

    @PostMapping("/task")
    public Result<String> executeTask(@RequestBody Map<String, String> request) {
        String goal = request.get("goal");
        return Result.success(agentService.executeTask(goal));
    }

    @GetMapping(value = "/stream/travel-plan", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamTravelPlan(@RequestParam String destination, @RequestParam int days) {
        return agentService.streamPlanTravel(destination, days);
    }
}
```

注意：需要在AgentService中添加streamPlanTravel方法。

- [ ] **Step 3: 更新AgentService添加流式方法**

Read first, then modify AgentService.java:

```java
package com.ailearn.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentService {

    private final ChatClient agentClient;

    public AgentService(ChatClient.Builder builder) {
        this.agentClient = builder
                .defaultSystem("""
                        你是一个专业的旅游规划 AI 助手，具有以下能力：
                        1. 查询各城市天气信息
                        2. 进行数学计算（预算、距离等）
                        3. 根据天气和用户需求给出个性化旅游建议

                        请主动使用工具获取真实信息，而不是凭空猜测。
                        思考步骤：先了解目的地天气 -> 制定行程 -> 估算费用 -> 给出建议
                        """)
                .defaultFunctions("weatherTool", "calculatorTool")
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    public String planTravel(String destination, int days) {
        String prompt = "请帮我规划 " + destination + " 的 " + days + " 天旅游计划。\n"
                + "要求：\n"
                + "1. 先查询目的地天气，根据天气推荐合适的活动\n"
                + "2. 规划每天的行程安排\n"
                + "3. 估算大概费用（住宿 400元/晚，餐饮 150元/天，景点 200元/天）\n"
                + "4. 给出行前准备建议";

        return agentClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    public Flux<String> streamPlanTravel(String destination, int days) {
        String prompt = "请帮我规划 " + destination + " 的 " + days + " 天旅游计划。\n"
                + "要求：\n"
                + "1. 先查询目的地天气，根据天气推荐合适的活动\n"
                + "2. 规划每天的行程安排\n"
                + "3. 估算大概费用（住宿 400元/晚，餐饮 150元/天，景点 200元/天）\n"
                + "4. 给出行前准备建议";

        return agentClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }

    public String executeTask(String goal) {
        return agentClient.prompt()
                .user(goal)
                .call()
                .content();
    }
}
```

- [ ] **Step 4: 改造StructuredOutputController**

```java
package com.ailearn.controller;

import com.ailearn.common.Result;
import com.ailearn.structured.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/structured")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StructuredOutputController {

    private final StructuredOutputService structuredOutputService;

    @PostMapping("/book")
    public Result<BookInfo> extractBook(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        return Result.success(structuredOutputService.extractBookInfo(text));
    }

    @PostMapping("/movie")
    public Result<MovieInfo> extractMovie(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        return Result.success(structuredOutputService.extractMovieInfo(text));
    }
}
```

- [ ] **Step 5: 改造ToolsController**

```java
package com.ailearn.controller;

import com.ailearn.common.Result;
import com.ailearn.tools.WeatherTool;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ToolsController {

    private final WeatherTool weatherTool;

    @GetMapping("/weather")
    public Result<String> weather(@RequestParam String city) {
        return Result.success(weatherTool.apply(city));
    }

    @PostMapping("/calculate")
    public Result<String> calculate(@RequestBody Map<String, String> request) {
        String expression = request.get("expression");
        String[] parts = expression.split("\\s+");
        if (parts.length != 3) {
            return Result.error("格式错误，请使用: 数字 运算符 数字（如：5 + 3）");
        }
        try {
            double a = Double.parseDouble(parts[0]);
            String op = parts[1];
            double b = Double.parseDouble(parts[2]);
            double result;
            switch (op) {
                case "+" -> result = a + b;
                case "-" -> result = a - b;
                case "*" -> result = a * b;
                case "/" -> result = a / b;
                default -> {
                    return Result.error("不支持的运算符: " + op);
                }
            }
            return Result.success(String.valueOf(result));
        } catch (NumberFormatException e) {
            return Result.error("数字格式错误");
        }
    }
}
```

- [ ] **Step 6: 更新WebConfig确保CORS正确**

确保WebConfig.java存在且配置正确：

```java
package com.ailearn.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

- [ ] **Step 7: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/ailearn/controller/ src/main/java/com/ailearn/agent/AgentService.java src/main/java/com/ailearn/config/WebConfig.java
git commit -m "feat: 改造所有Controller支持统一响应和API前缀"
```

---

### Task 12: 创建ConversationController会话管理接口

**Files:**
- Create: `src/main/java/com/ailearn/controller/ConversationController.java`

- [ ] **Step 1: 创建ConversationController**

```java
package com.ailearn.controller;

import com.ailearn.common.Result;
import com.ailearn.entity.ChatMessage;
import com.ailearn.entity.Conversation;
import com.ailearn.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public Result<List<Conversation>> list(@RequestParam(required = false) String type) {
        return Result.success(conversationService.listByType(type));
    }

    @PostMapping
    public Result<Conversation> create(@RequestBody Map<String, String> request) {
        String title = request.getOrDefault("title", "新对话");
        String type = request.getOrDefault("type", "chat");
        return Result.success(conversationService.create(title, type));
    }

    @GetMapping("/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long id) {
        return Result.success(conversationService.getMessages(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return Result.success();
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ailearn/controller/ConversationController.java
git commit -m "feat: 添加ConversationController会话管理接口"
```

---

## 第四阶段：前端项目初始化

### Task 13: 创建Vue3前端项目

**Files:**
- Create: `frontend/` directory with Vite Vue project

- [ ] **Step 1: 使用Vite创建Vue项目**

Run in project root:
```bash
npm create vite@latest frontend -- --template vue
```
Wait for prompts, select Vue → JavaScript (or TypeScript if preferred, but plan uses JS)

- [ ] **Step 2: 进入frontend目录安装依赖**

```bash
cd frontend
npm install
```

- [ ] **Step 3: 安装额外依赖**

```bash
npm install vue-router@4 pinia axios element-plus @element-plus/icons-vue marked highlight.js
```

- [ ] **Step 4: 配置vite.config.js代理**

Replace `frontend/vite.config.js` with:

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
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

- [ ] **Step 5: Commit前端初始化（在项目根目录执行）**

```bash
cd ..
git add frontend/
git commit -m "feat: 初始化Vue3 + Vite前端项目，安装依赖"
```

---

### Task 14: 配置前端全局样式（赛博朋克主题）

**Files:**
- Create: `frontend/src/assets/styles/cyber-theme.css`
- Create: `frontend/src/assets/styles/global.css`
- Modify: `frontend/src/main.js`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/index.html`

- [ ] **Step 1: 更新index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/vite.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>AI NEXUS - Spring AI 学习平台</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>
```

- [ ] **Step 2: 创建赛博朋克主题CSS**

Create `frontend/src/assets/styles/cyber-theme.css`:

```css
:root {
  --cyber-bg-primary: #0a0a1f;
  --cyber-bg-secondary: #0d0d2b;
  --cyber-bg-tertiary: #0d0d25;
  --cyber-bg-card: #151530;
  --cyber-cyan: #00ffff;
  --cyber-magenta: #ff00ff;
  --cyber-purple: #6b00ff;
  --cyber-green: #00ff88;
  --cyber-orange: #ffaa00;
  --cyber-text-primary: #ffffff;
  --cyber-text-secondary: #cccccc;
  --cyber-text-muted: #888888;
  --cyber-border: rgba(0, 255, 255, 0.2);
  --cyber-glow-cyan: 0 0 10px rgba(0, 255, 255, 0.5), 0 0 20px rgba(0, 255, 255, 0.2);
  --cyber-glow-magenta: 0 0 10px rgba(255, 0, 255, 0.5), 0 0 20px rgba(255, 0, 255, 0.2);
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Segoe UI', 'Microsoft YaHei', system-ui, sans-serif;
  background: var(--cyber-bg-primary);
  color: var(--cyber-text-primary);
  min-height: 100vh;
  overflow-x: hidden;
}

.cyber-grid-bg {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: 
    linear-gradient(rgba(0, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 30px 30px;
  pointer-events: none;
  z-index: 0;
}

.cyber-btn {
  background: linear-gradient(135deg, var(--cyber-magenta) 0%, var(--cyber-purple) 100%);
  border: none;
  color: white;
  padding: 12px 24px;
  font-weight: bold;
  cursor: pointer;
  font-size: 14px;
  letter-spacing: 1px;
  text-transform: uppercase;
  transition: all 0.3s ease;
  clip-path: polygon(10px 0, 100% 0, calc(100% - 10px) 100%, 0 100%);
}

.cyber-btn:hover {
  box-shadow: var(--cyber-glow-magenta);
  transform: translateY(-1px);
}

.cyber-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cyber-input {
  background: var(--cyber-bg-card);
  border: 1px solid rgba(0, 255, 255, 0.3);
  border-radius: 4px;
  padding: 12px 16px;
  color: white;
  font-size: 14px;
  outline: none;
  transition: all 0.3s ease;
  width: 100%;
}

.cyber-input:focus {
  border-color: var(--cyber-magenta);
  box-shadow: 0 0 15px rgba(255, 0, 255, 0.2), inset 0 0 10px rgba(255, 0, 255, 0.05);
}

.cyber-card {
  background: linear-gradient(135deg, rgba(26, 10, 46, 0.8) 0%, rgba(13, 26, 58, 0.8) 100%);
  border: 1px solid var(--cyber-border);
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 0 15px rgba(0, 255, 255, 0.05);
}

.cyber-glow-text {
  text-shadow: 0 0 10px currentColor, 0 0 20px currentColor;
}

::-webkit-scrollbar {
  width: 6px;
}

::-webkit-scrollbar-track {
  background: var(--cyber-bg-secondary);
}

::-webkit-scrollbar-thumb {
  background: var(--cyber-cyan);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--cyber-magenta);
}
```

- [ ] **Step 3: 创建global.css引入主题**

Create `frontend/src/assets/styles/global.css`:

```css
@import './cyber-theme.css';

#app {
  min-height: 100vh;
  position: relative;
  z-index: 1;
}
```

- [ ] **Step 4: 更新main.js配置**

Replace `frontend/src/main.js`:

```javascript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './assets/styles/global.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
```

- [ ] **Step 5: 更新App.vue为路由容器**

Replace `frontend/src/App.vue`:

```vue
<template>
  <div class="cyber-grid-bg"></div>
  <router-view />
</template>

<script setup>
</script>

<style>
</style>
```

- [ ] **Step 6: Commit**

```bash
git add frontend/index.html frontend/src/main.js frontend/src/App.vue frontend/src/assets/
git commit -m "feat: 配置赛博朋克主题全局样式"
```

---

### Task 15: 配置前端Router和API封装

**Files:**
- Create: `frontend/src/router/index.js`
- Create: `frontend/src/api/index.js`
- Create: `frontend/src/api/chat.js`
- Create: `frontend/src/api/conversation.js`

- [ ] **Step 1: 创建路由配置**

Create `frontend/src/router/index.js`:

```javascript
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue')
  },
  {
    path: '/memory',
    name: 'Memory',
    component: () => import('@/views/MemoryView.vue')
  },
  {
    path: '/rag',
    name: 'RAG',
    component: () => import('@/views/RagView.vue')
  },
  {
    path: '/agent',
    name: 'Agent',
    component: () => import('@/views/AgentView.vue')
  },
  {
    path: '/structured',
    name: 'Structured',
    component: () => import('@/views/StructuredView.vue')
  },
  {
    path: '/tools',
    name: 'Tools',
    component: () => import('@/views/ToolsView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
```

- [ ] **Step 2: 创建API基础封装**

Create `frontend/src/api/index.js`:

```javascript
import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.response.use(
  response => {
    if (response.data && response.data.code !== undefined) {
      if (response.data.code === 200) {
        return response.data.data
      }
      return Promise.reject(new Error(response.data.message || '请求失败'))
    }
    return response.data
  },
  error => {
    return Promise.reject(error)
  }
)

export default api
```

- [ ] **Step 3: 创建聊天API**

Create `frontend/src/api/chat.js`:

```javascript
import api from './index'

export function sendMessage(message, systemPrompt = '') {
  return api.post('/chat/send', { message, systemPrompt })
}

export function sendMemoryMessage(conversationId, message) {
  return api.post(`/memory/chat/${conversationId}`, { message })
}

export function sendRagMessage(message) {
  return api.post('/rag/chat', { message })
}

export function uploadDocument(file) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/rag/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function planTravel(destination, days) {
  return api.post('/agent/travel-plan', { destination, days })
}

export function executeTask(goal) {
  return api.post('/agent/task', { goal })
}

export function extractBook(text) {
  return api.post('/structured/book', { text })
}

export function extractMovie(text) {
  return api.post('/structured/movie', { text })
}

export function getWeather(city) {
  return api.get('/tools/weather', { params: { city } })
}

export function calculate(expression) {
  return api.post('/tools/calculate', { expression })
}
```

- [ ] **Step 4: 创建会话API**

Create `frontend/src/api/conversation.js`:

```javascript
import api from './index'

export function getConversations(type) {
  return api.get('/conversations', { params: { type } })
}

export function createConversation(title, type) {
  return api.post('/conversations', { title, type })
}

export function getMessages(conversationId) {
  return api.get(`/conversations/${conversationId}/messages`)
}

export function deleteConversation(conversationId) {
  return api.delete(`/conversations/${conversationId}`)
}
```

- [ ] **Step 5: 创建SSE流式请求工具**

Create `frontend/src/api/sse.js`:

```javascript
export function createSSE(url, onMessage, onError, onComplete) {
  const eventSource = new EventSource(url)
  
  eventSource.onmessage = (event) => {
    if (event.data === '[DONE]') {
      eventSource.close()
      onComplete && onComplete()
      return
    }
    onMessage && onMessage(event.data)
  }
  
  eventSource.onerror = (error) => {
    eventSource.close()
    onError && onError(error)
  }
  
  return eventSource
}

export function streamChat(message, onChunk, onError, onComplete) {
  return createSSE(`/api/chat/stream?message=${encodeURIComponent(message)}`, onChunk, onError, onComplete)
}

export function streamMemoryChat(conversationId, message, onChunk, onError, onComplete) {
  return createSSE(`/api/memory/stream/${conversationId}?message=${encodeURIComponent(message)}`, onChunk, onError, onComplete)
}
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/router/ frontend/src/api/
git commit -m "feat: 配置Vue Router和API封装"
```

---

## 第五阶段：前端布局与核心组件

### Task 16: 创建Pinia状态管理

**Files:**
- Create: `frontend/src/stores/chat.js`
- Create: `frontend/src/stores/conversation.js`
- Create: `frontend/src/stores/app.js`

- [ ] **Step 1: 创建聊天状态Store**

Create `frontend/src/stores/chat.js`:

```javascript
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useChatStore = defineStore('chat', () => {
  const messages = ref([])
  const isStreaming = ref(false)
  const currentConversationId = ref(null)

  function addMessage(role, content) {
    messages.value.push({ role, content, timestamp: Date.now() })
  }

  function appendToLastMessage(chunk) {
    if (messages.value.length > 0 && messages.value[messages.value.length - 1].role === 'assistant') {
      messages.value[messages.value.length - 1].content += chunk
    } else {
      addMessage('assistant', chunk)
    }
  }

  function setMessages(newMessages) {
    messages.value = newMessages.map(m => ({
      role: m.role,
      content: m.content,
      timestamp: new Date(m.createdAt).getTime()
    }))
  }

  function clearMessages() {
    messages.value = []
  }

  return {
    messages,
    isStreaming,
    currentConversationId,
    addMessage,
    appendToLastMessage,
    setMessages,
    clearMessages
  }
})
```

- [ ] **Step 2: 创建会话Store**

Create `frontend/src/stores/conversation.js`:

```javascript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getConversations, createConversation, deleteConversation } from '@/api/conversation'

export const useConversationStore = defineStore('conversation', () => {
  const conversations = ref([])
  const currentType = ref('chat')

  async function fetchConversations(type) {
    currentType.value = type || 'chat'
    conversations.value = await getConversations(type)
  }

  async function create(title, type) {
    const conv = await createConversation(title, type)
    await fetchConversations(type)
    return conv
  }

  async function remove(id) {
    await deleteConversation(id)
    await fetchConversations(currentType.value)
  }

  return {
    conversations,
    currentType,
    fetchConversations,
    create,
    remove
  }
})
```

- [ ] **Step 3: 创建App全局Store**

Create `frontend/src/stores/app.js`:

```javascript
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const activeNav = ref('chat')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setActiveNav(nav) {
    activeNav.value = nav
  }

  return {
    sidebarCollapsed,
    activeNav,
    toggleSidebar,
    setActiveNav
  }
})
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/stores/
git commit -m "feat: 添加Pinia状态管理"
```

---

### Task 17: 创建布局组件

**Files:**
- Create: `frontend/src/components/CyberLayout.vue`
- Create: `frontend/src/components/CyberHeader.vue`
- Create: `frontend/src/components/CyberSidebar.vue`
- Create: `frontend/src/views/` directory

- [ ] **Step 1: 创建目录**

Create `frontend/src/components/` and `frontend/src/views/` directories (they may already exist from Vite template).

- [ ] **Step 2: 创建CyberHeader.vue**

Create `frontend/src/components/CyberHeader.vue`:

```vue
<template>
  <header class="cyber-header">
    <div class="cyber-logo">
      <span class="logo-icon">⬡</span>
      <span class="logo-text">AI_NEXUS</span>
    </div>
    <nav class="cyber-nav">
      <router-link 
        v-for="item in navItems" 
        :key="item.path"
        :to="item.path"
        class="cyber-nav-item"
        :class="{ active: $route.path === item.path }"
        @click="setActive(item.name)"
      >
        <span class="nav-icon">{{ item.icon }}</span>
        <span class="nav-text">{{ item.label }}</span>
      </router-link>
    </nav>
    <div class="cyber-status">
      <span class="status-dot"></span>
      <span class="status-text">OLLAMA: QWEN2.5</span>
    </div>
  </header>
</template>

<script setup>
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()

const navItems = [
  { path: '/', name: 'chat', label: '聊天', icon: '💬' },
  { path: '/memory', name: 'memory', label: '记忆', icon: '🧠' },
  { path: '/rag', name: 'rag', label: '知识库', icon: '📚' },
  { path: '/agent', name: 'agent', label: 'Agent', icon: '🤖' },
  { path: '/structured', name: 'structured', label: '结构化', icon: '📋' },
  { path: '/tools', name: 'tools', label: '工具', icon: '🔧' }
]

function setActive(name) {
  appStore.setActiveNav(name)
}
</script>

<style scoped>
.cyber-header {
  background: linear-gradient(90deg, #0d0d2b 0%, #1a0a2e 50%, #0d0d2b 100%);
  border-bottom: 1px solid rgba(255, 0, 255, 0.3);
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 100;
}

.cyber-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  font-size: 24px;
  color: var(--cyber-cyan);
  text-shadow: 0 0 10px var(--cyber-cyan);
}

.logo-text {
  color: var(--cyber-cyan);
  font-weight: bold;
  font-size: 18px;
  letter-spacing: 3px;
  text-shadow: 0 0 10px var(--cyber-cyan), 0 0 20px rgba(0, 255, 255, 0.4);
}

.cyber-nav {
  display: flex;
  gap: 8px;
}

.cyber-nav-item {
  color: var(--cyber-text-muted);
  padding: 8px 16px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.3s ease;
  text-decoration: none;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  border-radius: 4px;
}

.cyber-nav-item:hover,
.cyber-nav-item.active {
  color: var(--cyber-magenta);
  border-color: rgba(255, 0, 255, 0.4);
  text-shadow: 0 0 8px var(--cyber-magenta);
  box-shadow: 0 0 10px rgba(255, 0, 255, 0.1);
  background: rgba(255, 0, 255, 0.05);
}

.cyber-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--cyber-green);
}

.status-dot {
  width: 8px;
  height: 8px;
  background: var(--cyber-green);
  border-radius: 50%;
  box-shadow: 0 0 8px var(--cyber-green);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
```

- [ ] **Step 3: 创建CyberSidebar.vue**

Create `frontend/src/components/CyberSidebar.vue`:

```vue
<template>
  <aside class="cyber-sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <div class="sidebar-header">
      <span class="sidebar-title">会话历史</span>
      <button class="new-chat-btn" @click="createNewConversation">
        <span>+</span> 新建
      </button>
    </div>
    <div class="conversation-list">
      <div 
        v-for="conv in conversationStore.conversations" 
        :key="conv.id"
        class="conversation-item"
        :class="{ active: chatStore.currentConversationId === conv.id }"
        @click="selectConversation(conv)"
      >
        <span class="conv-title">{{ conv.title }}</span>
        <button class="delete-btn" @click.stop="deleteConv(conv.id)">×</button>
      </div>
      <div v-if="conversationStore.conversations.length === 0" class="empty-hint">
        暂无会话记录
      </div>
    </div>
  </aside>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { useConversationStore } from '@/stores/conversation'
import { useAppStore } from '@/stores/app'
import { getMessages } from '@/api/conversation'

const chatStore = useChatStore()
const conversationStore = useConversationStore()
const appStore = useAppStore()
const router = useRouter()
const route = useRoute()

const typeMap = {
  '/': 'chat',
  '/memory': 'memory',
  '/rag': 'rag',
  '/agent': 'agent'
}

onMounted(() => {
  loadConversations()
})

async function loadConversations() {
  const type = typeMap[route.path] || 'chat'
  await conversationStore.fetchConversations(type)
}

async function createNewConversation() {
  const type = typeMap[route.path] || 'chat'
  const conv = await conversationStore.create('新对话', type)
  chatStore.currentConversationId = conv.id
  chatStore.clearMessages()
}

async function selectConversation(conv) {
  chatStore.currentConversationId = conv.id
  const messages = await getMessages(conv.id)
  chatStore.setMessages(messages)
}

async function deleteConv(id) {
  if (confirm('确定删除此会话吗？')) {
    await conversationStore.remove(id)
    if (chatStore.currentConversationId === id) {
      chatStore.currentConversationId = null
      chatStore.clearMessages()
    }
  }
}

defineExpose({ loadConversations })
</script>

<style scoped>
.cyber-sidebar {
  width: 240px;
  background: var(--cyber-bg-tertiary);
  border-right: 1px solid rgba(107, 0, 255, 0.2);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
}

.cyber-sidebar.collapsed {
  width: 60px;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid rgba(107, 0, 255, 0.2);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-title {
  color: var(--cyber-cyan);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 1px;
  opacity: 0.7;
}

.new-chat-btn {
  background: linear-gradient(135deg, var(--cyber-magenta) 0%, var(--cyber-purple) 100%);
  border: none;
  color: white;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.3s;
}

.new-chat-btn:hover {
  box-shadow: 0 0 10px rgba(255, 0, 255, 0.5);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.conversation-item {
  color: var(--cyber-text-secondary);
  font-size: 13px;
  padding: 10px 12px;
  margin-bottom: 6px;
  background: var(--cyber-bg-card);
  border-left: 2px solid rgba(255, 0, 255, 0.3);
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-radius: 0 4px 4px 0;
}

.conversation-item:hover {
  border-left-color: var(--cyber-cyan);
  background: #252550;
  box-shadow: inset 0 0 20px rgba(0, 255, 255, 0.05);
}

.conversation-item.active {
  border-left-color: var(--cyber-magenta);
  color: white;
  background: linear-gradient(90deg, rgba(255, 0, 255, 0.1), transparent);
}

.conv-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.delete-btn {
  background: none;
  border: none;
  color: var(--cyber-text-muted);
  cursor: pointer;
  font-size: 16px;
  padding: 0 4px;
  opacity: 0;
  transition: opacity 0.3s;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  color: var(--cyber-magenta);
}

.empty-hint {
  color: var(--cyber-text-muted);
  font-size: 13px;
  text-align: center;
  padding: 40px 20px;
  opacity: 0.5;
}
</style>
```

- [ ] **Step 4: 创建CyberLayout.vue布局容器**

Create `frontend/src/components/CyberLayout.vue`:

```vue
<template>
  <div class="cyber-layout">
    <CyberHeader />
    <div class="layout-body">
      <CyberSidebar ref="sidebarRef" />
      <main class="main-content">
        <div class="scanline"></div>
        <slot></slot>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import CyberHeader from './CyberHeader.vue'
import CyberSidebar from './CyberSidebar.vue'

const sidebarRef = ref(null)
</script>

<style scoped>
.cyber-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.layout-body {
  display: flex;
  flex: 1;
  height: calc(100vh - 60px);
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, var(--cyber-bg-primary) 0%, var(--cyber-bg-secondary) 100%);
  position: relative;
  overflow: hidden;
}

.scanline {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(0, 255, 255, 0.3), transparent);
  animation: scan 4s linear infinite;
  pointer-events: none;
  z-index: 10;
}

@keyframes scan {
  0% { top: 0; }
  100% { top: 100%; }
}
</style>
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/
git commit -m "feat: 创建布局组件(Header/Sidebar/Layout)"
```

---

### Task 18: 创建聊天消息组件和输入组件

**Files:**
- Create: `frontend/src/components/ChatMessage.vue`
- Create: `frontend/src/components/ChatInput.vue`

- [ ] **Step 1: 创建ChatMessage.vue消息气泡组件**

```vue
<template>
  <div class="chat-message" :class="message.role">
    <div class="message-avatar">
      {{ message.role === 'user' ? 'YOU' : 'AI' }}
    </div>
    <div class="message-body">
      <div class="message-content" v-html="renderedContent"></div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-dark.css'

marked.setOptions({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
})

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const renderedContent = computed(() => {
  return marked.parse(props.message.content || '')
})
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: bold;
  flex-shrink: 0;
}

.chat-message.ai .message-avatar {
  background: var(--cyber-cyan);
  color: #000;
  box-shadow: 0 0 10px var(--cyber-cyan);
}

.chat-message.user .message-avatar {
  background: var(--cyber-magenta);
  color: white;
  box-shadow: 0 0 10px var(--cyber-magenta);
}

.message-body {
  flex: 1;
  max-width: calc(100% - 60px);
}

.message-content {
  padding: 14px 18px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.chat-message.ai .message-content {
  background: linear-gradient(135deg, rgba(26, 10, 46, 0.9) 0%, rgba(13, 26, 58, 0.9) 100%);
  border: 1px solid rgba(0, 255, 255, 0.2);
  color: var(--cyber-text-secondary);
  box-shadow: 0 0 15px rgba(0, 255, 255, 0.05);
}

.chat-message.user .message-content {
  background: linear-gradient(135deg, rgba(42, 10, 58, 0.9) 0%, rgba(58, 10, 46, 0.9) 100%);
  border: 1px solid rgba(255, 0, 255, 0.2);
  color: var(--cyber-text-primary);
  box-shadow: 0 0 15px rgba(255, 0, 255, 0.05);
  margin-left: auto;
  max-width: 80%;
}

.message-content :deep(pre) {
  background: #0a0a1a;
  border: 1px solid rgba(0, 255, 255, 0.2);
  border-radius: 4px;
  padding: 12px;
  overflow-x: auto;
  margin: 10px 0;
}

.message-content :deep(code) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}

.message-content :deep(p) {
  margin-bottom: 10px;
}

.message-content :deep(p:last-child) {
  margin-bottom: 0;
}

.message-content :deep(ul), .message-content :deep(ol) {
  padding-left: 20px;
  margin: 10px 0;
}

.message-content :deep(h1), .message-content :deep(h2), .message-content :deep(h3) {
  color: var(--cyber-cyan);
  margin: 15px 0 10px;
}
</style>
```

- [ ] **Step 2: 创建ChatInput.vue输入组件**

```vue
<template>
  <div class="chat-input-area">
    <div class="input-wrapper">
      <textarea
        v-model="inputText"
        class="cyber-textarea"
        :placeholder="placeholder"
        @keydown="handleKeydown"
        :disabled="disabled"
        rows="1"
        ref="textareaRef"
      ></textarea>
      <button class="cyber-btn send-btn" @click="sendMessage" :disabled="disabled || !inputText.trim()">
        {{ disabled ? '生成中...' : '发送 ➤' }}
      </button>
    </div>
    <div class="input-hint">按 Enter 发送，Shift+Enter 换行</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  placeholder: {
    type: String,
    default: '输入消息...'
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send'])

const inputText = ref('')
const textareaRef = ref(null)

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function sendMessage() {
  if (inputText.value.trim() && !props.disabled) {
    emit('send', inputText.value.trim())
    inputText.value = ''
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  }
}
</script>

<style scoped>
.chat-input-area {
  padding: 16px 24px;
  border-top: 1px solid rgba(107, 0, 255, 0.2);
  background: var(--cyber-bg-secondary);
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.cyber-textarea {
  flex: 1;
  background: var(--cyber-bg-card);
  border: 1px solid rgba(0, 255, 255, 0.3);
  border-radius: 4px;
  padding: 12px 16px;
  color: white;
  font-size: 14px;
  outline: none;
  transition: all 0.3s;
  resize: none;
  max-height: 150px;
  font-family: inherit;
  line-height: 1.5;
}

.cyber-textarea:focus {
  border-color: var(--cyber-magenta);
  box-shadow: 0 0 15px rgba(255, 0, 255, 0.2), inset 0 0 10px rgba(255, 0, 255, 0.05);
}

.cyber-textarea:disabled {
  opacity: 0.5;
}

.send-btn {
  padding: 12px 20px;
  white-space: nowrap;
}

.input-hint {
  font-size: 11px;
  color: var(--cyber-text-muted);
  margin-top: 8px;
  text-align: center;
  opacity: 0.6;
}
</style>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ChatMessage.vue frontend/src/components/ChatInput.vue
git commit -m "feat: 创建聊天消息和输入组件"
```

---

## 第六阶段：前端各功能页面

### Task 19: 创建基础聊天页面ChatView

**Files:**
- Create: `frontend/src/views/ChatView.vue`

- [ ] **Step 1: 创建ChatView.vue聊天页面**

```vue
<template>
  <CyberLayout>
    <div class="chat-view">
      <div class="messages-area" ref="messagesRef">
        <div v-if="chatStore.messages.length === 0" class="welcome-screen">
          <div class="welcome-icon">⬡</div>
          <h2 class="welcome-title">欢迎使用 AI NEXUS</h2>
          <p class="welcome-desc">基于 Spring AI + Ollama 构建的智能对话平台</p>
          <div class="quick-actions">
            <button class="quick-btn" v-for="q in quickQuestions" :key="q" @click="askQuick(q)">
              {{ q }}
            </button>
          </div>
        </div>
        <ChatMessage
          v-for="(msg, index) in chatStore.messages"
          :key="index"
          :message="msg"
        />
        <div v-if="chatStore.isStreaming" class="typing-indicator">
          <span></span><span></span><span></span>
          AI 正在思考...
        </div>
      </div>
      <ChatInput :disabled="chatStore.isStreaming" @send="handleSend" />
    </div>
  </CyberLayout>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import CyberLayout from '@/components/CyberLayout.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import { useChatStore } from '@/stores/chat'
import { sendMessage } from '@/api/chat'
import { streamChat } from '@/api/sse'

const chatStore = useChatStore()
const messagesRef = ref(null)

const quickQuestions = [
  '解释一下Spring AI的核心概念',
  '什么是Function Calling?',
  '如何实现RAG检索增强生成?'
]

onMounted(() => {
  chatStore.clearMessages()
  chatStore.currentConversationId = null
})

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function askQuick(question) {
  handleSend(question)
}

async function handleSend(message) {
  chatStore.addMessage('user', message)
  chatStore.isStreaming = true
  scrollToBottom()

  try {
    let fullResponse = ''
    const eventSource = streamChat(
      message,
      (chunk) => {
        fullResponse += chunk
        if (!chatStore.messages[chatStore.messages.length - 1] || 
            chatStore.messages[chatStore.messages.length - 1].role !== 'assistant') {
          chatStore.addMessage('assistant', chunk)
        } else {
          chatStore.messages[chatStore.messages.length - 1].content = fullResponse
        }
        scrollToBottom()
      },
      (error) => {
        console.error('Stream error:', error)
        chatStore.isStreaming = false
      },
      () => {
        chatStore.isStreaming = false
      }
    )
  } catch (error) {
    chatStore.addMessage('assistant', '抱歉，发生了错误: ' + error.message)
    chatStore.isStreaming = false
  }
}
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.welcome-screen {
  text-align: center;
  padding: 60px 20px;
}

.welcome-icon {
  font-size: 64px;
  color: var(--cyber-cyan);
  text-shadow: 0 0 30px var(--cyber-cyan), 0 0 60px rgba(0, 255, 255, 0.3);
  margin-bottom: 20px;
}

.welcome-title {
  color: var(--cyber-cyan);
  font-size: 28px;
  font-weight: bold;
  margin-bottom: 12px;
  letter-spacing: 2px;
}

.welcome-desc {
  color: var(--cyber-text-muted);
  font-size: 14px;
  margin-bottom: 30px;
}

.quick-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

.quick-btn {
  background: transparent;
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: var(--cyber-cyan);
  padding: 10px 20px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.3s;
}

.quick-btn:hover {
  border-color: var(--cyber-magenta);
  color: var(--cyber-magenta);
  box-shadow: 0 0 15px rgba(255, 0, 255, 0.2);
  background: rgba(255, 0, 255, 0.05);
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  color: var(--cyber-cyan);
  font-size: 13px;
  opacity: 0.8;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  background: var(--cyber-cyan);
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/ChatView.vue
git commit -m "feat: 创建基础聊天页面"
```

---

### Task 20: 创建其他功能页面（占位+核心功能）

**Files:**
- Create: `frontend/src/views/MemoryView.vue`
- Create: `frontend/src/views/RagView.vue`
- Create: `frontend/src/views/AgentView.vue`
- Create: `frontend/src/views/StructuredView.vue`
- Create: `frontend/src/views/ToolsView.vue`

- [ ] **Step 1: 创建MemoryView.vue记忆对话页面**

```vue
<template>
  <CyberLayout>
    <div class="chat-view">
      <div class="page-header">
        <h2 class="page-title">🧠 记忆对话</h2>
        <p class="page-desc">AI 会记住之前的对话内容，支持多轮上下文对话（MySQL持久化）</p>
      </div>
      <div class="messages-area" ref="messagesRef">
        <div v-if="chatStore.messages.length === 0" class="welcome-screen">
          <p>请从左侧选择一个会话，或点击"新建"开始新的记忆对话</p>
        </div>
        <ChatMessage
          v-for="(msg, index) in chatStore.messages"
          :key="index"
          :message="msg"
        />
        <div v-if="chatStore.isStreaming" class="typing-indicator">
          <span></span><span></span><span></span>
          AI 正在思考...
        </div>
      </div>
      <ChatInput :disabled="chatStore.isStreaming" @send="handleSend" placeholder="输入消息，AI将记住对话历史..." />
    </div>
  </CyberLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import CyberLayout from '@/components/CyberLayout.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import { useChatStore } from '@/stores/chat'
import { useConversationStore } from '@/stores/conversation'
import { streamMemoryChat } from '@/api/sse'
import { getMessages } from '@/api/conversation'

const chatStore = useChatStore()
const conversationStore = useConversationStore()
const messagesRef = ref(null)

onMounted(async () => {
  chatStore.clearMessages()
  chatStore.currentConversationId = null
  await conversationStore.fetchConversations('memory')
})

function scrollToBottom() {
  setTimeout(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }, 50)
}

async function handleSend(message) {
  if (!chatStore.currentConversationId) {
    const conv = await conversationStore.create('记忆对话', 'memory')
    chatStore.currentConversationId = conv.id
  }
  
  chatStore.addMessage('user', message)
  chatStore.isStreaming = true
  scrollToBottom()

  let fullResponse = ''
  streamMemoryChat(
    chatStore.currentConversationId,
    message,
    (chunk) => {
      fullResponse += chunk
      const lastIdx = chatStore.messages.length - 1
      if (lastIdx >= 0 && chatStore.messages[lastIdx].role === 'assistant') {
        chatStore.messages[lastIdx].content = fullResponse
      } else {
        chatStore.addMessage('assistant', chunk)
      }
      scrollToBottom()
    },
    (err) => { console.error(err); chatStore.isStreaming = false },
    () => { 
      chatStore.isStreaming = false 
      conversationStore.fetchConversations('memory')
    }
  )
}
</script>

<style scoped>
.chat-view { display: flex; flex-direction: column; height: 100%; }
.page-header { padding: 20px 24px; border-bottom: 1px solid rgba(107,0,255,0.2); }
.page-title { color: var(--cyber-magenta); font-size: 20px; margin-bottom: 6px; }
.page-desc { color: var(--cyber-text-muted); font-size: 13px; }
.messages-area { flex: 1; overflow-y: auto; padding: 24px; }
.welcome-screen { text-align: center; padding: 60px; color: var(--cyber-text-muted); }
.typing-indicator { display: flex; align-items: center; gap: 8px; padding: 14px; color: var(--cyber-magenta); font-size: 13px; opacity: 0.8; }
.typing-indicator span { width: 6px; height: 6px; background: var(--cyber-magenta); border-radius: 50%; animation: bounce 1.4s infinite; }
.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }
@keyframes bounce { 0%,80%,100%{transform:scale(0)} 40%{transform:scale(1)} }
</style>
```

- [ ] **Step 2: 创建RagView.vue知识库页面**

```vue
<template>
  <CyberLayout>
    <div class="page-view">
      <div class="page-header">
        <h2 class="page-title">📚 RAG 知识库</h2>
        <p class="page-desc">上传文档，基于知识库内容进行问答</p>
      </div>
      <div class="rag-container">
        <div class="upload-section cyber-card">
          <h3>文档上传</h3>
          <div class="upload-area" @click="$refs.fileInput.click()" @dragover.prevent @drop="handleDrop">
            <input ref="fileInput" type="file" accept=".txt,.md,.pdf" hidden @change="handleFileSelect" />
            <p>点击或拖拽文件到此处上传</p>
            <p class="hint">支持 .txt, .md 格式</p>
          </div>
          <div v-if="uploadStatus" class="upload-status" :class="{ success: uploadSuccess }">
            {{ uploadStatus }}
          </div>
        </div>
        <div class="chat-section">
          <div class="messages-area">
            <ChatMessage
              v-for="(msg, index) in messages"
              :key="index"
              :message="msg"
            />
          </div>
          <ChatInput :disabled="loading" @send="handleSend" placeholder="基于知识库提问..." />
        </div>
      </div>
    </div>
  </CyberLayout>
</template>

<script setup>
import { ref } from 'vue'
import CyberLayout from '@/components/CyberLayout.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import { sendRagMessage, uploadDocument } from '@/api/chat'

const messages = ref([])
const loading = ref(false)
const uploadStatus = ref('')
const uploadSuccess = ref(false)
const fileInput = ref(null)

async function handleFileSelect(e) {
  const file = e.target.files[0]
  if (file) await upload(file)
}

async function handleDrop(e) {
  const file = e.dataTransfer.files[0]
  if (file) await upload(file)
}

async function upload(file) {
  uploadStatus.value = '上传中...'
  uploadSuccess.value = false
  try {
    await uploadDocument(file)
    uploadStatus.value = '✓ 文档上传成功！'
    uploadSuccess.value = true
  } catch (e) {
    uploadStatus.value = '上传失败: ' + e.message
  }
}

async function handleSend(message) {
  messages.value.push({ role: 'user', content: message })
  loading.value = true
  try {
    const response = await sendRagMessage(message)
    messages.value.push({ role: 'assistant', content: response })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '错误: ' + e.message })
  }
  loading.value = false
}
</script>

<style scoped>
.page-view { display: flex; flex-direction: column; height: 100%; }
.page-header { padding: 20px 24px; border-bottom: 1px solid rgba(107,0,255,0.2); }
.page-title { color: var(--cyber-cyan); font-size: 20px; margin-bottom: 6px; }
.page-desc { color: var(--cyber-text-muted); font-size: 13px; }
.rag-container { display: flex; flex: 1; overflow: hidden; }
.upload-section { width: 320px; margin: 20px; padding: 20px; }
.upload-section h3 { color: var(--cyber-cyan); margin-bottom: 16px; font-size: 16px; }
.upload-area { border: 2px dashed rgba(0,255,255,0.3); border-radius: 8px; padding: 40px 20px; text-align: center; cursor: pointer; transition: all 0.3s; }
.upload-area:hover { border-color: var(--cyber-magenta); background: rgba(255,0,255,0.05); }
.upload-area p { color: var(--cyber-text-secondary); }
.upload-area .hint { color: var(--cyber-text-muted); font-size: 12px; margin-top: 8px; }
.upload-status { margin-top: 12px; padding: 10px; border-radius: 4px; font-size: 13px; background: rgba(255,170,0,0.1); color: var(--cyber-orange); }
.upload-status.success { background: rgba(0,255,136,0.1); color: var(--cyber-green); }
.chat-section { flex: 1; display: flex; flex-direction: column; border-left: 1px solid rgba(107,0,255,0.2); }
.messages-area { flex: 1; overflow-y: auto; padding: 24px; }
</style>
```

- [ ] **Step 3: 创建AgentView.vue智能体页面**

```vue
<template>
  <CyberLayout>
    <div class="page-view">
      <div class="page-header">
        <h2 class="page-title">🤖 Agent 智能体</h2>
        <p class="page-desc">AI 可自主调用工具（天气查询、计算器）完成复杂任务</p>
      </div>
      <div class="agent-container">
        <div class="form-panel cyber-card">
          <h3>旅游规划助手</h3>
          <div class="form-group">
            <label>目的地</label>
            <input v-model="destination" class="cyber-input" placeholder="例如: 北京、上海、杭州..." />
          </div>
          <div class="form-group">
            <label>天数</label>
            <input v-model.number="days" type="number" min="1" max="30" class="cyber-input" />
          </div>
          <button class="cyber-btn" @click="planTrip" :disabled="loading">
            {{ loading ? '规划中...' : '开始规划' }}
          </button>
          
          <div class="divider"></div>
          
          <h3>自定义任务</h3>
          <textarea v-model="taskGoal" class="cyber-input" rows="3" placeholder="输入任意任务，Agent将自动使用工具完成..."></textarea>
          <button class="cyber-btn" style="margin-top: 12px;" @click="runTask" :disabled="loading">
            执行任务
          </button>
        </div>
        <div class="result-panel">
          <div class="result-content">
            <div v-if="!result && !loading" class="placeholder">
              输入参数后点击按钮，Agent将自动规划并展示结果
            </div>
            <div v-if="loading" class="loading">
              <div class="spinner"></div>
              Agent 正在思考和调用工具...
            </div>
            <div v-if="result" class="result-text" v-html="renderedResult"></div>
          </div>
        </div>
      </div>
    </div>
  </CyberLayout>
</template>

<script setup>
import { ref, computed } from 'vue'
import { marked } from 'marked'
import CyberLayout from '@/components/CyberLayout.vue'
import { planTravel, executeTask } from '@/api/chat'

const destination = ref('')
const days = ref(3)
const taskGoal = ref('')
const result = ref('')
const loading = ref(false)

const renderedResult = computed(() => marked.parse(result.value || ''))

async function planTrip() {
  if (!destination.value) return
  loading.value = true
  result.value = ''
  try {
    result.value = await planTravel(destination.value, days.value)
  } catch (e) {
    result.value = '错误: ' + e.message
  }
  loading.value = false
}

async function runTask() {
  if (!taskGoal.value) return
  loading.value = true
  result.value = ''
  try {
    result.value = await executeTask(taskGoal.value)
  } catch (e) {
    result.value = '错误: ' + e.message
  }
  loading.value = false
}
</script>

<style scoped>
.page-view { display: flex; flex-direction: column; height: 100%; }
.page-header { padding: 20px 24px; border-bottom: 1px solid rgba(107,0,255,0.2); }
.page-title { color: var(--cyber-purple); font-size: 20px; margin-bottom: 6px; }
.page-desc { color: var(--cyber-text-muted); font-size: 13px; }
.agent-container { display: flex; flex: 1; overflow: hidden; padding: 20px; gap: 20px; }
.form-panel { width: 340px; }
.form-panel h3 { color: var(--cyber-purple); margin-bottom: 16px; font-size: 16px; }
.form-group { margin-bottom: 14px; }
.form-group label { display: block; color: var(--cyber-text-secondary); font-size: 13px; margin-bottom: 6px; }
.divider { height: 1px; background: rgba(107,0,255,0.2); margin: 24px 0; }
.result-panel { flex: 1; background: linear-gradient(135deg,rgba(26,10,46,0.6),rgba(13,26,58,0.6)); border: 1px solid var(--cyber-border); border-radius: 8px; padding: 24px; overflow-y: auto; }
.placeholder { color: var(--cyber-text-muted); text-align: center; padding: 80px 20px; }
.loading { display: flex; align-items: center; gap: 12px; justify-content: center; padding: 80px; color: var(--cyber-purple); }
.spinner { width: 24px; height: 24px; border: 3px solid rgba(107,0,255,0.2); border-top-color: var(--cyber-purple); border-radius: 50%; animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.result-text { color: var(--cyber-text-secondary); line-height: 1.8; font-size: 14px; }
.result-text :deep(h1),:deep(h2),:deep(h3) { color: var(--cyber-purple); margin: 16px 0 10px; }
.result-text :deep(pre) { background: #0a0a1a; padding: 12px; border-radius: 4px; overflow-x: auto; }
.result-text :deep(ul),:deep(ol) { padding-left: 24px; margin: 10px 0; }
</style>
```

- [ ] **Step 4: 创建StructuredView.vue结构化输出页面**

```vue
<template>
  <CyberLayout>
    <div class="page-view">
      <div class="page-header">
        <h2 class="page-title">📋 结构化输出</h2>
        <p class="page-desc">AI 将文本转换为结构化的 JSON 数据（图书/电影信息提取）</p>
      </div>
      <div class="structured-container">
        <div class="input-section cyber-card">
          <h3>输入文本</h3>
          <textarea v-model="inputText" class="cyber-input" rows="8" placeholder="输入包含图书或电影信息的文本..."></textarea>
          <div class="button-group">
            <button class="cyber-btn" @click="extractBook" :disabled="loading">提取图书信息</button>
            <button class="cyber-btn" style="background: linear-gradient(135deg,#00ffff,#6b00ff);" @click="extractMovie" :disabled="loading">提取电影信息</button>
          </div>
        </div>
        <div class="output-section cyber-card">
          <h3>结构化结果</h3>
          <div v-if="loading" class="loading">提取中...</div>
          <pre v-else-if="result" class="json-output">{{ JSON.stringify(result, null, 2) }}</pre>
          <div v-else class="placeholder">提取的 JSON 结果将显示在这里</div>
        </div>
      </div>
    </div>
  </CyberLayout>
</template>

<script setup>
import { ref } from 'vue'
import CyberLayout from '@/components/CyberLayout.vue'
import { extractBook as apiExtractBook, extractMovie as apiExtractMovie } from '@/api/chat'

const inputText = ref('《三体》是刘慈欣创作的科幻小说，由重庆出版社出版，ISBN为9787536692930。这本书讲述了地球人类文明和三体文明的信息交流、生死搏杀及两个文明在宇宙中的兴衰历程。')
const result = ref(null)
const loading = ref(false)

async function extractBook() {
  if (!inputText.value) return
  loading.value = true
  try { result.value = await apiExtractBook(inputText.value) }
  catch (e) { result.value = { error: e.message } }
  loading.value = false
}

async function extractMovie() {
  if (!inputText.value) return
  loading.value = true
  try { result.value = await apiExtractMovie(inputText.value) }
  catch (e) { result.value = { error: e.message } }
  loading.value = false
}
</script>

<style scoped>
.page-view { display: flex; flex-direction: column; height: 100%; }
.page-header { padding: 20px 24px; border-bottom: 1px solid rgba(107,0,255,0.2); }
.page-title { color: var(--cyber-orange); font-size: 20px; margin-bottom: 6px; }
.page-desc { color: var(--cyber-text-muted); font-size: 13px; }
.structured-container { display: flex; gap: 20px; padding: 20px; flex: 1; overflow: hidden; }
.input-section, .output-section { flex: 1; display: flex; flex-direction: column; }
.input-section h3, .output-section h3 { color: var(--cyber-orange); margin-bottom: 16px; font-size: 16px; }
.button-group { display: flex; gap: 12px; margin-top: 16px; }
.json-output { background: #0a0a1a; border: 1px solid rgba(0,255,255,0.2); border-radius: 4px; padding: 16px; color: var(--cyber-green); font-family: 'Consolas',monospace; font-size: 13px; overflow: auto; flex: 1; margin: 0; }
.placeholder, .loading { flex: 1; display: flex; align-items: center; justify-content: center; color: var(--cyber-text-muted); }
.loading { color: var(--cyber-orange); }
</style>
```

- [ ] **Step 5: 创建ToolsView.vue工具演示页面**

```vue
<template>
  <CyberLayout>
    <div class="page-view">
      <div class="page-header">
        <h2 class="page-title">🔧 工具演示</h2>
        <p class="page-desc">直接测试 AI 可调用的工具：天气查询、计算器</p>
      </div>
      <div class="tools-container">
        <div class="tool-card cyber-card">
          <h3>🌤️ 天气查询</h3>
          <div class="tool-desc">查询城市天气信息（模拟数据）</div>
          <div class="tool-form">
            <input v-model="city" class="cyber-input" placeholder="输入城市名..." />
            <button class="cyber-btn" @click="queryWeather" :disabled="weatherLoading">查询</button>
          </div>
          <div v-if="weatherResult" class="tool-result">{{ weatherResult }}</div>
        </div>
        <div class="tool-card cyber-card">
          <h3>🧮 计算器</h3>
          <div class="tool-desc">支持 + - * / 四则运算</div>
          <div class="tool-form">
            <input v-model="expression" class="cyber-input" placeholder="例如: 5 + 3" />
            <button class="cyber-btn" @click="calc" :disabled="calcLoading">计算</button>
          </div>
          <div v-if="calcResult" class="tool-result">结果: {{ calcResult }}</div>
        </div>
      </div>
    </div>
  </CyberLayout>
</template>

<script setup>
import { ref } from 'vue'
import CyberLayout from '@/components/CyberLayout.vue'
import { getWeather, calculate } from '@/api/chat'

const city = ref('')
const expression = ref('')
const weatherResult = ref('')
const calcResult = ref('')
const weatherLoading = ref(false)
const calcLoading = ref(false)

async function queryWeather() {
  if (!city.value) return
  weatherLoading.value = true
  try { weatherResult.value = await getWeather(city.value) }
  catch (e) { weatherResult.value = '错误: ' + e.message }
  weatherLoading.value = false
}

async function calc() {
  if (!expression.value) return
  calcLoading.value = true
  try { calcResult.value = await calculate(expression.value) }
  catch (e) { calcResult.value = '错误: ' + e.message }
  calcLoading.value = false
}
</script>

<style scoped>
.page-view { display: flex; flex-direction: column; height: 100%; }
.page-header { padding: 20px 24px; border-bottom: 1px solid rgba(107,0,255,0.2); }
.page-title { color: var(--cyber-green); font-size: 20px; margin-bottom: 6px; }
.page-desc { color: var(--cyber-text-muted); font-size: 13px; }
.tools-container { display: grid; grid-template-columns: repeat(2,1fr); gap: 20px; padding: 20px; }
.tool-card h3 { color: var(--cyber-green); margin-bottom: 8px; font-size: 18px; }
.tool-desc { color: var(--cyber-text-muted); font-size: 13px; margin-bottom: 16px; }
.tool-form { display: flex; gap: 12px; }
.tool-result { margin-top: 16px; padding: 16px; background: #0a0a1a; border-radius: 4px; border: 1px solid rgba(0,255,136,0.2); color: var(--cyber-green); font-family: 'Consolas',monospace; }
</style>
```

- [ ] **Step 6: Commit所有页面**

```bash
git add frontend/src/views/
git commit -m "feat: 创建所有功能页面(记忆/RAG/Agent/结构化/工具)"
```

---

## 第七阶段：测试与收尾

### Task 21: 删除旧的静态页面并更新后端配置

**Files:**
- Delete: `src/main/resources/static/index.html`
- Verify: all controllers work

- [ ] **Step 1: 删除旧的静态index.html（前端分离后不需要了）**

删除 `src/main/resources/static/index.html` 文件。

- [ ] **Step 2: 更新.gitignore添加前端和IDE忽略**

Create/Update `.gitignore` in project root:

```
# IDE
.idea/
*.iml
*.iws
*.ipr
.vscode/

# Maven
target/

# Node
frontend/node_modules/
frontend/dist/

# Logs
*.log

# OS
.DS_Store
Thumbs.db

# Superpowers
.superpowers/
```

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git rm src/main/resources/static/index.html
git commit -m "chore: 删除旧静态页面，更新gitignore"
```

---

### Task 22: 验证后端编译

- [ ] **Step 1: 编译后端项目**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 启动后端（需要MySQL和Ollama运行中）**

先确保MySQL运行，数据库 `ai_learn` 已创建，然后运行：
```bash
mvn spring-boot:run
```
Expected: 应用在8080端口启动成功

- [ ] **Step 3: 测试API（另一个终端）**

Run: `curl http://localhost:8080/api/chat/send -X POST -H "Content-Type: application/json" -d "{\"message\":\"你好\"}"`
Expected: 返回JSON格式响应

---

### Task 23: 验证前端启动

- [ ] **Step 1: 启动前端dev server**

```bash
cd frontend
npm run dev
```
Expected: Vite dev server在 http://localhost:5173 启动

- [ ] **Step 2: 在浏览器访问并测试**

打开 http://localhost:5173 ，测试：
1. 基础聊天页面 - 发送消息是否流式响应
2. 记忆对话页面 - 新建会话后多轮对话
3. 其他页面是否正常渲染

- [ ] **Step 3: 最终Commit所有改动**

检查是否有遗漏文件，然后commit：
```bash
git add .
git commit -m "feat: 完成Spring AI赛博朋克前后端分离平台"
```

---

## 完成标准

- 后端Spring Boot成功启动，连接MySQL和Ollama
- 前端Vue项目成功启动在5173端口
- 所有页面都采用赛博朋克深色霓虹主题
- 基础聊天支持SSE流式响应
- 记忆对话使用MySQL持久化会话历史
- 所有AI功能（聊天/记忆/RAG/Agent/结构化/工具）可正常使用
- API统一返回Result格式
- CORS配置正确，前端可正常调用后端
