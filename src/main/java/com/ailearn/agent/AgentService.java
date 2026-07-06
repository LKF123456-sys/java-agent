package com.ailearn.agent;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.dto.AgentChatRequest;
import com.ailearn.entity.Conversation;
import com.ailearn.memory.DatabaseChatMemory;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.ConversationService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * Agent智能体服务类
 * 提供基于大语言模型的智能体对话能力，支持工具调用（天气查询、数学计算、联网搜索等），
 * 同时支持同步调用和SSE流式输出两种模式，并自动将会话和消息持久化到数据库。
 *
 * <p>核心功能：
 * <ul>
 *   <li>工具调用对话：Agent可自动调用天气工具、计算器工具、联网搜索工具完成复杂任务</li>
 *   <li>会话持久化：自动创建/获取会话，保存用户提问和AI回复</li>
 *   <li>流式输出：支持SSE实时推送token，提升用户体验</li>
 *   <li>限流保护：通过Resilience4j限制调用频率，防止模型服务被压垮</li>
 * </ul>
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
@RateLimiter(name = "agentService")
public class AgentService {

    /**
     * Agent聊天客户端
     * 配置了系统提示词、对话记忆和工具回调，支持Agent自动调用工具完成任务
     */
    private final ChatClient agentClient;

    /**
     * 会话服务
     * 用于会话的创建、查询和消息持久化
     */
    private final ConversationService conversationService;

    /**
     * 构造方法：初始化Agent服务
     * 构建Agent专用的ChatClient，配置工具调用能力和对话记忆
     *
     * @param chatModel            聊天模型
     * @param chatMemory           数据库聊天记忆实现，用于多轮对话上下文管理
     * @param conversationService  会话服务
     * @param toolCallbackProvider 工具回调提供者，自动注入所有可用工具（天气、计算器、搜索等）
     */
    public AgentService(ChatModel chatModel,
                        DatabaseChatMemory chatMemory,
                        ConversationService conversationService,
                        ToolCallbackProvider toolCallbackProvider) {
        this.conversationService = conversationService;
        this.agentClient = ChatClient.builder(chatModel)
                // Agent系统提示词：定义AI助手角色为工具调用专家，指导何时使用各种工具
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
                // 配置对话记忆顾问，自动管理多轮对话上下文
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                // 注册所有可用工具，Agent可自主决定调用哪个工具
                .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                .build();
        log.info("AgentService初始化完成，已注册工具: {}", toolCallbackProvider.getToolCallbacks().length);
    }

    /**
     * Agent工具调用对话（同步模式）
     * 接收用户任务请求，调用Agent执行（可自动使用工具），同步返回完整结果，
     * 同时自动处理会话创建/获取和消息持久化。
     *
     * <p>执行流程：
     * <ol>
     *   <li>参数校验：验证任务内容非空</li>
     *   <li>会话处理：conversationId为空则创建新会话，否则获取已有会话</li>
     *   <li>保存用户消息：将用户任务持久化到数据库</li>
     *   <li>调用Agent：执行大模型推理（可能触发工具调用）</li>
     *   <li>保存AI回复：将AI响应持久化到数据库</li>
     *   <li>返回结果：返回AI生成的完整响应内容</li>
     * </ol>
     *
     * @param req  Agent聊天请求，包含任务内容和可选的会话ID
     * @param user 当前登录用户主体，包含用户ID和用户名等信息
     * @return String Agent执行完成后的完整响应内容
     * @throws BusinessException 参数校验失败或AI调用失败时抛出对应业务异常
     */
    public String callWithTools(AgentChatRequest req, UserPrincipal user) {
        log.info("Agent同步工具调用开始: userId={}, task={}",
                user != null ? user.getUserId() : "anonymous",
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            log.warn("Agent调用失败：任务内容为空");
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        Long conversationId = req.getConversationId();
        Long finalConversationId;

        if (conversationId == null) {
            log.debug("创建新Agent会话: userId={}", user != null ? user.getUserId() : "anonymous");
            Conversation conversation = conversationService.createConversation(
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "agent"
            );
            finalConversationId = conversation.getId();
            log.debug("新Agent会话创建成功: conversationId={}", finalConversationId);
        } else {
            log.debug("使用已有Agent会话: conversationId={}", conversationId);
            conversationService.getConversationById(conversationId);
            finalConversationId = conversationId;
        }

        conversationService.saveMessage(finalConversationId, "user", task);
        log.debug("用户消息已保存: conversationId={}", finalConversationId);

        String response;
        try {
            response = agentClient.prompt()
                    .user(task)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "agent_" + finalConversationId))
                    .call()
                    .content();
            log.info("Agent同步调用完成: conversationId={}, responseLength={}", finalConversationId, response.length());
        } catch (Exception e) {
            log.error("Agent调用失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, e);
        }

        if (StringUtils.hasText(response)) {
            conversationService.saveMessage(finalConversationId, "assistant", response);
            log.debug("AI回复已保存: conversationId={}", finalConversationId);
        }

        return response;
    }

    /**
     * Agent工具调用对话（SSE流式模式）
     * 接收用户任务请求，以SSE（Server-Sent Events）方式实时推送Agent生成的token，
     * 流式执行完成后自动保存会话和消息。支持工具调用，工具执行期间不输出token，
     * 工具返回结果后继续输出最终回复内容。
     *
     * <p>SSE输出格式：纯文本token流，由前端按EventSource规范解析并实时展示。
     * 流结束后会在后台异步保存完整的对话历史到数据库。
     *
     * <p>执行流程：
     * <ol>
     *   <li>参数校验：验证任务内容非空</li>
     *   <li>会话处理：创建新会话或获取已有会话</li>
     *   <li>保存用户消息</li>
     *   <li>调用Agent流式接口：逐token输出</li>
     *   <li>流结束后（doOnComplete）：拼接完整回复并保存到数据库</li>
     * </ol>
     *
     * @param req  Agent聊天请求，包含任务内容和可选的会话ID
     * @param user 当前登录用户主体
     * @return Flux<String> SSE流式响应，每个元素是一个token字符串
     * @throws BusinessException 参数校验失败时抛出业务异常
     */
    public Flux<String> streamCallWithTools(AgentChatRequest req, UserPrincipal user) {
        log.info("Agent流式工具调用开始: userId={}, task={}",
                user != null ? user.getUserId() : "anonymous",
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            log.warn("Agent流式调用失败：任务内容为空");
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        Long conversationId = req.getConversationId();
        Long finalConversationId;

        if (conversationId == null) {
            log.debug("创建新Agent会话（流式）: userId={}", user != null ? user.getUserId() : "anonymous");
            Conversation conversation = conversationService.createConversation(
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "agent"
            );
            finalConversationId = conversation.getId();
            log.debug("新Agent会话创建成功（流式）: conversationId={}", finalConversationId);
        } else {
            log.debug("使用已有Agent会话（流式）: conversationId={}", conversationId);
            conversationService.getConversationById(conversationId);
            finalConversationId = conversationId;
        }

        conversationService.saveMessage(finalConversationId, "user", task);
        log.debug("用户消息已保存（流式）: conversationId={}", finalConversationId);
        final Long convId = finalConversationId;

        // 采用"先同步获取完整结果，再模拟流式输出"的方式，绕过Ollama qwen模型流式工具调用时evalDuration为null的bug
        Mono<String> agentCallMono = Mono.fromCallable(() -> {
            log.debug("开始同步调用Agent获取完整结果: conversationId={}", convId);
            String response = agentClient.prompt()
                    .user(task)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "agent_" + convId))
                    .call()
                    .content();
            if (StringUtils.hasText(response)) {
                conversationService.saveMessage(convId, "assistant", response);
                log.info("Agent同步调用完成: conversationId={}, responseLength={}", convId, response.length());
            }
            return response != null ? response : "";
        }).subscribeOn(Schedulers.boundedElastic());

        return agentCallMono.flatMapMany(fullResponse -> {
            if (!StringUtils.hasText(fullResponse)) {
                return Flux.just("[ERROR] Agent返回空回复");
            }
            // 将完整回复拆分成小块，模拟流式输出效果，每30ms输出2-4个字符
            return Flux.fromArray(splitIntoChunks(fullResponse))
                    .delayElements(Duration.ofMillis(25))
                    .onErrorResume(e -> {
                        log.error("Agent流式输出异常: conversationId={}, error={}", convId, e.getMessage(), e);
                        return Flux.just("[ERROR] " + e.getMessage());
                    });
        }).onErrorResume(e -> {
            log.error("Agent调用失败: conversationId={}, error={}", convId, e.getMessage(), e);
            String errMsg = e.getMessage() != null ? e.getMessage() : "Agent调用失败";
            return Flux.just("[ERROR] " + errMsg);
        });
    }

    private String[] splitIntoChunks(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        // 按字符拆分，每2-4个字符为一块，模拟自然的打字效果
        java.util.List<String> chunks = new java.util.ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int chunkSize = 2 + (int)(Math.random() * 3);
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
            i = end;
        }
        return chunks.toArray(new String[0]);
    }

    /**
     * 旅游规划（同步模式，保留原有功能）
     * 综合使用天气查询工具和计算能力，为用户生成完整的旅游计划，
     * 包含天气建议、每日行程、费用估算和行前准备。
     *
     * @param destination 目的地城市名称
     * @param days        旅游天数
     * @return String 完整的旅游规划方案
     */
    public String planTravel(String destination, int days) {
        log.info("旅游规划开始: destination={}, days={}", destination, days);
        String prompt = "请帮我规划 " + destination + " 的 " + days + " 天旅游计划。\n"
                + "要求：\n"
                + "1. 先查询目的地天气，根据天气推荐合适的活动\n"
                + "2. 规划每天的行程安排\n"
                + "3. 估算大概费用（住宿 400元/晚，餐饮 150元/天，景点 200元/天）\n"
                + "4. 给出行前准备建议";

        String result = agentClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "travel_" + destination))
                .call()
                .content();
        log.info("旅游规划完成: destination={}, responseLength={}", destination, result.length());
        return result;
    }

    /**
     * 通用任务执行（同步模式，保留原有功能）
     * 执行任意用户指定的任务，Agent可自主判断是否需要调用工具。
     *
     * @param goal 任务目标描述
     * @return String 任务执行结果
     */
    public String executeTask(String goal) {
        log.info("通用任务执行开始: goal={}", goal != null ? goal.substring(0, Math.min(50, goal.length())) : "null");
        String result = agentClient.prompt()
                .user(goal)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "task_" + Math.abs(goal.hashCode() % 10000)))
                .call()
                .content();
        log.info("通用任务执行完成: responseLength={}", result.length());
        return result;
    }

    /**
     * 通用任务流式执行（SSE流式模式，保留原有功能）
     * 以流式方式执行任意任务，逐token输出结果。
     *
     * @param goal           任务目标描述
     * @param conversationId 会话ID，用于关联对话历史
     * @return Flux<String> SSE流式token输出
     */
    public Flux<String> streamTask(String goal, String conversationId) {
        log.info("通用任务流式执行开始: conversationId={}, goal={}",
                conversationId, goal != null ? goal.substring(0, Math.min(50, goal.length())) : "null");
        return agentClient.prompt()
                .user(goal)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doOnComplete(() -> log.info("通用任务流式执行完成: conversationId={}", conversationId))
                .doOnError(e -> log.error("通用任务流式执行失败: conversationId={}, error={}",
                        conversationId, e.getMessage(), e));
    }
}
