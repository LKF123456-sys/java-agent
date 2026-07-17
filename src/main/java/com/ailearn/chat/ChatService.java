package com.ailearn.chat; // 声明包名，属于聊天模块

import com.ailearn.common.BusinessException; // 导入业务异常类，用于抛出可预期的业务错误
import com.ailearn.common.ErrorCode; // 导入错误码枚举，提供标准化的错误码定义
import com.ailearn.dto.ChatRequest; // 导入聊天请求DTO，封装用户发送的聊天参数
import com.ailearn.entity.Conversation; // 导入会话实体类，对应数据库中的会话记录
import com.ailearn.security.UserPrincipal; // 导入用户认证主体，包含当前登录用户的信息
import com.ailearn.service.ConversationService; // 导入会话服务类，提供会话CRUD操作
import io.github.resilience4j.ratelimiter.annotation.RateLimiter; // 导入限流注解，控制接口调用频率
import jakarta.validation.Valid; // 导入参数校验注解，触发JSR380校验规则
import jakarta.validation.constraints.NotNull; // 导入非空校验注解，确保参数不为null
import lombok.extern.slf4j.Slf4j; // 导入Lombok日志注解，自动生成log对象
import org.springframework.ai.chat.client.ChatClient; // 导入Spring AI聊天客户端，封装大模型调用能力
import org.springframework.ai.chat.model.ChatModel; // 导入聊天模型接口，Spring AI的核心抽象
import org.springframework.stereotype.Service; // 导入服务注解，标记此类为Spring业务层组件
import org.springframework.validation.annotation.Validated; // 导入方法级校验注解，启用@Valid/@NotNull等参数校验
import reactor.core.publisher.Flux; // 导入Reactor的Flux响应式流类型，用于SSE流式输出

import java.util.HashMap; // 导入HashMap，用于构建返回结果Map
import java.util.Map; // 导入Map接口，作为返回值的类型声明

/**
 * 聊天服务类
 * 提供基础AI对话功能，支持同步调用和SSE流式输出两种模式
 * 集成会话管理，自动创建和保存对话记录
 *
 * @author AiLearn Platform
 */
@Slf4j // Lombok注解，自动注入log对象用于日志记录
@Service // Spring注解，标记此类为业务层组件，由Spring容器管理生命周期
@Validated // 启用方法级参数校验，使@NotNull等注解生效
@RateLimiter(name = "chatService") // 限流注解，使用RateLimiterConfig中定义的chatService限流策略
public class ChatService { // 聊天服务类定义

    private final ChatClient chatClient; // AI聊天客户端，封装了与大模型交互的能力（同步/流式）

    private final ConversationService conversationService; // 会话服务，提供会话创建、查询、消息保存等操作

    /**
     * 构造方法：初始化聊天服务
     * 注入ChatModel和ConversationService依赖，并构建ChatClient实例
     *
     * @param chatModel           Spring AI聊天模型（由Ollama自动配置注入）
     * @param conversationService 会话服务（由Spring自动注入）
     */
    public ChatService(ChatModel chatModel, ConversationService conversationService) { // 构造方法，注入依赖
        this.conversationService = conversationService; // 保存会话服务引用
        this.chatClient = ChatClient.builder(chatModel) // 使用Builder模式构建ChatClient
                .defaultSystem("你是一个专业、友好、有帮助的AI助手。请用简洁清晰的中文回答问题。" + // 设置默认系统提示词，定义AI角色
                        "回答要准确、有条理，避免编造虚假信息。如果不确定，请坦诚说明。") // 系统提示词续写，强调准确性
                .build(); // 完成ChatClient构建
        log.info("ChatService初始化完成，ChatClient已构建"); // 记录初始化完成日志
    } // 构造方法结束

    /**
     * 同步聊天方法
     * 发送用户消息到大模型，等待完整回复后返回结果
     * 自动管理会话：如果没有会话ID则创建新会话
     *
     * @param req  聊天请求，包含消息内容和会话ID
     * @param user 当前登录用户信息
     * @return Map 包含会话ID和AI回复的结果
     */
    public Map<String, Object> chat(@Valid @NotNull(message = "请求参数不能为空") ChatRequest req, // 校验请求参数非空
                                     @NotNull(message = "用户信息不能为空") UserPrincipal user) { // 校验用户信息非空
        log.info("普通对话请求: userId={}, conversationId={}, messageLength={}", // 记录请求日志
                user.getUserId(), req.getConversationId(), req.getMessage().length()); // 包含用户ID、会话ID、消息长度

        Long userId = user.getUserId(); // 从认证主体中提取用户ID
        Long conversationId = ensureConversation(userId, req.getConversationId(), req.getMessage(), "chat"); // 确保会话存在，不存在则创建
        conversationService.saveMessage(userId, conversationId, "user", req.getMessage()); // 保存用户发送的消息到数据库

        String aiReply; // 声明AI回复变量
        try { // 开始AI调用异常处理
            aiReply = chatClient.prompt() // 构建聊天提示
                    .user(req.getMessage()) // 设置用户消息内容
                    .call() // 同步调用大模型，等待完整回复
                    .content(); // 提取回复文本内容
            if (aiReply == null || aiReply.isEmpty()) { // 检查AI回复是否为空
                throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, "AI回复为空"); // 空回复时抛出业务异常
            } // 空回复检查结束
        } catch (BusinessException e) { // 捕获业务异常（不包装，直接重新抛出）
            throw e; // 重新抛出业务异常，保持原始错误码
        } catch (Exception e) { // 捕获其他所有异常（如网络错误、模型不可用等）
            log.error("AI调用失败: conversationId={}, error={}", conversationId, e.getMessage(), e); // 记录详细错误日志
            throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, e.getMessage()); // 包装为业务异常抛出
        } // try-catch结束

        conversationService.saveMessage(userId, conversationId, "assistant", aiReply); // 保存AI回复到数据库

        Map<String, Object> result = new HashMap<>(); // 创建返回结果Map
        result.put("conversationId", conversationId); // 放入会话ID，前端可用于后续对话
        result.put("reply", aiReply); // 放入AI回复内容
        log.info("普通对话完成: userId={}, conversationId={}", user.getUserId(), conversationId); // 记录完成日志
        return result; // 返回结果Map
    } // chat方法结束

    /**
     * 流式聊天方法（SSE）
     * 发送用户消息到大模型，以SSE流方式实时输出AI回复的每个token
     * 流式完成后自动保存完整回复到数据库
     *
     * @param req  聊天请求
     * @param user 当前登录用户信息
     * @return Flux<String> AI回复的token流，每个元素为一个文本片段
     */
    public Flux<String> streamChat(@Valid @NotNull(message = "请求参数不能为空") ChatRequest req, // 校验请求参数
                                    @NotNull(message = "用户信息不能为空") UserPrincipal user) { // 校验用户信息
        log.info("流式对话请求: userId={}, conversationId={}, messageLength={}", // 记录流式请求日志
                user.getUserId(), req.getConversationId(), req.getMessage().length()); // 包含关键请求信息

        Long userId = user.getUserId(); // 提取用户ID
        Long conversationId = ensureConversation(userId, req.getConversationId(), req.getMessage(), "chat"); // 确保会话存在
        conversationService.saveMessage(userId, conversationId, "user", req.getMessage()); // 保存用户消息
        final Long finalConversationId = conversationId; // 将变量声明为final，供lambda表达式引用
        final Long finalUserId = userId; // 将用户ID声明为final，供lambda表达式引用

        StringBuilder fullReply = new StringBuilder(); // 创建StringBuilder收集完整的AI回复

        return chatClient.prompt() // 构建聊天提示
                    .user(req.getMessage()) // 设置用户消息
                    .stream() // 流式调用大模型，返回Flux响应式流
                    .content() // 提取文本内容流
                .doOnNext(chunk -> { // 每收到一个文本块时执行
                    if (chunk != null) { // 空值保护
                        fullReply.append(chunk); // 将文本块追加到完整回复中
                    } // 空值检查结束
                }) // doOnNext结束
                .doOnComplete(() -> { // 流完成时执行
                    String aiReply = fullReply.toString(); // 将StringBuilder转为完整回复字符串
                    if (!aiReply.isEmpty()) { // 仅在回复非空时保存
                        conversationService.saveMessage(finalUserId, finalConversationId, "assistant", aiReply); // 保存完整回复到数据库
                        log.info("流式对话完成: userId={}, conversationId={}, replyLength={}", // 记录完成日志
                                user.getUserId(), finalConversationId, aiReply.length()); // 包含回复长度等关键信息
                    } // 非空检查结束
                }) // doOnComplete结束
                .doOnError(e -> log.error("流式对话错误: conversationId={}, error={}", // 流出错时记录日志
                        finalConversationId, e.getMessage(), e)) // 包含会话ID和错误详情
                .onErrorResume(e -> Flux.just("[ERROR] " + (e.getMessage() != null ? e.getMessage() : "AI调用失败"))); // 错误时返回错误提示而非中断流
    } // streamChat方法结束

    /**
     * 简化版同步聊天（内部调用用）
     * 不管理会话，直接发送消息并返回AI回复，供Agent等其他模块调用
     *
     * @param userMessage 用户消息文本
     * @return String AI回复内容
     */
    public String chat(String userMessage) { // 简化版聊天方法
        return chatClient.prompt() // 构建聊天提示
                .user(userMessage) // 设置用户消息
                .call() // 同步调用
                .content(); // 返回回复文本
    } // 简化版chat方法结束

    /**
     * 简化版流式聊天（内部调用用）
     * 不管理会话，直接发送消息并返回流式回复
     *
     * @param userMessage 用户消息文本
     * @return Flux<String> AI回复的token流
     */
    public Flux<String> streamChat(String userMessage) { // 简化版流式聊天方法
        return chatClient.prompt() // 构建聊天提示
                .user(userMessage) // 设置用户消息
                .stream() // 流式调用
                .content(); // 返回文本流
    } // 简化版streamChat方法结束

    /**
     * 带自定义系统提示词的同步聊天
     * 允许调用方覆盖默认系统提示词，实现不同角色的AI助手
     *
     * @param userMessage  用户消息文本
     * @param systemPrompt 自定义系统提示词
     * @return String AI回复内容
     */
    public String chatWithSystem(String userMessage, String systemPrompt) { // 带系统提示词的聊天方法
        return chatClient.prompt() // 构建聊天提示
                .system(systemPrompt) // 设置自定义系统提示词，覆盖默认提示
                .user(userMessage) // 设置用户消息
                .call() // 同步调用
                .content(); // 返回回复文本
    } // chatWithSystem方法结束

    /**
     * 确保会话存在
     * 如果conversationId为null，自动创建新会话；否则验证会话是否属于当前用户
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID（可为null）
     * @param userMessage    用户消息（用于生成会话标题）
     * @param type           会话类型（chat/agent/memory等）
     * @return Long 确保存在的会话ID
     */
    private Long ensureConversation(Long userId, Long conversationId, String userMessage, String type) { // 确保会话存在
        if (conversationId == null) { // 如果未提供会话ID，需要创建新会话
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage; // 取前20个字符作为标题
            Conversation conversation = conversationService.createConversation(userId, title, type); // 创建新会话
            log.info("自动创建新会话: conversationId={}, userId={}, title={}", conversation.getId(), userId, title); // 记录创建日志
            return conversation.getId(); // 返回新创建的会话ID
        } // 创建新会话分支结束
        conversationService.getConversationById(userId, conversationId); // 验证会话存在且属于当前用户
        return conversationId; // 返回已有的会话ID
    } // ensureConversation方法结束
} // ChatService类结束
