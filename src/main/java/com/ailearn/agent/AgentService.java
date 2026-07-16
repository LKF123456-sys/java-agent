package com.ailearn.agent;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.common.StreamUtils;
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

@Slf4j
@Service
@RateLimiter(name = "agentService")
public class AgentService {

    private final ChatClient agentClient;

    private final ConversationService conversationService;

    public AgentService(ChatModel chatModel,
                        DatabaseChatMemory chatMemory,
                        ConversationService conversationService,
                        ToolCallbackProvider toolCallbackProvider) {
        this.conversationService = conversationService;
        var callbacks = toolCallbackProvider.getToolCallbacks();
        this.agentClient = ChatClient.builder(chatModel)
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
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(callbacks)
                .build();
        log.info("AgentService初始化完成，已注册工具: {}", callbacks.length);
    }

    public String callWithTools(AgentChatRequest req, UserPrincipal user) {
        Long userId = user.getUserId();
        log.info("Agent同步工具调用开始: userId={}, task={}",
                userId,
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            log.warn("Agent调用失败：任务内容为空");
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        Long conversationId = req.getConversationId();
        Long finalConversationId;

        if (conversationId == null) {
            log.debug("创建新Agent会话: userId={}", userId);
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
            Conversation existing = conversationService.getConversationById(userId, conversationId);
            if (existing == null) {
                throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
            }
            finalConversationId = conversationId;
        }

        conversationService.saveMessage(userId, finalConversationId, "user", task);
        log.debug("用户消息已保存: conversationId={}", finalConversationId);

        String response;
        try {
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
            log.error("Agent调用失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, e);
        }

        if (StringUtils.hasText(response)) {
            conversationService.saveMessage(userId, finalConversationId, "assistant", response);
            log.debug("AI回复已保存: conversationId={}", finalConversationId);
        }

        return response != null ? response : "";
    }

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

        conversationService.saveMessage(userId, finalConversationId, "user", task);
        log.debug("用户消息已保存（流式）: conversationId={}", finalConversationId);
        final Long convId = finalConversationId;
        final Long uid = userId;

        StringBuilder fullReply = new StringBuilder();

        return agentClient.prompt()
                    .user(task)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "agent_" + convId))
                    .stream()
                    .content()
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        fullReply.append(chunk);
                    }
                })
                .doOnComplete(() -> {
                    String aiReply = fullReply.toString();
                    if (StringUtils.hasText(aiReply)) {
                        conversationService.saveMessage(uid, convId, "assistant", aiReply);
                        log.info("Agent流式调用完成: conversationId={}, responseLength={}", convId, aiReply.length());
                    }
                })
                .doOnError(e -> log.error("Agent流式调用错误: conversationId={}, error={}", convId, e.getMessage(), e))
                .onErrorResume(e -> {
                    log.error("Agent流式调用失败: conversationId={}, error={}", convId, e.getMessage(), e);
                    String errMsg = e.getMessage() != null ? e.getMessage() : "Agent调用失败";
                    return Flux.just("[ERROR] " + errMsg);
                });
    }

    public String planTravel(String destination, int days) {
        log.info("旅游规划开始: destination={}, days={}", destination, days);
        if (!StringUtils.hasText(destination)) {
            throw new BusinessException(ErrorCode.SYSTEM_PARAM_VALIDATION_ERROR, "目的地不能为空");
        }
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
        int len = result != null ? result.length() : 0;
        log.info("旅游规划完成: destination={}, responseLength={}", destination, len);
        return result != null ? result : "";
    }

    public String executeTask(String goal) {
        log.info("通用任务执行开始: goal={}", goal != null ? goal.substring(0, Math.min(50, goal.length())) : "null");
        if (!StringUtils.hasText(goal)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        String result = agentClient.prompt()
                .user(goal)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "task_" + Math.abs(goal.hashCode() % 10000)))
                .call()
                .content();
        int len = result != null ? result.length() : 0;
        log.info("通用任务执行完成: responseLength={}", len);
        return result != null ? result : "";
    }

    public Flux<String> streamTask(String goal, String conversationId) {
        log.info("通用任务流式执行开始: conversationId={}, goal={}",
                conversationId, goal != null ? goal.substring(0, Math.min(50, goal.length())) : "null");
        if (!StringUtils.hasText(goal)) {
            return Flux.just("[ERROR] 任务内容不能为空");
        }
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
