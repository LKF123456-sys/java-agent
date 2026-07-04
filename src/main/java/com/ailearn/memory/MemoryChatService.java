package com.ailearn.memory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.stereotype.Service;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

/**
 * 多轮对话 Service
 *
 * Spring AI 核心概念 - Advisor（顾问）模式：
 * Advisor 是 Spring AI 的 AOP 机制，在请求/响应流程中插入横切关注点。
 * 常用 Advisor：
 * - MessageChatMemoryAdvisor：自动注入历史消息
 * - QuestionAnswerAdvisor：RAG 检索增强
 * - SafeGuardAdvisor：内容安全过滤
 */
@Service
public class MemoryChatService {

    private final ChatClient chatClient;
    // InMemoryChatMemory 是内存存储，重启后消失
    // 生产环境可替换为 CassandraChatMemory / RedisChatMemory 等持久化实现
    private final InMemoryChatMemory chatMemory = new InMemoryChatMemory();

    public MemoryChatService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("你是一个记忆力超强的AI助手，能记住对话中的所有细节。")
                // 注册 MessageChatMemoryAdvisor，自动处理历史消息的注入
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
    }

    public String chat(String conversationId, String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                // 通过 advisor 的 param 方法传入会话ID和历史消息数量上限
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 20))

                .call()
                .content();
    }

    public void clearMemory(String conversationId) {
        chatMemory.clear(conversationId);
    }
}