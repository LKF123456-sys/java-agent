package com.ailearn.agent;

import com.ailearn.common.Result;
import com.ailearn.dto.AgentChatRequest;
import com.ailearn.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 单智能体（Agent）控制器
 * 提供具备工具调用能力的单Agent对话REST API接口。
 * Agent可自主决定调用天气查询、数学计算、联网搜索等工具来完成用户任务。
 *
 * <p>接口列表：
 * <ul>
 *   <li>POST /api/agent/chat - Agent同步对话</li>
 *   <li>GET /api/agent/stream - Agent SSE流式对话（GET方式）</li>
 *   <li>POST /api/agent/stream - Agent SSE流式对话（POST方式）</li>
 *   <li>POST /api/agent/travel-plan - 旅游规划专用接口（示例场景）</li>
 * </ul>
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "智能体", description = "单Agent工具调用对话")
public class AgentController {

    /**
     * Agent服务，提供工具调用对话的业务逻辑
     */
    private final AgentService agentService;

    /**
     * Agent同步对话接口
     * 发送任务给Agent，Agent可自主调用工具完成任务，同步返回完整结果。
     * 接口路径：POST /api/agent/chat
     *
     * @param request Agent聊天请求，包含task（任务描述）和可选的conversationId（会话ID）
     * @return Result&lt;Map&lt;String, Object&gt;&gt; 统一响应结果，包含conversationId和reply
     */
    @PostMapping("/chat")
    @Operation(summary = "Agent对话（同步）", description = "与具备工具调用能力的Agent对话，支持天气查询、计算、联网搜索等")
    public Result<Map<String, Object>> chat(@Valid @RequestBody AgentChatRequest request) {
        UserPrincipal user = getCurrentUser();
        String response = agentService.callWithTools(request, user);
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", request.getConversationId());
        data.put("reply", response);
        return Result.success(data);
    }

    /**
     * Agent SSE流式对话接口（GET方式）
     * 以Server-Sent Events方式实时推送Agent的输出，包括工具调用过程和最终回复。
     * 接口路径：GET /api/agent/stream
     *
     * @param task           用户任务描述，必填
     * @param conversationId 会话ID，可选，为空时自动创建
     * @return Flux&lt;String&gt; SSE数据流，实时推送token
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Agent对话（SSE流式）", description = "SSE流式输出Agent回复，实时看到工具调用和回答过程")
    public Flux<String> stream(
            @Parameter(description = "任务描述", required = true)
            @RequestParam String task,
            @Parameter(description = "会话ID，为空时自动创建")
            @RequestParam(required = false) Long conversationId) {
        UserPrincipal user = getCurrentUser();
        AgentChatRequest req = new AgentChatRequest();
        req.setTask(task);
        req.setConversationId(conversationId);
        return agentService.streamCallWithTools(req, user);
    }

    /**
     * Agent SSE流式对话接口（POST方式）
     * POST方式的SSE流式对话，参数通过JSON Body传递。
     * 接口路径：POST /api/agent/stream
     *
     * @param request Agent聊天请求
     * @return Flux&lt;String&gt; SSE数据流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Agent对话（SSE流式POST）", description = "POST方式的Agent SSE流式对话")
    public Flux<String> streamPost(@Valid @RequestBody AgentChatRequest request) {
        UserPrincipal user = getCurrentUser();
        return agentService.streamCallWithTools(request, user);
    }

    /**
     * 旅游规划专用接口
     * 综合使用天气查询和费用计算能力，为用户生成完整旅游计划的示例接口。
     * 接口路径：POST /api/agent/travel-plan
     *
     * @param destination 目的地城市名称
     * @param days        旅游天数
     * @return Result&lt;Map&lt;String, Object&gt;&gt; 包含目的地、天数和完整规划方案
     */
    @PostMapping("/travel-plan")
    @Operation(summary = "旅游规划", description = "Agent自动查询天气、计算费用，生成完整旅游计划")
    public Result<Map<String, Object>> travelPlan(
            @Parameter(description = "目的地城市", required = true)
            @RequestParam String destination,
            @Parameter(description = "旅游天数", required = true)
            @RequestParam int days) {
        log.info("收到旅游规划请求: destination={}, days={}", destination, days);
        String plan = agentService.planTravel(destination, days);
        Map<String, Object> data = new HashMap<>();
        data.put("destination", destination);
        data.put("days", days);
        data.put("plan", plan);
        return Result.success(data);
    }

    /**
     * 获取当前登录用户信息的私有辅助方法
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
