---
kind: error_handling
name: 统一错误处理体系：基于Spring Boot的全局异常与业务错误码规范
category: error_handling
scope:
    - '**'
source_files:
    - src/main/java/com/ailearn/common/GlobalExceptionHandler.java
    - src/main/java/com/ailearn/common/BusinessException.java
    - src/main/java/com/ailearn/common/ErrorCode.java
    - src/main/java/com/ailearn/common/Result.java
    - src/test/java/com/ailearn/common/GlobalExceptionHandlerTest.java
---

## 错误处理架构概述

Cyber AI平台采用Spring Boot框架的`@RestControllerAdvice`全局异常处理机制，结合自定义业务异常类和统一的错误码枚举，构建了完整的错误处理体系。该体系贯穿Controller层、Service层和基础设施层，确保所有API响应格式一致且错误信息可追踪。

## 核心组件设计

### 1. 统一响应包装类（Result）
- **位置**: `com.ailearn.common.Result`
- **功能**: 封装所有REST API的返回结果，包含code、message、data、timestamp、traceId字段
- **特点**: 提供静态工厂方法`success()`和多种`error()`重载，自动注入链路追踪ID

### 2. 业务异常类（BusinessException）
- **位置**: `com.ailearn.common.BusinessException`
- **继承**: `RuntimeException`，支持链式异常包装
- **特性**: 包含ErrorCode枚举引用和可选的detail详细信息，提供`of()`静态工厂方法

### 3. 错误码枚举（ErrorCode）
- **位置**: `com.ailearn.common.ErrorCode`
- **分类体系**:
  - 1xxx: 认证授权相关 (AUTH)
  - 2xxx: 用户相关 (USER) 
  - 3xxx: 聊天相关 (CHAT)
  - 4xxx: Agent相关 (AGENT)
  - 5xxx: RAG知识库相关 (RAG)
  - 6xxx: MCP协议相关 (MCP)
  - 9xxx: 系统级错误 (SYSTEM)

### 4. 全局异常处理器（GlobalExceptionHandler）
- **位置**: `com.ailearn.common.GlobalExceptionHandler`
- **职责**: 使用`@RestControllerAdvice`拦截所有Controller异常，统一转换为Result格式
- **处理策略**:
  - 业务异常 → HTTP 200 + 业务错误码
  - 参数校验异常 → HTTP 400 + 参数错误码
  - 认证异常 → HTTP 401 + 认证错误码
  - 权限不足 → HTTP 403 + 权限错误码
  - 未知异常 → HTTP 500 + 系统内部错误码

## 异常处理流程

### 正常业务流程
```java
// Service层抛出业务异常
throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户ID: 999");

// GlobalExceptionHandler捕获并转换
return Result.error(ErrorCode.USER_NOT_FOUND.getCode(), "用户不存在: 用户ID: 999");
```

### 参数校验流程
```java
// Controller方法使用@Valid注解
public Result<?> chat(@Valid @RequestBody ChatRequest request)

// 校验失败时抛出MethodArgumentNotValidException
// GlobalExceptionHandler收集所有字段错误并拼接为分号分隔的消息
```

### 第三方服务调用异常
```java
try {
    // 调用AI模型或外部服务
} catch (BusinessException e) {
    throw e; // 保持原始业务异常
} catch (Exception e) {
    log.error("调用失败", e);
    throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, e.getMessage());
}
```

## 流式响应错误处理

对于SSE流式响应，采用响应式编程的错误处理模式：
```java
Flux<String> stream = chatClient.prompt().user(message).stream().content();
return stream
    .doOnError(e -> log.error("流式对话错误", e))
    .onErrorResume(e -> Flux.just("[ERROR] " + e.getMessage()));
```

## 开发者规范

### 1. 异常抛出规范
- **业务异常**: 使用`BusinessException.of(ErrorCode.XXX)`或构造函数
- **参数验证**: 在DTO字段上使用`@NotNull`、`@Size`等JSR-303注解
- **方法级验证**: 在Service类上添加`@Validated`注解

### 2. 错误码使用规范
- 优先使用预定义的ErrorCode枚举值
- 需要额外上下文时使用`BusinessException(ErrorCode, detail)`构造器
- 避免直接抛出运行时异常，应包装为业务异常

### 3. 日志记录规范
- 业务异常使用`log.warn()`级别
- 系统异常使用`log.error()`级别并记录完整堆栈
- 关键操作前后记录INFO级别日志

### 4. 测试覆盖要求
- 每个新的ErrorCode都需要相应的单元测试
- 使用MockMvc测试各种异常场景的HTTP状态码和响应格式
- 验证错误消息的可读性和完整性

## 技术特色

1. **类型安全**: 通过枚举保证错误码的唯一性和完整性
2. **可扩展性**: 新增业务模块只需扩展ErrorCode枚举
3. **可追踪性**: 所有响应自动包含traceId便于问题定位
4. **用户体验**: 前端可根据不同错误码实现差异化提示
5. **安全性**: 系统内部异常不暴露具体错误细节给客户端