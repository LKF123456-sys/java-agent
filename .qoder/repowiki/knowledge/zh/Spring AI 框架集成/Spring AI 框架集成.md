---
kind: external_dependency
name: Spring AI 框架集成
slug: spring-ai
category: external_dependency
category_hints:
    - framework_behavior
    - sdk_real_api
scope:
    - '**'
---

### Spring AI 框架集成
- 已启用 Ollama 本地模型、PgVector 向量存储、PDF/Tika 文档读取器；RAG 与多 Agent 均基于此统一抽象层
- 流式响应通过 Flux<String> 暴露，SSE 事件由上层自行封装 JSON 事件格式推送前端