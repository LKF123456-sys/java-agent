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

/**
 * 基础聊天服务类
 * 提供与AI大模型的基础对话能力，支持同步和SSE流式两种响应模式，
 * 自动管理会话生命周期，将用户消息和AI回复持久化到数据库。
 *
 * <p>核心功能：
 * <ul>
 *   <li>同步对话：发送消息后等待AI完整回复再返回</li>
 *   <li>SSE流式对话：实时推送AI生成的token，提供打字机效果</li>
 *   <li>会话管理：自动创建新会话或复用已有会话</li>
 *   <li>消息持久化：保存用户提问和AI回复到数据库</li>
 *   <li>限流保护：通过Resilience4j限制调用频率</li>
 * </ul>
 *
 * <p>此服务是最基础的聊天实现，不具备工具调用、联网搜索或知识库检索能力，
 * 仅使用大模型的原生对话能力。如需Agent工具调用或RAG检索功能，请使用对应服务。
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
@Validated
@RateLimiter(name = "chatService")
public class ChatService {

    /**
     * AI聊天客户端
     * 基于Spring AI ChatClient构建，配置了默认系统提示词，用于与大模型交互
     */
    private final ChatClient chatClient;

    /**
     * 会话服务
     * 用于会话的创建、查询和消息的持久化存储
     */
    private final ConversationService conversationService;

    /**
     * 构造方法：初始化聊天服务
     * 构建ChatClient并配置默认系统提示词，设定AI助手的角色定位
     *
     * @param chatModel           Spring AI聊天模型，由框架自动注入
     * @param conversationService 会话服务，用于管理对话历史
     */
    public ChatService(ChatModel chatModel, ConversationService conversationService) {
        this.conversationService = conversationService;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你是一个专业、友好、有帮助的AI助手。请用简洁清晰的中文回答问题。" +
                        "回答要准确、有条理，避免编造虚假信息。如果不确定，请坦诚说明。")
                .build();
        log.info("ChatService初始化完成，ChatClient已构建");
    }

    /**
     * 同步对话接口（带用户认证和会话管理）
     * 接收用户消息，调用AI生成完整回复后同步返回，同时保存对话历史。
     *
     * <p>执行流程：
     * <ol>
     *   <li>确保会话存在：conversationId为空时自动创建新会话</li>
     *   <li>保存用户消息到数据库</li>
     *   <li>调用大模型生成回复（同步阻塞等待）</li>
     *   <li>保存AI回复到数据库</li>
     *   <li>返回包含conversationId和reply的结果Map</li>
     * </ol>
     *
     * @param req  聊天请求参数，包含用户消息和可选的会话ID
     * @param user 当前登录用户信息
     * @return Map&lt;String, Object&gt; 包含conversationId（会话ID）和reply（AI回复）的结果映射
     * @throws BusinessException 当AI回复为空或调用失败时抛出业务异常
     */
    public Map<String, Object> chat(@Valid @NotNull(message = "请求参数不能为空") ChatRequest req,
                                     @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("普通对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        // 确保会话存在，不存在则创建新会话
        Long conversationId = ensureConversation(req.getConversationId(), req.getMessage(), "chat");
        // 保存用户消息到数据库
        conversationService.saveMessage(conversationId, "user", req.getMessage());

        String aiReply;
        try {
            // 同步调用大模型获取完整回复
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

        // 保存AI回复到数据库
        conversationService.saveMessage(conversationId, "assistant", aiReply);

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversationId);
        result.put("reply", aiReply);
        log.info("普通对话完成: userId={}, conversationId={}", user.getUserId(), conversationId);
        return result;
    }

    /**
     * SSE流式对话接口（带用户认证和会话管理）
     * 以Server-Sent Events方式实时推送AI生成的token，提供流畅的打字机效果。
     * 流式传输完成后在后台异步保存完整的对话历史。
     *
     * <p>Flux响应式流处理链说明：
     * <ul>
     *   <li>doOnNext：逐token拼接完整回复内容</li>
     *   <li>doOnComplete：流结束后将完整AI回复保存到数据库</li>
     *   <li>doOnError：记录流式调用错误日志</li>
     *   <li>onErrorResume：发生错误时发送错误标记消息给前端</li>
     * </ul>
     *
     * @param req  聊天请求参数，包含用户消息和可选的会话ID
     * @param user 当前登录用户信息
     * @return Flux&lt;String&gt; SSE流式响应，每个元素是AI生成的一个token字符串
     */
    public Flux<String> streamChat(@Valid @NotNull(message = "请求参数不能为空") ChatRequest req,
                                    @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("流式对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length());

        // 确保会话存在
        Long conversationId = ensureConversation(req.getConversationId(), req.getMessage(), "chat");
        // 保存用户消息
        conversationService.saveMessage(conversationId, "user", req.getMessage());
        final Long finalConversationId = conversationId;

        // 用于拼接完整回复的StringBuilder
        StringBuilder fullReply = new StringBuilder();

        // 构建Flux流式处理链
        return chatClient.prompt()
                .user(req.getMessage())
                .stream()
                .content()
                // 每个token到达时追加到fullReply
                .doOnNext(fullReply::append)
                // 流完成时保存完整回复到数据库
                .doOnComplete(() -> {
                    String aiReply = fullReply.toString();
                    if (!aiReply.isEmpty()) {
                        conversationService.saveMessage(finalConversationId, "assistant", aiReply);
                        log.info("流式对话完成: userId={}, conversationId={}, replyLength={}",
                                user.getUserId(), finalConversationId, aiReply.length());
                    }
                })
                // 记录错误日志
                .doOnError(e -> log.error("流式对话错误: conversationId={}, error={}",
                        finalConversationId, e.getMessage(), e))
                // 错误恢复：向前端发送错误消息
                .onErrorResume(e -> Flux.just("[ERROR] " + (e.getMessage() != null ? e.getMessage() : "AI调用失败")));
    }

    /**
     * 简化版同步对话接口（无会话管理）
     * 仅接收用户消息并返回AI回复，不进行会话管理和消息持久化。
     * 适用于内部服务调用或不需要保存历史的简单场景。
     *
     * @param userMessage 用户消息内容
     * @return String AI生成的回复内容
     */
    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }

    /**
     * 简化版流式对话接口（无会话管理）
     * 仅接收用户消息并返回token流，不进行会话管理和消息持久化。
     *
     * @param userMessage 用户消息内容
     * @return Flux&lt;String&gt; AI生成的token流
     */
    public Flux<String> streamChat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .stream()
                .content();
    }

    /**
     * 带自定义系统提示词的同步对话接口
     * 使用指定的系统提示词替代默认提示词，适用于需要定制AI角色的场景。
     *
     * @param userMessage  用户消息内容
     * @param systemPrompt 自定义系统提示词，定义AI的角色和行为规则
     * @return String AI生成的回复内容
     */
    public String chatWithSystem(String userMessage, String systemPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    /**
     * 确保会话存在的私有辅助方法
     * 如果conversationId为null，自动创建新会话；否则验证会话存在。
     *
     * @param conversationId 会话ID，为null时创建新会话
     * @param userMessage    用户消息，用于生成新会话标题（取前20个字符）
     * @param type           会话类型标识（如"chat"、"agent"等）
     * @return Long 有效的会话ID
     */
    private Long ensureConversation(Long conversationId, String userMessage, String type) {
        if (conversationId == null) {
            // 自动创建新会话，标题取用户消息前20个字符
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            Conversation conversation = conversationService.createConversation(title, type);
            log.info("自动创建新会话: conversationId={}, title={}", conversation.getId(), title);
            return conversation.getId();
        }
        // 验证已有会话存在
        conversationService.getConversationById(conversationId);
        return conversationId;
    }
}
