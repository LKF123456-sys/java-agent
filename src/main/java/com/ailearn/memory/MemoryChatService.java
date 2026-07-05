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

/**
 * 带记忆的聊天服务类
 * 提供基于Spring AI ChatClient的多轮对话功能，通过DatabaseChatMemory实现对话历史持久化
 * 支持同步和流式（SSE）两种对话模式，自动管理会话创建和上下文记忆
 * 集成Resilience4j限流保护，防止API滥用
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
@Validated
@RateLimiter(name = "memoryChatService")
public class MemoryChatService {

    /**
     * Spring AI聊天客户端
     * 构建时配置了默认系统提示词和记忆顾问，用于执行AI对话调用
     */
    private final ChatClient chatClient;

    /**
     * 会话服务
     * 用于会话的创建、查询、验证等业务操作
     */
    private final ConversationService conversationService;

    /**
     * 数据库聊天记忆实现
     * 用于对话历史的持久化存储和读取，实现多轮对话上下文保持
     */
    private final DatabaseChatMemory chatMemory;

    /**
     * 构造函数，初始化带记忆的ChatClient
     * 配置系统提示词和MessageChatMemoryAdvisor，将DatabaseChatMemory注入到ChatClient中
     *
     * @param chatModel           Spring AI聊天模型，由Spring自动注入（如OpenAI、Ollama等）
     * @param chatMemory          数据库持久化聊天记忆实现
     * @param conversationService 会话管理服务
     */
    public MemoryChatService(ChatModel chatModel,
                              DatabaseChatMemory chatMemory,
                              ConversationService conversationService) {
        this.chatMemory = chatMemory;
        this.conversationService = conversationService;
        this.chatClient = ChatClient.builder(chatModel)
                // 配置默认系统提示词，定义AI助手的行为和风格
                .defaultSystem("你是一个记忆力超强的AI助手，能够记住对话中的所有细节和上下文。" +
                        "请基于对话历史提供连贯、准确的回答，用简洁清晰的中文回复。")
                // 配置记忆顾问，自动管理对话历史的加载和保存
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        log.info("MemoryChatService初始化完成，带记忆的ChatClient已构建");
    }

    /**
     * 同步聊天对话方法
     * 发送用户消息并等待AI完整回复，返回包含会话ID和回复内容的结果
     * 如果conversationId为空会自动创建新会话
     *
     * @param req  聊天请求参数，包含会话ID和用户消息内容
     * @param user 当前登录用户信息
     * @return Map&lt;String, Object&gt; 对话结果
     *         - conversationId: Long 会话ID
     *         - reply: String AI回复内容
     * @throws BusinessException 当AI调用失败或回复为空时抛出CHAT_AI_CALL_FAILED异常
     */
    public Map<String, Object> chat(@Valid @NotNull(message = "请求参数不能为空") MemoryChatRequest req,
                                     @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("记忆对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        // 确保会话存在，不存在则自动创建
        Long conversationId = ensureConversation(req.getConversationId(), req.getMessage(), "memory");
        String convIdStr = String.valueOf(conversationId);

        String aiReply;
        try {
            // 调用ChatClient执行同步对话，传入会话ID用于记忆管理
            aiReply = chatClient.prompt()
                    .user(req.getMessage())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convIdStr))
                    .call()
                    .content();
            // 校验AI回复有效性
            if (aiReply == null || aiReply.isEmpty()) {
                throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, "AI回复为空");
            }
        } catch (BusinessException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            // 其他异常（如网络错误、模型调用失败）包装为业务异常
            log.error("记忆对话AI调用失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, e.getMessage());
        }

        // 封装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversationId);
        result.put("reply", aiReply);
        log.info("记忆对话完成: userId={}, conversationId={}", user.getUserId(), conversationId);
        return result;
    }

    /**
     * 流式聊天对话方法（SSE）
     * 以Server-Sent Events方式实时返回AI生成的内容，支持打字机效果
     * 如果conversationId为空会自动创建新会话
     *
     * @param req  聊天请求参数，包含会话ID和用户消息内容
     * @param user 当前登录用户信息
     * @return Flux&lt;String&gt; 响应式流，实时推送AI生成的文本片段
     */
    public Flux<String> streamChat(@Valid @NotNull(message = "请求参数不能为空") MemoryChatRequest req,
                                    @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("记忆流式对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        // 确保会话存在，不存在则自动创建
        Long conversationId = ensureConversation(req.getConversationId(), req.getMessage(), "memory");
        String convIdStr = String.valueOf(conversationId);

        // 用于收集完整回复内容，在完成时记录日志
        StringBuilder fullReply = new StringBuilder();

        return chatClient.prompt()
                .user(req.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convIdStr))
                .stream()
                .content()
                // 收集每个文本片段
                .doOnNext(fullReply::append)
                // 流完成时记录日志
                .doOnComplete(() -> {
                    log.info("记忆流式对话完成: userId={}, conversationId={}, replyLength={}",
                            user.getUserId(), conversationId, fullReply.length());
                })
                // 错误处理
                .doOnError(e -> log.error("记忆流式对话错误: conversationId={}, error={}",
                        conversationId, e.getMessage(), e))
                // 发生错误时返回错误提示给前端
                .onErrorResume(e -> Flux.just("[ERROR] " + (e.getMessage() != null ? e.getMessage() : "AI调用失败")));
    }

    /**
     * 清除指定会话的记忆
     * 验证会话存在后删除该会话的所有聊天历史记录
     *
     * @param conversationId 会话ID，不能为空
     */
    public void clearMemory(@NotNull(message = "会话ID不能为空") Long conversationId) {
        // 先验证会话存在
        conversationService.getConversationById(conversationId);
        // 清除记忆
        chatMemory.clear(String.valueOf(conversationId));
        log.info("会话记忆已清除: conversationId={}", conversationId);
    }

    /**
     * 清除指定会话的记忆（字符串ID重载）
     * 支持字符串格式的会话ID，尝试解析为Long类型，失败则直接使用字符串
     *
     * @param conversationId 会话ID字符串
     */
    public void clearMemory(String conversationId) {
        try {
            // 尝试解析为Long类型调用主方法
            clearMemory(Long.parseLong(conversationId));
        } catch (NumberFormatException e) {
            // 解析失败时直接使用字符串清除（兼容非数字ID场景）
            chatMemory.clear(conversationId);
        }
    }

    /**
     * 简化版同步聊天方法
     * 不经过用户验证和DTO包装，直接使用会话ID和消息进行对话
     * 用于内部调用或测试场景
     *
     * @param conversationId 会话ID字符串
     * @param userMessage    用户消息内容
     * @return String AI回复内容
     */
    public String chat(String conversationId, String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    /**
     * 简化版流式聊天方法
     * 不经过用户验证和DTO包装，直接使用会话ID和消息进行流式对话
     * 用于内部调用或测试场景
     *
     * @param conversationId 会话ID字符串
     * @param userMessage    用户消息内容
     * @return Flux&lt;String&gt; 响应式流，实时推送AI生成的文本片段
     */
    public Flux<String> streamChat(String conversationId, String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    /**
     * 确保会话存在的内部辅助方法
     * 如果传入的conversationId为null，则自动创建新会话（使用用户消息前20个字符作为标题）；
     * 如果conversationId不为null，则验证会话是否存在
     *
     * @param conversationId 会话ID，为null时表示需要新建会话
     * @param userMessage    用户消息，用于生成新会话标题
     * @param type           会话类型（memory/chat/agent/rag等）
     * @return Long 有效的会话ID（新建的或已存在的）
     */
    private Long ensureConversation(Long conversationId, String userMessage, String type) {
        if (conversationId == null) {
            // 会话ID为空，自动创建新会话，标题取消息前20个字符
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            Conversation conversation = conversationService.createConversation(title, type);
            log.info("自动创建新记忆会话: conversationId={}", conversation.getId());
            return conversation.getId();
        }
        // 验证已存在的会话
        conversationService.getConversationById(conversationId);
        return conversationId;
    }
}
