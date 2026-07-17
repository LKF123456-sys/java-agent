package com.ailearn.agent;

// 导入业务异常类，用于抛出可预期的业务错误
import com.ailearn.common.BusinessException;
// 导入错误码枚举，定义所有业务错误码常量
import com.ailearn.common.ErrorCode;
// 导入文本分块工具类，用于模拟SSE流式输出
import com.ailearn.common.StreamUtils;
// 导入Agent聊天请求DTO
import com.ailearn.dto.AgentChatRequest;
// 导入会话实体类
import com.ailearn.entity.Conversation;
// 导入数据库聊天记忆实现
import com.ailearn.memory.DatabaseChatMemory;
// 导入用户安全主体
import com.ailearn.security.UserPrincipal;
// 导入会话管理服务
import com.ailearn.service.ConversationService;
// 导入Resilience4j限流器注解
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
// 导入Lombok日志注解
import lombok.extern.slf4j.Slf4j;
// 导入Spring AI ChatClient核心类
import org.springframework.ai.chat.client.ChatClient;
// 导入消息记忆顾问，自动注入对话上下文
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
// 导入ChatMemory接口，定义会话ID参数名常量
import org.springframework.ai.chat.memory.ChatMemory;
// 导入ChatModel接口，底层AI模型抽象
import org.springframework.ai.chat.model.ChatModel;
// 导入工具回调提供者，注册所有可用AI工具
import org.springframework.ai.tool.ToolCallbackProvider;
// 导入Spring Service注解
import org.springframework.stereotype.Service;
// 导入Spring字符串工具类
import org.springframework.util.StringUtils;
// 导入Reactor Flux响应式流
import reactor.core.publisher.Flux;
// 导入Reactor Mono单值响应式容器
import reactor.core.publisher.Mono;
// 导入弹性线程池调度器，用于异步执行阻塞操作
import reactor.core.scheduler.Schedulers;

// 导入时间Duration类，用于流式延迟设置
import java.time.Duration;

/**
 * 单Agent工具调用服务
 * 提供具备天气查询、数学计算、联网搜索等工具调用能力的单Agent对话服务。
 * Agent可自主决定调用哪些工具来完成用户任务，支持同步和SSE流式两种调用方式。
 *
 * <p>核心功能：
 * <ul>
 *   <li>同步工具调用：发送任务给Agent，等待完整结果返回</li>
 *   <li>SSE流式工具调用：实时推送Agent的输出token</li>
 *   <li>旅游规划：综合天气查询和费用计算的示例场景</li>
 *   <li>通用任务执行：同步和流式两种模式的通用任务处理</li>
 * </ul>
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
// 使用Resilience4j限流器，限制agentService的调用频率
@RateLimiter(name = "agentService")
public class AgentService {

    /**
     * Agent客户端实例，预配置了系统提示词、记忆顾问和工具回调
     * 所有API共享同一个ChatClient，通过conversationId区分会话上下文
     */
    private final ChatClient agentClient;

    /**
     * 会话管理服务，负责会话创建、查询和消息持久化
     */
    private final ConversationService conversationService;

    /**
     * 构造方法：通过Spring依赖注入初始化Agent客户端
     *
     * @param chatModel            AI大模型客户端
     * @param chatMemory           数据库聊天记忆
     * @param conversationService  会话管理服务
     * @param toolCallbackProvider 工具回调提供者
     */
    public AgentService(ChatModel chatModel,
                        DatabaseChatMemory chatMemory,
                        ConversationService conversationService,
                        ToolCallbackProvider toolCallbackProvider) {
        // 保存会话服务引用
        this.conversationService = conversationService;
        // 获取所有已注册的工具回调列表
        var callbacks = toolCallbackProvider.getToolCallbacks();
        // 构建Agent客户端，配置系统提示词、记忆顾问和工具
        this.agentClient = ChatClient.builder(chatModel)
                // 设置Agent的系统提示词，定义其角色和能力
                .defaultSystem("""
                        你是一个专业的AI助手，具有以下能力：
                        1. 查询各城市天气信息（使用天气工具）
                        2. 进行数学计算（使用计算器工具）
                        3. 联网搜索获取实时信息（使用searchWeb搜索工具）
                        4. 获取系统信息（使用系统工具）
                        5. 根据用户需求给出专业建议
                        
                        请主动使用工具获取真实信息，而不是凭空猜测。
                        对于实时信息（新闻、价格、最新动态等），必须使用searchWeb工具搜索。
                        思考步骤：分析问题 → 判断是否需要工具 → 调用工具 → 综合回答
                        """)
                // 注册消息记忆顾问，自动将历史对话注入上下文
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                // 注册所有工具回调，使Agent可以自主调用工具
                .defaultToolCallbacks(callbacks)
                .build();
        // 打印初始化日志，记录可用工具数量
        log.info("AgentService初始化完成，已注册工具: {}", callbacks.length);
    }

    /**
     * Agent同步工具调用
     * 发送任务给Agent，Agent可自主调用工具完成任务，同步返回完整结果
     *
     * @param req  Agent聊天请求，包含task和可选conversationId
     * @param user 当前登录用户主体
     * @return Agent的完整回复文本
     * @throws BusinessException 任务为空、会话不存在或执行失败时抛出
     */
    public String callWithTools(AgentChatRequest req, UserPrincipal user) {
        // 获取当前用户ID
        Long userId = user.getUserId();
        // 记录调用开始日志，截取任务前50字符防止日志过长
        log.info("Agent同步工具调用开始: userId={}, task={}",
                userId,
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        // 获取用户任务描述
        String task = req.getTask();
        // 校验任务内容不能为空
        if (!StringUtils.hasText(task)) {
            log.warn("Agent调用失败：任务内容为空");
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        // 获取请求中的会话ID（可能为null）
        Long conversationId = req.getConversationId();
        Long finalConversationId;

        // 如果未提供会话ID，自动创建新会话
        if (conversationId == null) {
            log.debug("创建新Agent会话: userId={}", userId);
            // 创建会话，标题取任务前50字符，类型为agent
            Conversation conversation = conversationService.createConversation(
                    userId,
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "agent"
            );
            finalConversationId = conversation.getId();
            req.setConversationId(finalConversationId);
            log.debug("新Agent会话创建成功: conversationId={}", finalConversationId);
        } else {
            log.debug("使用已有Agent会话: conversationId={}", conversationId);
            // 验证会话归属权，确保当前用户有权访问该会话
            Conversation existing = conversationService.getConversationById(userId, conversationId);
            if (existing == null) {
                throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
            }
            finalConversationId = conversationId;
        }

        // 保存用户发送的任务消息到数据库
        conversationService.saveMessage(userId, finalConversationId, "user", task);
        log.debug("用户消息已保存: conversationId={}", finalConversationId);

        String response;
        try {
            // 调用Agent客户端，发送任务并使用agent_前缀隔离记忆上下文
            response = agentClient.prompt()
                    .user(task)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "agent_" + finalConversationId))
                    .call()
                    .content();
            if (response != null) {
                log.info("Agent同步调用完成: conversationId={}, responseLength={}", finalConversationId, response.length());
            } else {
                log.warn("Agent同步调用返回null: conversationId={}", finalConversationId);
            }
        } catch (Exception e) {
            // 调用异常时记录错误日志并抛出业务异常
            log.error("Agent调用失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, e);
        }

        // 如果AI回复非空，保存回复消息到数据库
        if (StringUtils.hasText(response)) {
            conversationService.saveMessage(userId, finalConversationId, "assistant", response);
            log.debug("AI回复已保存: conversationId={}", finalConversationId);
        }

        // 返回Agent回复，null时返回空字符串
        return response != null ? response : "";
    }

    /**
     * Agent SSE流式工具调用
     * 以Server-Sent Events方式实时推送Agent输出，包括工具调用过程和最终回复
     *
     * @param req  Agent聊天请求
     * @param user 当前登录用户主体
     * @return Flux&lt;String&gt; SSE数据流，实时推送token
     * @throws BusinessException 任务为空或会话不存在时抛出
     */
    public Flux<String> streamCallWithTools(AgentChatRequest req, UserPrincipal user) {
        Long userId = user.getUserId();
        log.info("Agent流式工具调用开始: userId={}, task={}",
                userId,
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            log.warn("Agent流式调用失败：任务内容为空");
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        Long conversationId = req.getConversationId();
        Long finalConversationId;

        // 未提供会话ID时自动创建新会话
        if (conversationId == null) {
            log.debug("创建新Agent会话（流式）: userId={}", userId);
            Conversation conversation = conversationService.createConversation(
                    userId,
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "agent"
            );
            finalConversationId = conversation.getId();
            req.setConversationId(finalConversationId);
            log.debug("新Agent会话创建成功（流式）: conversationId={}", finalConversationId);
        } else {
            log.debug("使用已有Agent会话（流式）: conversationId={}", conversationId);
            Conversation existing = conversationService.getConversationById(userId, conversationId);
            if (existing == null) {
                throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
            }
            finalConversationId = conversationId;
        }

        // 保存用户消息
        conversationService.saveMessage(userId, finalConversationId, "user", task);
        log.debug("用户消息已保存（流式）: conversationId={}", finalConversationId);
        // 保存为final变量供lambda表达式使用
        final Long convId = finalConversationId;
        final Long uid = userId;

        // 创建StringBuilder累积完整回复，用于流结束后持久化
        StringBuilder fullReply = new StringBuilder();

        // 发起流式请求，返回Flux token序列
        return agentClient.prompt()
                    .user(task)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "agent_" + convId))
                    .stream()
                    .content()
                // 每个token到达时累积到fullReply
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        fullReply.append(chunk);
                    }
                })
                // 流完成时将完整AI回复保存到数据库
                .doOnComplete(() -> {
                    String aiReply = fullReply.toString();
                    if (StringUtils.hasText(aiReply)) {
                        conversationService.saveMessage(uid, convId, "assistant", aiReply);
                        log.info("Agent流式调用完成: conversationId={}, responseLength={}", convId, aiReply.length());
                    }
                })
                // 流异常时记录错误日志
                .doOnError(e -> log.error("Agent流式调用错误: conversationId={}, error={}", convId, e.getMessage(), e))
                // 异常处理：返回错误消息而不是抛出异常，防止前端断开连接
                .onErrorResume(e -> {
                    log.error("Agent流式调用失败: conversationId={}, error={}", convId, e.getMessage(), e);
                    String errMsg = e.getMessage() != null ? e.getMessage() : "Agent调用失败";
                    return Flux.just("[ERROR] " + errMsg);
                });
    }

    /**
     * 旅游规划专用方法
     * 综合天气查询和费用计算能力，为用户生成完整旅游计划
     *
     * @param destination 目的地城市名称
     * @param days        旅游天数
     * @return 完整旅游计划文本
     * @throws BusinessException 目的地为空时抛出
     */
    public String planTravel(String destination, int days) {
        log.info("旅游规划开始: destination={}, days={}", destination, days);
        // 校验目的地不能为空
        if (!StringUtils.hasText(destination)) {
            throw new BusinessException(ErrorCode.SYSTEM_PARAM_VALIDATION_ERROR, "目的地不能为空");
        }
        // 构建旅游规划提示词，包含天气查询、行程安排、费用估算和出行建议
        String prompt = "请帮我规划 " + destination + " 的 " + days + " 天旅游计划。\n"
                + "要求：\n"
                + "1. 先查询目的地天气，根据天气推荐合适的活动\n"
                + "2. 规划每天的行程安排\n"
                + "3. 估算大概费用（住宿 400元/晚，餐饮 150元/天，景点 200元/天）\n"
                + "4. 给出行前准备建议";

        // 调用Agent客户端生成旅游计划，使用travel_前缀隔离记忆
        String result = agentClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "travel_" + destination))
                .call()
                .content();
        // 记录结果长度日志
        int len = result != null ? result.length() : 0;
        log.info("旅游规划完成: destination={}, responseLength={}", destination, len);
        return result != null ? result : "";
    }

    /**
     * 通用任务同步执行方法
     * 发送通用任务给Agent处理，返回完整结果
     *
     * @param goal 任务目标描述
     * @return Agent的完整回复文本
     * @throws BusinessException 任务为空时抛出
     */
    public String executeTask(String goal) {
        log.info("通用任务执行开始: goal={}", goal != null ? goal.substring(0, Math.min(50, goal.length())) : "null");
        if (!StringUtils.hasText(goal)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        // 调用Agent执行任务，使用task_前缀+hash值隔离记忆
        String result = agentClient.prompt()
                .user(goal)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "task_" + Math.abs(goal.hashCode() % 10000)))
                .call()
                .content();
        int len = result != null ? result.length() : 0;
        log.info("通用任务执行完成: responseLength={}", len);
        return result != null ? result : "";
    }

    /**
     * 通用任务流式执行方法
     * 以SSE方式实时推送Agent处理通用任务的输出
     *
     * @param goal           任务目标描述
     * @param conversationId 会话ID字符串
     * @return Flux&lt;String&gt; 流式token序列
     */
    public Flux<String> streamTask(String goal, String conversationId) {
        log.info("通用任务流式执行开始: conversationId={}, goal={}",
                conversationId, goal != null ? goal.substring(0, Math.min(50, goal.length())) : "null");
        // 任务为空时返回错误消息
        if (!StringUtils.hasText(goal)) {
            return Flux.just("[ERROR] 任务内容不能为空");
        }
        // 发起流式请求
        return agentClient.prompt()
                .user(goal)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                // 流完成时记录日志
                .doOnComplete(() -> log.info("通用任务流式执行完成: conversationId={}", conversationId))
                // 流异常时记录错误日志
                .doOnError(e -> log.error("通用任务流式执行失败: conversationId={}, error={}",
                        conversationId, e.getMessage(), e));
    }
}
