package com.ailearn.chat;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.dto.ChatRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 普通聊天服务类
 * 封装Spring AI ChatClient的核心调用，提供同步对话和流式对话功能
 * 无记忆功能，每次对话独立，不保留上下文
 * 使用Resilience4j限流保护，防止AI接口被恶意刷用
 *
 * ChatClient是Spring AI 1.0引入的高级API（类似RestClient风格），
 * 底层使用ChatModel，提供了更流畅的链式调用体验
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
@Validated
@RateLimiter(name = "chatService")
public class ChatService {

    /**
     * Spring AI聊天客户端
     * 由Spring AI AutoConfiguration自动注入，通过Builder构建
     * 用于调用AI大模型进行对话生成
     */
    private final ChatClient chatClient;

    /**
     * 会话服务
     * 用于会话创建、消息保存等操作
     */
    private final ConversationService conversationService;

    /**
     * JSON对象映射器
     * 用于构建SSE流式响应的JSON数据
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造方法：通过ChatClient.Builder构建默认ChatClient实例
     * 设置全局默认系统提示词，定义AI助手的角色和行为
     *
     * @param builder ChatClient构建器，由Spring AI自动注入
     * @param conversationService 会话服务
     */
    public ChatService(ChatClient.Builder builder, ConversationService conversationService) {
        this.conversationService = conversationService;
        // 构建默认ChatClient，设置全局系统提示词
        this.chatClient = builder
                .defaultSystem("你是一个专业、友好、有帮助的AI助手。请用简洁清晰的中文回答问题。" +
                        "回答要准确、有条理，避免编造虚假信息。如果不确定，请坦诚说明。")
                .build();
        log.info("ChatService初始化完成，ChatClient已构建");
    }

    /**
     * 同步普通对话方法
     * 处理完整的对话流程：创建会话（如果需要）→ 保存用户消息 → 调用AI获取回复 → 保存AI回复 → 返回结果
     *
     * @param req  聊天请求参数，包含消息内容和可选的会话ID，使用@Valid自动校验参数
     * @param user 当前登录用户信息，包含用户ID、用户名、角色等
     * @return Map&lt;String, Object&gt; 包含会话ID和AI回复的响应数据
     *         - conversationId: Long 会话ID（新创建或已存在的）
     *         - reply: String AI回复的文本内容
     * @throws BusinessException 当AI调用失败时抛出CHAT_AI_CALL_FAILED异常
     */
    public Map<String, Object> chat(@Valid @NotNull(message = "请求参数不能为空") ChatRequest req,
                                     @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("普通对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        Long conversationId = req.getConversationId();
        String userMessage = req.getMessage();

        // 步骤1：如果未指定会话ID，则创建新会话
        if (conversationId == null) {
            // 使用用户消息的前20个字符作为会话标题（便于识别）
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            Conversation conversation = conversationService.createConversation(title, "chat");
            conversationId = conversation.getId();
            log.info("自动创建新会话: conversationId={}, title={}", conversationId, title);
        } else {
            // 验证会话存在
            conversationService.getConversationById(conversationId);
        }

        // 步骤2：保存用户消息到数据库
        conversationService.saveMessage(conversationId, "user", userMessage);
        log.debug("用户消息已保存: conversationId={}", conversationId);

        // 步骤3：调用AI大模型获取回复
        String aiReply;
        try {
            aiReply = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();

            if (aiReply == null || aiReply.isEmpty()) {
                log.error("AI回复为空: conversationId={}", conversationId);
                throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, "AI回复为空");
            }

            log.debug("AI调用成功: conversationId={}, replyLength={}", conversationId, aiReply.length());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI调用失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, e.getMessage());
        }

        // 步骤4：保存AI回复到数据库
        conversationService.saveMessage(conversationId, "assistant", aiReply);
        log.debug("AI回复已保存: conversationId={}", conversationId);

        // 步骤5：封装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversationId);
        result.put("reply", aiReply);

        log.info("普通对话完成: userId={}, conversationId={}", user.getUserId(), conversationId);
        return result;
    }

    /**
     * 流式对话方法（SSE服务器发送事件）
     * 处理流式对话流程：创建会话（如果需要）→ 保存用户消息 → 流式调用AI → 逐token输出 → 完成时保存完整回复
     * 输出格式为SSE标准格式：每个token发送 "data: {json}\n\n"，最后发送 "[DONE]" 事件
     *
     * @param req  聊天请求参数，包含消息内容和可选的会话ID
     * @param user 当前登录用户信息
     * @return Flux&lt;String&gt; 响应式流，每个元素是一个SSE格式的数据片段
     */
    public Flux<String> streamChat(@Valid @NotNull(message = "请求参数不能为空") ChatRequest req,
                                    @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("流式对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        Long conversationId = req.getConversationId();
        String userMessage = req.getMessage();

        // 步骤1：如果未指定会话ID，则创建新会话
        if (conversationId == null) {
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            Conversation conversation = conversationService.createConversation(title, "chat");
            conversationId = conversation.getId();
            log.info("流式对话自动创建新会话: conversationId={}", conversationId);
        } else {
            // 验证会话存在
            conversationService.getConversationById(conversationId);
        }

        // 步骤2：保存用户消息（流式对话先保存用户消息，再开始流式输出）
        final Long finalConversationId = conversationId;
        conversationService.saveMessage(finalConversationId, "user", userMessage);

        // 步骤3：用于累积完整AI回复的StringBuilder
        StringBuilder fullReply = new StringBuilder();

        // 步骤4：构建SSE流式响应
        return chatClient.prompt()
                .user(userMessage)
                .stream()
                .content()
                // 将每个token包装成SSE JSON格式
                .map(token -> {
                    fullReply.append(token);
                    return buildSseEvent("token", token);
                })
                // 流开始时发送会话ID信息
                .startWith(buildSseEvent("conversationId", String.valueOf(finalConversationId)))
                // 流完成时的处理
                .doOnComplete(() -> {
                    // 保存完整的AI回复到数据库
                    String aiReply = fullReply.toString();
                    if (!aiReply.isEmpty()) {
                        conversationService.saveMessage(finalConversationId, "assistant", aiReply);
                        log.info("流式对话完成: userId={}, conversationId={}, replyLength={}",
                                user.getUserId(), finalConversationId, aiReply.length());
                    }
                })
                // 流发生错误时的处理
                .doOnError(e -> {
                    log.error("流式对话发生错误: conversationId={}, error={}", finalConversationId, e.getMessage(), e);
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
     * 构建SSE事件字符串
     * 将事件类型和数据封装成标准SSE格式：data: {"type":"xxx","data":"yyy"}\n\n
     *
     * @param type 事件类型，如：token、conversationId、done、error
     * @param data 事件数据内容
     * @return String 格式化后的SSE事件字符串
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

    /**
     * 同步单次对话（内部使用，兼容旧代码）
     * 简单的消息发送和回复获取，不涉及会话管理和消息保存
     *
     * @param userMessage 用户消息文本
     * @return String AI回复文本
     */
    public String chat(String userMessage) {
        log.debug("简单对话: messageLength={}", userMessage.length());
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }

    /**
     * 流式对话（内部使用，兼容旧代码）
     * 简单的流式消息发送，不涉及会话管理和消息保存
     *
     * @param userMessage 用户消息文本
     * @return Flux&lt;String&gt; token流
     */
    public Flux<String> streamChat(String userMessage) {
        log.debug("简单流式对话: messageLength={}", userMessage.length());
        return chatClient.prompt()
                .user(userMessage)
                .stream()
                .content();
    }

    /**
     * 动态设置System Prompt进行对话
     * 覆盖默认系统提示词，实现角色扮演或场景定制
     *
     * @param userMessage  用户消息
     * @param systemPrompt 自定义系统提示词
     * @return String AI回复
     */
    public String chatWithSystem(String userMessage, String systemPrompt) {
        log.debug("带System Prompt对话: systemPromptLength={}, messageLength={}",
                systemPrompt.length(), userMessage.length());
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }
}
