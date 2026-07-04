package com.ailearn.memory;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.dto.MemoryChatRequest;
import com.ailearn.entity.Conversation;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.ConversationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 带记忆的聊天服务类
 * 使用Spring AI的MessageChatMemoryAdvisor实现多轮对话记忆功能
 * AI能够记住当前会话中的历史对话内容，提供上下文连贯的对话体验
 * 通过DatabaseChatMemory将对话历史持久化到数据库中
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
@Validated
@RateLimiter(name = "chatService")
public class MemoryChatService {

    /**
     * 带记忆功能的Spring AI聊天客户端
     * 通过MessageChatMemoryAdvisor集成ChatMemory实现对话记忆
     */
    private final ChatClient chatClient;

    /**
     * 会话服务
     * 用于会话创建和管理
     */
    private final ConversationService conversationService;

    /**
     * 数据库聊天记忆实现
     * 用于从数据库读取和保存对话历史
     */
    private final DatabaseChatMemory chatMemory;

    /**
     * JSON对象映射器
     * 用于构建SSE流式响应的JSON数据
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造方法：构建带记忆功能的ChatClient
     * 通过defaultAdvisors添加MessageChatMemoryAdvisor，自动集成对话记忆功能
     *
     * @param builder             ChatClient构建器，由Spring AI自动注入
     * @param chatMemory          数据库聊天记忆实现
     * @param conversationService 会话服务
     */
    public MemoryChatService(ChatClient.Builder builder,
                              DatabaseChatMemory chatMemory,
                              ConversationService conversationService) {
        this.chatMemory = chatMemory;
        this.conversationService = conversationService;
        // 构建带记忆功能的ChatClient，设置系统提示词并添加记忆顾问
        this.chatClient = builder
                .defaultSystem("你是一个记忆力超强的AI助手，能够记住对话中的所有细节和上下文。" +
                        "请基于对话历史提供连贯、准确的回答，用简洁清晰的中文回复。")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        log.info("MemoryChatService初始化完成，带记忆的ChatClient已构建");
    }

    /**
     * 带记忆的同步对话方法
     * 处理完整的多轮对话流程：创建会话（如果需要）→ 调用AI（自动携带历史上下文）→ 返回结果
     * MessageChatMemoryAdvisor会自动通过ChatMemory加载和保存对话历史
     *
     * @param req  记忆聊天请求参数，包含消息内容和可选的会话ID
     * @param user 当前登录用户信息
     * @return Map&lt;String, Object&gt; 包含会话ID和AI回复的响应数据
     *         - conversationId: Long 会话ID
     *         - reply: String AI回复的文本内容（基于对话上下文）
     * @throws BusinessException 当AI调用失败时抛出CHAT_AI_CALL_FAILED异常
     */
    public Map<String, Object> chat(@Valid @NotNull(message = "请求参数不能为空") MemoryChatRequest req,
                                     @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("记忆对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        Long conversationId = req.getConversationId();
        String userMessage = req.getMessage();
        String conversationIdStr;

        // 步骤1：如果未指定会话ID，则创建新会话
        if (conversationId == null) {
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            Conversation conversation = conversationService.createConversation(title, "memory");
            conversationId = conversation.getId();
            log.info("记忆对话自动创建新会话: conversationId={}", conversationId);
        } else {
            // 验证会话存在
            conversationService.getConversationById(conversationId);
        }
        conversationIdStr = String.valueOf(conversationId);

        // 步骤2：调用带记忆的AI大模型获取回复
        // MessageChatMemoryAdvisor会自动：
        // - 从ChatMemory加载该会话的历史消息
        // - 将用户消息和AI回复保存到ChatMemory（最终持久化到数据库）
        String aiReply;
        try {
            aiReply = chatClient.prompt()
                    .user(userMessage)
                    .advisors(a -> a
                            .param(ChatMemory.CONVERSATION_ID, conversationIdStr))
                    .call()
                    .content();

            if (aiReply == null || aiReply.isEmpty()) {
                log.error("记忆对话AI回复为空: conversationId={}", conversationId);
                throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, "AI回复为空");
            }

            log.debug("记忆对话AI调用成功: conversationId={}, replyLength={}", conversationId, aiReply.length());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("记忆对话AI调用失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, e.getMessage());
        }

        // 步骤3：封装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversationId);
        result.put("reply", aiReply);

        log.info("记忆对话完成: userId={}, conversationId={}", user.getUserId(), conversationId);
        return result;
    }

    /**
     * 带记忆的流式对话方法（SSE服务器发送事件）
     * 支持多轮上下文的流式输出，AI会基于历史对话记忆生成连贯回复
     * MessageChatMemoryAdvisor在stream模式下也能正常工作，自动管理对话历史
     *
     * @param req  记忆聊天请求参数，包含消息内容和可选的会话ID
     * @param user 当前登录用户信息
     * @return Flux&lt;String&gt; 响应式SSE流，每个token都是标准SSE格式数据
     */
    public Flux<String> streamChat(@Valid @NotNull(message = "请求参数不能为空") MemoryChatRequest req,
                                    @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("记忆流式对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        Long conversationId = req.getConversationId();
        String userMessage = req.getMessage();
        String conversationIdStr;

        // 步骤1：如果未指定会话ID，则创建新会话
        if (conversationId == null) {
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            Conversation conversation = conversationService.createConversation(title, "memory");
            conversationId = conversation.getId();
            log.info("记忆流式对话自动创建新会话: conversationId={}", conversationId);
        } else {
            // 验证会话存在
            conversationService.getConversationById(conversationId);
        }
        conversationIdStr = String.valueOf(conversationId);

        // 使用final变量供lambda表达式使用
        final String finalConversationIdStr = conversationIdStr;
        final Long finalConversationId = conversationId;

        // 用于累积完整AI回复（用于日志记录）
        StringBuilder fullReply = new StringBuilder();

        // 步骤2：构建带记忆的SSE流式响应
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, finalConversationIdStr))
                .stream()
                .content()
                // 将每个token包装成SSE JSON格式
                .map(token -> {
                    fullReply.append(token);
                    return buildSseEvent("token", token);
                })
                // 流开始时发送会话ID信息
                .startWith(buildSseEvent("conversationId", String.valueOf(finalConversationId)))
                // 流完成时记录日志
                .doOnComplete(() -> {
                    log.info("记忆流式对话完成: userId={}, conversationId={}, replyLength={}",
                            user.getUserId(), finalConversationId, fullReply.length());
                    // 注意：MessageChatMemoryAdvisor会自动保存消息到ChatMemory，
                    // 不需要手动调用conversationService.saveMessage()
                })
                // 流发生错误时的处理
                .doOnError(e -> {
                    log.error("记忆流式对话发生错误: conversationId={}, error={}",
                            finalConversationId, e.getMessage(), e);
                })
                // 最后发送结束标记
                .concatWithValues(buildSseEvent("done", "[DONE]"))
                // 异常时发送错误事件
                .onErrorResume(e -> {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "AI调用失败";
                    return Flux.just(
                            buildSseEvent("error", errorMsg),
                            buildSseEvent("done", "[DONE]")
                    );
                });
    }

    /**
     * 清除指定会话的记忆
     * 清除数据库中该会话的所有聊天历史记录，AI将"忘记"之前的对话
     *
     * @param conversationId 会话ID，不能为空
     */
    public void clearMemory(@NotNull(message = "会话ID不能为空") Long conversationId) {
        log.info("清除会话记忆(Long): conversationId={}", conversationId);
        // 验证会话存在
        conversationService.getConversationById(conversationId);
        // 清除ChatMemory中的历史记录
        chatMemory.clear(String.valueOf(conversationId));
        log.info("会话记忆已清除: conversationId={}", conversationId);
    }

    /**
     * 清除指定会话的记忆（String参数版本，兼容旧代码）
     *
     * @param conversationId 会话ID字符串
     */
    public void clearMemory(String conversationId) {
        log.info("清除会话记忆(String): conversationId={}", conversationId);
        try {
            Long convId = Long.parseLong(conversationId);
            clearMemory(convId);
        } catch (NumberFormatException e) {
            log.warn("会话ID格式错误，直接清除ChatMemory: {}", conversationId);
            chatMemory.clear(conversationId);
        }
    }

    /**
     * 带记忆的简单对话（内部使用，兼容旧代码）
     * 使用conversationId作为字符串参数，不涉及会话创建逻辑
     *
     * @param conversationId 会话ID字符串
     * @param userMessage    用户消息
     * @return String AI回复
     */
    public String chat(String conversationId, String userMessage) {
        log.debug("简单记忆对话: conversationId={}, messageLength={}", conversationId, userMessage.length());
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    /**
     * 带记忆的简单流式对话（内部使用，兼容旧代码）
     *
     * @param conversationId 会话ID字符串
     * @param userMessage    用户消息
     * @return Flux&lt;String&gt; token流
     */
    public Flux<String> streamChat(String conversationId, String userMessage) {
        log.debug("简单记忆流式对话: conversationId={}, messageLength={}", conversationId, userMessage.length());
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    /**
     * 构建SSE事件字符串
     * 将事件类型和数据封装成标准SSE格式：data: {"type":"xxx","data":"yyy"}\n\n
     *
     * @param type 事件类型
     * @param data 事件数据
     * @return String SSE格式字符串
     */
    private String buildSseEvent(String type, String data) {
        Map<String, String> eventData = new HashMap<>();
        eventData.put("type", type);
        eventData.put("data", data);
        try {
            String json = objectMapper.writeValueAsString(eventData);
            return "data: " + json + "\n\n";
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败", e);
            return "data: {\"type\":\"error\",\"data\":\"序列化失败\"}\n\n";
        }
    }
}
