---
kind: logging_system
name: 基于Logback+MDC的分布式链路追踪日志系统
category: logging_system
scope:
    - '**'
source_files:
    - src/main/resources/logback-spring.xml
    - src/main/java/com/ailearn/config/MdcTraceFilter.java
    - docker/logstash/pipeline/logstash.conf
    - docker/logstash/config/logstash.yml
---

## 日志系统架构

该项目采用 **Logback + SLF4J + Lombok** 作为后端日志框架，结合 **MDC（Mapped Diagnostic Context）** 实现分布式链路追踪，并通过 **Logstash + Elasticsearch** 构建完整的ELK日志采集与分析体系。

### 核心组件

**1. 日志框架层**
- **SLF4J接口**：统一日志抽象
- **Logback实现**：Spring Boot默认日志实现
- **Lombok注解**：`@Slf4j`自动生成logger实例
- **LogstashEncoder**：JSON格式输出，便于结构化解析

**2. 链路追踪机制**
- **MdcTraceFilter**：自定义过滤器，为每个HTTP请求生成traceId/spanId
- **MDC上下文**：存储traceId、spanId、userId、requestPath等关键信息
- **自动传播**：支持从请求头X-Trace-Id接收上游服务传递的追踪ID

**3. 多环境配置**
- **dev环境**：控制台彩色日志 + 普通文件日志 + 错误日志分离
- **docker环境**：控制台日志 + JSON格式日志（供ELK采集）

### 日志输出策略

**文件滚动策略**：
- 按天滚动：`app.%d{yyyy-MM-dd}.%i.log`
- 单文件大小限制：100MB
- 总大小上限：10GB
- 保留天数：30天

**日志级别控制**：
- 根级别：INFO
- 错误日志单独输出到error.log
- 可通过`logging.level.*`配置覆盖特定包级别

### ELK集成方案

**Logstash管道配置**：
- Input：监听`/app/logs/app-json-*.log`文件
- Codec：JSON格式直接解析
- Filter：时间戳处理、字段重命名避免冲突
- Output：输出到Elasticsearch，索引按日期分割`cyber-ai-logs-YYYY.MM.dd`

### 开发者规范

**日志使用约定**：
- 使用`@Slf4j`注解注入logger
- 参数化日志：`log.info("用户登录: userId={}", userId)`
- 异常日志：`log.error("操作失败: {}", message, e)`
- 避免在日志中输出敏感信息

**MDC字段规范**：
- traceId：16位UUID，无横线分隔
- spanId：当前服务内的请求跨度标识
- userId：当前登录用户ID
- requestPath/requestMethod：HTTP请求元数据

**日志级别使用**：
- DEBUG：详细的调试信息
- INFO：业务关键流程节点
- WARN：可恢复的异常情况
- ERROR：需要关注的错误信息

### 配置文件位置
- `src/main/resources/logback-spring.xml`：主日志配置
- `docker/logstash/pipeline/logstash.conf`：日志采集管道
- `docker/logstash/config/logstash.yml`：Logstash全局配置