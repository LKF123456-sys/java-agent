---
kind: external_dependency
name: Ollama 本地大模型服务
slug: ollama
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### Ollama 本地大模型服务
- 作为默认 LLM 后端，通过 spring-ai-starter-model-ollama 自动装配 ChatModel Bean，无需显式配置 API Key
- 运行期需确保本机或网络可达的 Ollama 服务已启动并加载对应模型（如 qwen2.5:7b）
- 所有 Agent/RAG 对话最终都经由该 ChatModel 实例发起请求，切换其他模型供应商需替换 starter 依赖