package com.ailearn.config;

import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.ratelimiter.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j限流配置类
 * 基于Resilience4j实现接口限流保护，防止AI接口被恶意调用或突发流量压垮模型服务。
 *
 * 限流策略说明（配置在application.yml中）：
 * - chatService：普通聊天接口，限制10次/10秒（每秒最多1次，允许短暂突发）
 * - agentService：Agent智能体接口，限制3次/30秒（Agent推理消耗大量Token，限制更严格）
 * - ragService：RAG知识库接口，限制5次/60秒（涉及向量检索+文档处理，资源消耗较大）
 *
 * 注意：限流的具体阈值参数配置在application.yml的resilience4j.ratelimiter节点下，
 * 由Resilience4j自动配置，本配置类仅负责注册事件监听器用于监控和日志记录。
 *
 * 使用方式：在Service层方法上使用 @RateLimiter(name = "chatService") 注解即可启用限流。
 * 限流触发时会抛出 RequestNotPermitted 异常，由GlobalExceptionHandler统一处理返回429状态码。
 *
 * @author AiLearn Platform
 */
@Slf4j
@Configuration
public class RateLimiterConfig {

    /**
     * 限流实例名称常量：聊天服务
     * 对应application.yml中 resilience4j.ratelimiter.instances.chatService 配置
     */
    public static final String CHAT_SERVICE = "chatService";

    /**
     * 限流实例名称常量：Agent智能体服务
     * 对应application.yml中 resilience4j.ratelimiter.instances.agentService 配置
     */
    public static final String AGENT_SERVICE = "agentService";

    /**
     * 限流实例名称常量：RAG知识库服务
     * 对应application.yml中 resilience4j.ratelimiter.instances.ragService 配置
     */
    public static final String RAG_SERVICE = "ragService";

    /**
     * 配置RateLimiter注册表事件消费者
     * 监听限流注册事件和限流触发事件，用于日志记录和监控告警。
     * 当新的RateLimiter被创建、移除或替换时，会记录相关日志信息。
     *
     * Resilience4j 2.x的事件API说明：
     * - onEntryAddedEvent：新条目添加时触发，通过event.getAddedEntry()获取限流器实例
     * - onEntryRemovedEvent：条目移除时触发，通过event.getRemovedEntry()获取限流器实例
     * - onEntryReplacedEvent：条目替换时触发，通过event.getOldEntry()/getNewEntry()获取旧/新实例
     *
     * @return RegistryEventConsumer<RateLimiter> 限流器事件消费者
     */
    @Bean
    public RegistryEventConsumer<RateLimiter> rateLimiterEventConsumer() {
        return new RegistryEventConsumer<RateLimiter>() {

            /**
             * 当新的RateLimiter实例被创建并添加到注册表时调用
             * 用于记录限流器初始化信息，便于排查配置是否正确加载
             *
             * @param event 条目添加事件对象，包含被添加的限流器实例
             */
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<RateLimiter> event) {
                // 从事件中获取新添加的限流器实例
                RateLimiter rateLimiter = event.getAddedEntry();
                // 获取限流器名称
                String name = rateLimiter.getName();
                // 获取限流器配置（使用全限定名避免与本类名冲突）
                io.github.resilience4j.ratelimiter.RateLimiterConfig config = rateLimiter.getRateLimiterConfig();
                // 记录限流器初始化日志，包含关键配置参数
                log.info("限流器[{}]已注册 - 周期限制次数:{}, 刷新周期:{}, 等待超时:{}",
                        name,
                        config.getLimitForPeriod(),
                        config.getLimitRefreshPeriod(),
                        config.getTimeoutDuration());
            }

            /**
             * 当RateLimiter实例从注册表中移除时调用
             *
             * @param event 条目移除事件对象，包含被移除的限流器实例
             */
            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<RateLimiter> event) {
                // 从事件中获取被移除的限流器实例
                RateLimiter rateLimiter = event.getRemovedEntry();
                // 记录限流器移除日志
                log.info("限流器[{}]已移除", rateLimiter.getName());
            }

            /**
             * 当RateLimiter配置被替换时调用（例如运行时动态更新限流规则）
             *
             * @param event 条目替换事件对象，包含旧实例和新实例
             */
            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<RateLimiter> event) {
                // 从事件中获取新的限流器实例
                RateLimiter newEntry = event.getNewEntry();
                // 记录限流器配置更新日志
                log.info("限流器[{}]配置已更新", newEntry.getName());
            }
        };
    }
}
