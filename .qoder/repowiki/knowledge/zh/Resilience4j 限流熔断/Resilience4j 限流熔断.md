---
kind: external_dependency
name: Resilience4j 限流熔断
slug: resilience4j
category: external_dependency
category_hints:
    - framework_behavior
scope:
    - '**'
---

### Resilience4j 限流熔断
- 通过 @RateLimiter 注解对 AgentService 和 SearchAgentService 进行调用频率限制，名称分别为 agentService 和 searchAgentService
- 限流规则需在 application.yml 中定义（rate-limiter.instances.*），未声明则使用默认配置
- 配合 spring-retry 与 Micrometer Tracing 形成可观测的弹性防护链