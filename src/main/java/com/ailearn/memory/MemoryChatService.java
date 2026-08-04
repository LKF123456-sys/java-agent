package com.ailearn.memory; // 声明当前类所在的包：memory（记忆对话模块）

// 导入业务异常类，用于抛出业务错误
import com.ailearn.common.BusinessException;
// 导入错误码枚举，定义了各类业务错误码
import com.ailearn.common.ErrorCode;
// 导入记忆对话请求DTO，包含message和可选conversationId
import com.ailearn.dto.MemoryChatRequest;
// 导入会话实体类，对应数据库的会话表
import com.ailearn.entity.Conversation;
// 导入用户安全主体，封装当前登录用户信息
import com.ailearn.security.UserPrincipal;
// 导入会话管理服务，负责会话的创建、查询、消息保存
import com.ailearn.service.ConversationService;
// 导入Resilience4j限流器注解，限制本服务的调用频率
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
// 导入Jakarta参数校验注解：@Valid触发DTO级联校验、@NotNull做方法参数非空校验
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
// 导入Lombok日志注解，自动生成log对象
import lombok.extern.slf4j.Slf4j;
// 导入Spring AI的ChatClient，流式API调用大模型的入口
import org.springframework.ai.chat.client.ChatClient;
// 导入消息记忆顾问，每次调用前自动把历史对话注入上下文
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
// 导入ChatMemory接口，定义了记忆存取的规范（含CONVERSATION_ID常量）
import org.springframework.ai.chat.memory.ChatMemory;
// 导入ChatModel接口，代表底层大模型（本项目是Ollama）
import org.springframework.ai.chat.model.ChatModel;
// 导入Spring的@Service注解，标记这是业务服务层Bean
import org.springframework.stereotype.Service;
// 导入@Validated注解，启用方法级参数校验（让@NotNull等在方法参数上生效）
import org.springframework.validation.annotation.Validated;
// 导入Reactor的Flux，SSE流式响应的返回类型
import reactor.core.publisher.Flux;

// 导入HashMap，拼装返回的键值对数据
import java.util.HashMap;
// 导入Map接口
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
@Slf4j // Lombok注解：自动生成log日志对象
@Service // Spring注解：标记为业务服务Bean
// 启用参数校验（方法级@NotNull等注解生效，配合AOP拦截校验）
@Validated
// 使用Resilience4j限流器保护AI接口调用频率（name对应application.yml的配置）
@RateLimiter(name = "memoryChatService")
public class MemoryChatService { // 定义记忆对话服务类

    /**
     * 带记忆能力的ChatClient，预配置了系统提示词和记忆顾问
     */
    private final ChatClient chatClient; // 预配置的ChatClient，构建时就绑定了系统提示词+记忆顾问

    /**
     * 会话管理服务
     */
    private final ConversationService conversationService; // 注入会话服务：创建会话、保存消息

    /**
     * 数据库聊天记忆实现，负责对话历史的持久化存储和检索
     */
    private final DatabaseChatMemory chatMemory; // 注入记忆实现：clearMemory时要直接操作它

    /**
     * 构造方法：初始化带记忆的ChatClient
     *
     * @param chatModel           AI大模型客户端
     * @param chatMemory          数据库聊天记忆实现
     * @param conversationService 会话管理服务
     */
    public MemoryChatService(ChatModel chatModel, // 构造器注入3个依赖
                              DatabaseChatMemory chatMemory,
                              ConversationService conversationService) {
        // 保存聊天记忆引用到成员变量
        this.chatMemory = chatMemory;
        // 保存会话服务引用到成员变量
        this.conversationService = conversationService;
        // 构建带记忆的ChatClient
        this.chatClient = ChatClient.builder(chatModel) // 基于底层模型开始构建
                // 设置系统提示词，告知AI具有记忆能力
                .defaultSystem("你是一个记忆力超强的AI助手，能够记住对话中的所有细节和上下文。" +
                        "请基于对话历史提供连贯、准确的回答，用简洁清晰的中文回复。") // 每次调用都自动携带这段系统提示词
                // 注册消息记忆顾问，自动注入历史对话到上下文（Spring AI 2.0中该顾问默认在工具循环外运行，只存最终一问一答）
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()) // 用数据库记忆构建顾问
                .build(); // 构建完成
        log.info("MemoryChatService初始化完成，带记忆的ChatClient已构建"); // 打印初始化日志
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
    public Map<String, Object> chat(@Valid @NotNull(message = "请求参数不能为空") MemoryChatRequest req, // @Valid级联校验DTO，@NotNull校验对象本身
                                     @NotNull(message = "用户信息不能为空") UserPrincipal user) { // @NotNull校验用户不为空
        // 记录对话请求日志
        log.info("记忆对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length()); // 记录用户ID、会话ID、消息长度

        Long userId = user.getUserId(); // 取出用户ID
        // 确保会话存在，不存在时自动创建
        Long conversationId = ensureConversation(userId, req.getConversationId(), req.getMessage(), "memory"); // 拿到有效会话ID
        // 将会话ID转为字符串，供记忆顾问使用（Spring AI的记忆键是String类型）
        String convIdStr = String.valueOf(conversationId);

        String aiReply; // 声明AI回复变量
        try {
            // 调用AI客户端，设置会话记忆ID
            aiReply = chatClient.prompt() // 开始构建一次AI调用
                    .user(req.getMessage()) // 设置用户消息
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convIdStr)) // 告诉记忆顾问用哪个会话ID读写历史
                    .call() // 同步调用（阻塞等完整结果）
                    .content(); // 取出回答文本
            // 校验AI回复不能为空（防御性编程）
            if (aiReply == null || aiReply.isEmpty()) {
                throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, "AI回复为空"); // 空回复视为调用失败
            }
        } catch (BusinessException e) {
            // 业务异常直接抛出（不要二次包装，保留原始错误码）
            throw e;
        } catch (Exception e) {
            // 其他异常（网络超时、模型崩溃等）包装为业务异常
            log.error("记忆对话AI调用失败: conversationId={}, error={}", conversationId, e.getMessage(), e); // 记录错误含堆栈
            throw new BusinessException(ErrorCode.CHAT_AI_CALL_FAILED, e.getMessage()); // 抛出统一的AI调用失败错误
        }

        // 构建返回结果Map
        Map<String, Object> result = new HashMap<>(); // 创建返回Map
        result.put("conversationId", conversationId); // 放入会话ID（前端后续对话要带上）
        result.put("reply", aiReply); // 放入AI回复
        log.info("记忆对话完成: userId={}, conversationId={}", user.getUserId(), conversationId); // 打印完成日志
        return result; // 返回结果
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
    public Flux<String> streamChat(@Valid @NotNull(message = "请求参数不能为空") MemoryChatRequest req, // 流式版本
                                    @NotNull(message = "用户信息不能为空") UserPrincipal user) {
        log.info("记忆流式对话请求: userId={}, conversationId={}, messageLength={}",
                user.getUserId(), req.getConversationId(), req.getMessage().length()); // 打印请求日志

        Long userId = user.getUserId(); // 取出用户ID
        // 确保会话存在
        Long conversationId = ensureConversation(userId, req.getConversationId(), req.getMessage(), "memory");
        String convIdStr = String.valueOf(conversationId); // 转字符串供记忆顾问使用
        // 保存为final变量供lambda表达式使用（Java闭包要求引用的局部变量必须final或有效final）
        final Long convId = conversationId; // 会话ID的final副本
        final Long uid = userId; // 用户ID的final副本

        // 先保存用户消息到数据库（流式场景要先存用户消息，AI回复在流完成时再存）
        conversationService.saveMessage(userId, conversationId, "user", req.getMessage());

        // 创建StringBuilder累积完整AI回复（token一个个来，要累积成完整回答才能存库）
        StringBuilder fullReply = new StringBuilder();

        // 发起流式请求
        return chatClient.prompt() // 构建AI调用
                    .user(req.getMessage()) // 设置用户消息
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convIdStr)) // 指定记忆会话ID
                    .stream() // 流式调用
                    .content() // 取出每个token的文本，形成Flux<String>
                // 每个token到达时累积到缓冲区（doOnNext是副作用操作）
                .doOnNext(chunk -> {
                    if (chunk != null) { // 防空指针
                        fullReply.append(chunk); // 追加到缓冲区
                    }
                })
                // 流完成时保存AI回复到数据库（doOnComplete在所有token发完后触发）
                .doOnComplete(() -> {
                    String aiReply = fullReply.toString(); // 取出累积的完整回复
                    if (aiReply != null && !aiReply.isEmpty()) { // 非空才保存
                        conversationService.saveMessage(uid, convId, "assistant", aiReply); // role="assistant"存AI回复
                    }
                    log.info("记忆流式对话完成: userId={}, conversationId={}, replyLength={}",
                            uid, convId, fullReply.length()); // 打印完成日志
                })
                // 流异常时记录错误日志（doOnError在流出错时触发）
                .doOnError(e -> log.error("记忆流式对话错误: conversationId={}, error={}",
                        convId, e.getMessage(), e))
                // 异常处理：返回错误消息而非抛出异常（防止前端无限等待）
                .onErrorResume(e -> Flux.just("[ERROR] " + (e.getMessage() != null ? e.getMessage() : "AI调用失败"))); // 用错误消息替换流
    }

    /**
     * 清除指定会话的聊天记忆
     * 验证会话归属权后，删除数据库中该会话的所有记忆消息
     *
     * @param userId         用户ID
     * @param conversationId 会话ID
     */
    public void clearMemory(@NotNull(message = "用户ID不能为空") Long userId, // 带归属校验的清除（对外接口用）
                            @NotNull(message = "会话ID不能为空") Long conversationId) {
        // 验证会话归属权（不存在或不属于当前用户时，getConversationById内部会抛异常）
        conversationService.getConversationById(userId, conversationId);
        // 清除该会话的所有记忆
        chatMemory.clear(String.valueOf(conversationId)); // 调用DatabaseChatMemory删除记忆
        log.info("会话记忆已清除: userId={}, conversationId={}", userId, conversationId); // 打印日志
    }

    /**
     * 按会话ID字符串清除记忆（内部使用）
     * 异常时仅记录警告日志，不抛出异常
     *
     * @param conversationId 会话ID字符串
     */
    public void clearMemory(String conversationId) { // 重载版本：无归属校验，内部调用（如删除会话时级联清理）
        try {
            chatMemory.clear(conversationId); // 直接清除记忆
        } catch (Exception e) { // 捕获一切异常
            log.warn("清除记忆失败: conversationId={}", conversationId, e); // 只记警告不抛出（清理失败不该阻断主流程）
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
    public String chat(String conversationId, String userMessage) { // 轻量版同步对话（不碰数据库会话，供内部模块复用）
        return chatClient.prompt() // 构建AI调用
                .user(userMessage) // 设置用户消息
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)) // 指定记忆会话ID
                .call() // 同步调用
                .content(); // 返回回答文本
    }

    /**
     * 简易流式对话方法（内部调用）
     * 直接按会话ID发送消息并流式返回token
     *
     * @param conversationId 会话ID字符串
     * @param userMessage    用户消息
     * @return Flux&lt;String&gt; 流式token序列
     */
    public Flux<String> streamChat(String conversationId, String userMessage) { // 轻量版流式对话
        return chatClient.prompt() // 构建AI调用
                .user(userMessage) // 设置用户消息
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)) // 指定记忆会话ID
                .stream() // 流式调用
                .content(); // 返回token流
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
    private Long ensureConversation(Long userId, Long conversationId, String userMessage, String type) { // 私有辅助方法
        if (conversationId == null) { // 情况1：未传会话ID，自动创建
            // 自动创建新会话，标题取消息前20字符
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage; // 超20字符截断
            Conversation conversation = conversationService.createConversation(userId, title, type); // 调用会话服务创建
            log.info("自动创建新记忆会话: conversationId={}, userId={}", conversation.getId(), userId); // 打印日志
            return conversation.getId(); // 返回新会话ID
        }
        // 情况2：传了会话ID，验证归属权（不存在会抛异常）
        conversationService.getConversationById(userId, conversationId);
        return conversationId; // 验证通过，返回原会话ID
    }
}
