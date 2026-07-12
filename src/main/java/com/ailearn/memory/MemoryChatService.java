package com.ailearn.memory;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.dto.MemoryChatRequest;
import com.ailearn.entity.Conversation;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.ConversationService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@Validated
@RateLimiter(name = "memoryChatService")
public class MemoryChatService {

    private final ChatClient chatClient;

    private final ConversationService conversationService;

    private final DatabaseChatMemory chatMemory;

    public MemoryChatService(ChatModel chatModel,
                              DatabaseChatMemory chatMemory,
                              ConversationService conversationService) {
        this.chatMemory = chatMemory;
        this.conversationService = conversationService;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你是一个记忆力超强的AI助手，能够记住对话中的所有细节和上下文。" +
                        "请基于对话历史提供连贯、准确的回答，用简洁清晰的中文回复。")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        log.info("MemoryChatService初始化完成，带记忆的ChatClient已构建");
    }

    public Map<String, Object> chat(@Valid @NotNull(message = "请求参数不能为空") MemoryChatRequest req,
                                     @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("记忆对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        Long userId = user.getUserId();
        Long conversationId = ensureConversation(userId, req.getConversationId(), req.getMessage(), "memory");
        String convIdStr = String.valueOf(conversationId);

        String aiReply;
        try {
            aiReply = chatClient.prompt()
                    .user(req.getMessage())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convIdStr))
                    .call()
                    .content();
            if (aiReply == null || aiReply.isEmpty()) {
                throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, "AI回复为空");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("记忆对话AI调用失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversationId);
        result.put("reply", aiReply);
        log.info("记忆对话完成: userId={}, conversationId={}", user.getUserId(), conversationId);
        return result;
    }

    public Flux<String> streamChat(@Valid @NotNull(message = "请求参数不能为空") MemoryChatRequest req,
                                    @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("记忆流式对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        Long userId = user.getUserId();
        Long conversationId = ensureConversation(userId, req.getConversationId(), req.getMessage(), "memory");
        String convIdStr = String.valueOf(conversationId);

        StringBuilder fullReply = new StringBuilder();

        return chatClient.prompt()
                .user(req.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convIdStr))
                .stream()
                .content()
                .doOnNext(fullReply::append)
                .doOnComplete(() -> {
                    log.info("记忆流式对话完成: userId={}, conversationId={}, replyLength={}",
                            user.getUserId(), conversationId, fullReply.length());
                })
                .doOnError(e -> log.error("记忆流式对话错误: conversationId={}, error={}",
                        conversationId, e.getMessage(), e))
                .onErrorResume(e -> Flux.just("[ERROR] " + (e.getMessage() != null ? e.getMessage() : "AI调用失败")));
    }

    public void clearMemory(@NotNull(message = "用户ID不能为空") Long userId,
                            @NotNull(message = "会话ID不能为空") Long conversationId) {
        conversationService.getConversationById(userId, conversationId);
        chatMemory.clear(String.valueOf(conversationId));
        log.info("会话记忆已清除: userId={}, conversationId={}", userId, conversationId);
    }

    public void clearMemory(String conversationId) {
        try {
            chatMemory.clear(conversationId);
        } catch (Exception e) {
            log.warn("清除记忆失败: conversationId={}", conversationId, e);
        }
    }

    public String chat(String conversationId, String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    public Flux<String> streamChat(String conversationId, String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    private Long ensureConversation(Long userId, Long conversationId, String userMessage, String type) {
        if (conversationId == null) {
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            Conversation conversation = conversationService.createConversation(userId, title, type);
            log.info("自动创建新记忆会话: conversationId={}, userId={}", conversation.getId(), userId);
            return conversation.getId();
        }
        conversationService.getConversationById(userId, conversationId);
        return conversationId;
    }
}
