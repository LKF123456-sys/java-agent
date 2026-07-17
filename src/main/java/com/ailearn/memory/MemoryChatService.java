package com.ailearn.memory;

// 导入业务异常类
import com.ailearn.common.BusinessException;
// 导入错误码枚举
import com.ailearn.common.ErrorCode;
// 导入记忆对话请求DTO
import com.ailearn.dto.MemoryChatRequest;
// 导入会话实体类
import com.ailearn.entity.Conversation;
// 导入用户安全主体
import com.ailearn.security.UserPrincipal;
// 导入会话管理服务
import com.ailearn.service.ConversationService;
// 导入Resilience4j限流器注解
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
// 导入Jakarta参数校验注解
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
// 导入Lombok注解
import lombok.extern.slf4j.Slf4j;
// 导入Spring AI ChatClient
import org.springframework.ai.chat.client.ChatClient;
// 导入消息记忆顾问
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
// 导入ChatMemory接口
import org.springframework.ai.chat.memory.ChatMemory;
// 导入ChatModel接口
import org.springframework.ai.chat.model.ChatModel;
// 导入Spring Service注解
import org.springframework.stereotype.Service;
// 导入参数校验注解
import org.springframework.validation.annotation.Validated;
// 导入Reactor Flux响应式流
import reactor.core.publisher.Flux;

// 导入HashMap
import java.util.HashMap;
// 导入Map
import java.util.Map;

/**
 * 带持久化记忆的多轮对话服务
 * 基于数据库聊天记忆实现多轮对话，AI能够记住对话历史中的所有细节和上下文。
 * 支持同步对话和SSE流式对话两种模式。
 *
 * <p>核心特性：
 * <ul>
 *   <li>长期记忆：基于DatabaseChatMemory持久化对话历史</li>
 *   <li>上下文感知：自动将历史对话注入AI上下文</li>
 *   <li>会话管理：自动创建/验证会话，保存对话消息</li>
 *   <li>记忆清除：支持清除指定会话的对话记忆</li>
 * </ul>
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
// 启用参数校验（方法级@NotNull等注解生效）
@Validated
// 使用Resilience4j限流器保护AI接口调用频率
@RateLimiter(name = "memoryChatService")
public class MemoryChatService {

    /**
     * 带记忆能力的ChatClient，预配置了系统提示词和记忆顾问
     */
    private final ChatClient chatClient;

    /**
     * 会话管理服务
     */
    private final ConversationService conversationService;

    /**
     * 数据库聊天记忆实现，负责对话历史的持久化存储和检索
     */
    private final DatabaseChatMemory chatMemory;

    /**
     * 构造方法：初始化带记忆的ChatClient
     *
     * @param chatModel           AI大模型客户端
     * @param chatMemory          数据库聊天记忆实现
     * @param conversationService 会话管理服务
     */
    public MemoryChatService(ChatModel chatModel,
                              DatabaseChatMemory chatMemory,
                              ConversationService conversationService) {
        // 保存聊天记忆引用
        this.chatMemory = chatMemory;
        // 保存会话服务引用
        this.conversationService = conversationService;
        // 构建带记忆的ChatClient
        this.chatClient = ChatClient.builder(chatModel)
                // 设置系统提示词，告知AI具有记忆能力
                .defaultSystem("你是一个记忆力超强的AI助手，能够记住对话中的所有细节和上下文。" +
                        "请基于对话历史提供连贯、准确的回答，用简洁清晰的中文回复。")
                // 注册消息记忆顾问，自动注入历史对话到上下文
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        log.info("MemoryChatService初始化完成，带记忆的ChatClient已构建");
    }

    /**
     * 带记忆的同步对话
     * AI基于对话历史提供连贯回答，同步返回完整结果
     *
     * @param req  记忆聊天请求，包含message和可选conversationId
     * @param user 当前登录用户
     * @return Map包含conversationId和AI回复reply
     * @throws BusinessException AI回复为空或调用失败时抛出
     */
    public Map<String, Object> chat(@Valid @NotNull(message = "请求参数不能为空") MemoryChatRequest req,
                                     @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        // 记录对话请求日志
        log.info("记忆对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        Long userId = user.getUserId();
        // 确保会话存在，不存在时自动创建
        Long conversationId = ensureConversation(userId, req.getConversationId(), req.getMessage(), "memory");
        // 将会话ID转为字符串，供记忆顾问使用
        String convIdStr = String.valueOf(conversationId);

        String aiReply;
        try {
            // 调用AI客户端，设置会话记忆ID
            aiReply = chatClient.prompt()
                    .user(req.getMessage())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convIdStr))
                    .call()
                    .content();
            // 校验AI回复不能为空
            if (aiReply == null || aiReply.isEmpty()) {
                throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, "AI回复为空");
            }
        } catch (BusinessException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            // 其他异常包装为业务异常
            log.error("记忆对话AI调用失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, e.getMessage());
        }

        // 构建返回结果Map
        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversationId);
        result.put("reply", aiReply);
        log.info("记忆对话完成: userId={}, conversationId={}", user.getUserId(), conversationId);
        return result;
    }

    /**
     * 带记忆的SSE流式对话
     * 实时推送AI回复token，同时保存用户消息和AI回复到数据库
     *
     * @param req  记忆聊天请求
     * @param user 当前登录用户
     * @return Flux&lt;String&gt; SSE数据流
     * @throws BusinessException 参数校验失败时抛出
     */
    public Flux<String> streamChat(@Valid @NotNull(message = "请求参数不能为空") MemoryChatRequest req,
                                    @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("记忆流式对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        Long userId = user.getUserId();
        // 确保会话存在
        Long conversationId = ensureConversation(userId, req.getConversationId(), req.getMessage(), "memory");
        String convIdStr = String.valueOf(conversationId);
        // 保存为final变量供lambda表达式使用
        final Long convId = conversationId;
        final Long uid = userId;

        // 先保存用户消息到数据库
        conversationService.saveMessage(userId, conversationId, "user", req.getMessage());

        // 创建StringBuilder累积完整AI回复
        StringBuilder fullReply = new StringBuilder();

        // 发起流式请求
        return chatClient.prompt()
                    .user(req.getMessage())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convIdStr))
                    .stream()
                    .content()
                // 每个token到达时累积到缓冲区
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        fullReply.append(chunk);
                    }
                })
                // 流完成时保存AI回复到数据库
                .doOnComplete(() -> {
                    String aiReply = fullReply.toString();
                    if (aiReply != null && !aiReply.isEmpty()) {
                        conversationService.saveMessage(uid, convId, "assistant", aiReply);
                    }
                    log.info("记忆流式对话完成: userId={}, conversationId={}, replyLength={}",
                            uid, convId, fullReply.length());
                })
                // 流异常时记录错误日志
                .doOnError(e -> log.error("记忆流式对话错误: conversationId={}, error={}",
                        convId, e.getMessage(), e))
                // 异常处理：返回错误消息而非抛出异常
                .onErrorResume(e -> Flux.just("[ERROR] " + (e.getMessage() != null ? e.getMessage() : "AI调用失败")));
    }

    /**
     * 清除指定会话的聊天记忆
     * 验证会话归属权后，删除数据库中该会话的所有记忆消息
     *
     * @param userId         用户ID
     * @param conversationId 会话ID
     */
    public void clearMemory(@NotNull(message = "用户ID不能为空") Long userId,
                            @NotNull(message = "会话ID不能为空") Long conversationId) {
        // 验证会话归属权
        conversationService.getConversationById(userId, conversationId);
        // 清除该会话的所有记忆
        chatMemory.clear(String.valueOf(conversationId));
        log.info("会话记忆已清除: userId={}, conversationId={}", userId, conversationId);
    }

    /**
     * 按会话ID字符串清除记忆（内部使用）
     * 异常时仅记录警告日志，不抛出异常
     *
     * @param conversationId 会话ID字符串
     */
    public void clearMemory(String conversationId) {
        try {
            chatMemory.clear(conversationId);
        } catch (Exception e) {
            log.warn("清除记忆失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 简易同步对话方法（内部调用）
     * 直接按会话ID发送消息并获取回复
     *
     * @param conversationId 会话ID字符串
     * @param userMessage    用户消息
     * @return AI回复文本
     */
    public String chat(String conversationId, String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    /**
     * 简易流式对话方法（内部调用）
     * 直接按会话ID发送消息并流式返回token
     *
     * @param conversationId 会话ID字符串
     * @param userMessage    用户消息
     * @return Flux&lt;String&gt; 流式token序列
     */
    public Flux<String> streamChat(String conversationId, String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    /**
     * 确保会话存在的辅助方法
     * 如果conversationId为null则自动创建新会话，否则验证会话归属权
     *
     * @param userId         用户ID
     * @param conversationId 会话ID（可能为null）
     * @param userMessage    用户消息（用于生成会话标题）
     * @param type           会话类型
     * @return 有效的会话ID
     */
    private Long ensureConversation(Long userId, Long conversationId, String userMessage, String type) {
        if (conversationId == null) {
            // 自动创建新会话，标题取消息前20字符
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            Conversation conversation = conversationService.createConversation(userId, title, type);
            log.info("自动创建新记忆会话: conversationId={}, userId={}", conversation.getId(), userId);
            return conversation.getId();
        }
        // 验证会话归属权
        conversationService.getConversationById(userId, conversationId);
        return conversationId;
    }
}
