---
kind: external_dependency
name: Tavily 联网搜索 API
slug: tavily-search-api
category: external_dependency
category_hints:
    - vendor_identity
    - auth_protocol
scope:
    - '**'
---

### Tavily 联网搜索 API
- 认证方式：在请求体中以 api_key 字段传递密钥（非 Authorization Header），密钥从 ${tavily.api-key} 注入
- 返回 JSON 含 answer 摘要与 results 列表（title/url/content），被格式化后注入到 SearchAgent 的用户提示词中
- 失败时降级为纯 LLM 回答，不影响主流程