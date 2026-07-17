---
kind: external_dependency
name: PostgreSQL + PgVector 向量数据库
slug: postgresql-pgvector
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### PostgreSQL + PgVector 向量数据库
- 作为 RAG 知识库的向量存储后端，通过 spring-ai-starter-vector-store-pgvector 提供 VectorStore 实现
- 文档经 TokenTextSplitter 切分后以余弦相似度检索（topK=5, threshold=0.7），结果按相关性排序拼接为上下文注入 Prompt