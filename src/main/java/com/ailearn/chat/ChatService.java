package com.ailearn.chat;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.dto.ChatRequest;
import com.ailearn.entity.Conversation;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.ConversationService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@Validated
@RateLimiter(name = "chatService")
public class ChatService {

    private final ChatClient chatClient;

    private final ConversationService conversationService;

    public ChatService(ChatModel chatModel, ConversationService conversationService) {
        this.conversationService = conversationService;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你是一个专业、友好、有帮助的AI助手。请用简洁清晰的中文回答问题。" +
                        "回答要准确、有条理，避免编造虚假信息。如果不确定，请坦诚说明。")
                .build();
        log.info("ChatService初始化完成，ChatClient已构建");
    }

    public Map<String, Object> chat(@Valid @NotNull(message = "请求参数不能为空") ChatRequest req,
                                     @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("普通对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        Long userId = user.getUserId();
        Long conversationId = ensureConversation(userId, req.getConversationId(), req.getMessage(), "chat");
        conversationService.saveMessage(userId, conversationId, "user", req.getMessage());

        String aiReply;
        try {
            aiReply = chatClient.prompt()
                    .user(req.getMessage())
                    .call()
                    .content();
            if (aiReply == null || aiReply.isEmpty()) {
                throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, "AI回复为空");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI调用失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, e.getMessage());
        }

        conversationService.saveMessage(userId, conversationId, "assistant", aiReply);

        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversationId);
        result.put("reply", aiReply);
        log.info("普通对话完成: userId={}, conversationId={}", user.getUserId(), conversationId);
        return result;
    }

    public Flux<String> streamChat(@Valid @NotNull(message = "请求参数不能为空") ChatRequest req,
                                    @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("流式对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        Long userId = user.getUserId();
        Long conversationId = ensureConversation(userId, req.getConversationId(), req.getMessage(), "chat");
        conversationService.saveMessage(userId, conversationId, "user", req.getMessage());
        final Long finalConversationId = conversationId;
        final Long finalUserId = userId;

        StringBuilder fullReply = new StringBuilder();

        return chatClient.prompt()
                    .user(req.getMessage())
                    .stream()
                    .content()
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        fullReply.append(chunk);
                    }
                })
                .doOnComplete(() -> {
                    String aiReply = fullReply.toString();
                    if (!aiReply.isEmpty()) {
                        conversationService.saveMessage(finalUserId, finalConversationId, "assistant", aiReply);
                        log.info("流式对话完成: userId={}, conversationId={}, replyLength={}",
                                user.getUserId(), finalConversationId, aiReply.length());
                    }
                })
                .doOnError(e -> log.error("流式对话错误: conversationId={}, error={}",
                        finalConversationId, e.getMessage(), e))
                .onErrorResume(e -> Flux.just("[ERROR] " + (e.getMessage() != null ? e.getMessage() : "AI调用失败")));
    }

    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }

    public Flux<String> streamChat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .stream()
                .content();
    }

    public String chatWithSystem(String userMessage, String systemPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    private Long ensureConversation(Long userId, Long conversationId, String userMessage, String type) {
        if (conversationId == null) {
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            Conversation conversation = conversationService.createConversation(userId, title, type);
            log.info("自动创建新会话: conversationId={}, userId={}, title={}", conversation.getId(), userId, title);
            return conversation.getId();
        }
        conversationService.getConversationById(userId, conversationId);
        return conversationId;
    }
}
