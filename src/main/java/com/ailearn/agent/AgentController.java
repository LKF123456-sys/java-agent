package com.ailearn.agent;

import com.ailearn.common.Result;
import com.ailearn.dto.AgentChatRequest;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 智能体控制器
 * 提供单Agent工具调用对话功能，Agent能够自主判断并使用天气查询、
 * 数学计算等工具来完成复杂任务，支持同步和SSE流式两种调用模式
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "智能体", description = "单Agent工具调用对话（Agent自动使用天气/计算器等工具）")
public class AgentController {

    /**
     * Agent服务，提供Agent工具调用对话能力
     */
    private final AgentService agentService;

    /**
     * 会话服务，提供会话创建和消息持久化功能
     */
    private final ConversationService conversationService;

    /**
     * Agent对话（同步模式）
     * 接收用户任务请求，Agent会自主判断是否需要调用工具（如天气查询、计算器），
     * 执行完成后同步返回完整结果
     * 接口路径：POST /api/agent/chat
     *
     * @param request Agent聊天请求参数，包含任务描述和可选的会话ID，使用@Valid自动校验
     * @return Result<Map> 对话结果，包含：
     *         - conversationId: Long 会话ID（新创建或已存在的）
     *         - reply: String Agent执行后的完整回复内容
     */
    @PostMapping("/chat")
    @Operation(summary = "Agent对话（同步）", description = "与AI Agent进行对话，Agent可以自主调用天气、计算器等工具来完成任务，同步等待完整结果")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "执行成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（任务内容为空或过长）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "500", description = "Agent执行失败")
    })
    public Result<Map<String, Object>> chat(
            @Parameter(description = "Agent对话请求参数", required = true)
            @Valid @RequestBody AgentChatRequest request) {
        log.info("收到Agent同步对话请求: conversationId={}, taskLength={}",
                request.getConversationId(), request.getTask() != null ? request.getTask().length() : 0);

        String task = request.getTask();
        String convId = request.getConversationId() != null
                ? String.valueOf(request.getConversationId())
                : String.valueOf(System.currentTimeMillis());

        String response = agentService.executeTask(task);

        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", convId);
        data.put("reply", response);
        return Result.success(data);
    }

    /**
     * Agent对话（SSE流式模式）
     * 使用Server-Sent Events（SSE）实时推送Agent生成的token，
     * Agent在执行过程中会实时输出思考过程和工具调用结果（流式输出）
     * SSE端点使用GET方法，参数通过@RequestParam接收
     * 接口路径：GET /api/agent/stream
     *
     * @param task           用户任务描述，必填
     * @param conversationId 会话ID，可选，为空时自动生成
     * @return Flux<String> SSE数据流，实时推送Agent输出的token
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Agent对话（SSE流式）", description = "使用SSE流式输出Agent回复，实时推送token，可看到Agent思考和工具调用过程。注意：此接口为GET请求，参数通过URL查询参数传递")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SSE连接建立成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（任务内容为空）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Flux<String> stream(
            @Parameter(description = "任务描述", required = true)
            @RequestParam String task,
            @Parameter(description = "会话ID，为空时自动生成")
            @RequestParam(required = false) String conversationId) {
        log.info("收到Agent流式对话请求: conversationId={}, taskLength={}",
                conversationId, task != null ? task.length() : 0);

        String convId = conversationId != null ? conversationId : String.valueOf(System.currentTimeMillis());
        return agentService.streamTask(task, convId);
    }

    /**
     * 从SecurityContext获取当前登录用户信息
     * 私有辅助方法，用于在各个接口中获取当前用户
     *
     * @return UserPrincipal 当前用户主体，未登录时返回null
     */
    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
