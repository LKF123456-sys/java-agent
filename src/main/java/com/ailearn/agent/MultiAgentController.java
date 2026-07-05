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
 * 多智能体协作控制器
 * 提供多个Agent角色协作完成复杂任务的REST API接口。
 * 包含Planner（规划）、Researcher（研究）、Coder（编码）、Critic（审查）、Executor（执行）五种角色，
 * 支持动态路由（根据任务复杂度决定启用哪些Agent）和Critic迭代优化（代码最多3轮审查修改）。
 *
 * <p>接口列表：
 * <ul>
 *   <li>POST /api/multi-agent/execute - 多Agent协作同步执行</li>
 *   <li>GET /api/multi-agent/stream - 多Agent协作SSE流式执行（GET方式）</li>
 *   <li>POST /api/multi-agent/stream - 多Agent协作SSE流式执行（POST方式）</li>
 * </ul>
 *
 * <p>SSE流式输出为结构化JSON事件，包含type字段标识事件类型：
 * <ul>
 *   <li>agent_start：某个Agent开始工作</li>
 *   <li>token：Agent输出的文本token</li>
 *   <li>agent_end：某个Agent工作结束</li>
 *   <li>info：系统提示信息（如跳过某个Agent、审查通过等）</li>
 *   <li>error：错误信息</li>
 *   <li>done：所有Agent协作完成</li>
 * </ul>
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/multi-agent")
@RequiredArgsConstructor
@Tag(name = "多智能体协作", description = "Planner/Researcher/Coder/Critic/Executor多Agent协作")
public class MultiAgentController {

    /**
     * 多Agent协作服务，提供协作执行的核心业务逻辑
     */
    private final MultiAgentService multiAgentService;

    /**
     * 多Agent协作同步执行接口
     * 所有Agent按顺序执行完成后，一次性返回完整协作结果（包含各Agent的输出过程）。
     * 接口路径：POST /api/multi-agent/execute
     *
     * @param request Agent聊天请求，包含task（任务描述）和可选conversationId
     * @return Result&lt;Map&lt;String, Object&gt;&gt; 包含conversationId和完整协作结果reply
     */
    @PostMapping("/execute")
    @Operation(summary = "多Agent协作执行（同步）", description = "Planner规划→Researcher研究→Coder编码（编程任务）→Critic审查→Executor整合，一次性返回结果")
    public Result<Map<String, Object>> execute(@Valid @RequestBody AgentChatRequest request) {
        UserPrincipal user = getCurrentUser();
        String response = multiAgentService.collaborativeExecute(request, user);
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", request.getConversationId());
        data.put("reply", response);
        return Result.success(data);
    }

    /**
     * 多Agent协作SSE流式执行接口（GET方式）
     * 实时推送每个Agent的工作过程，前端可以看到Planner规划、Researcher搜索、Coder写代码、Critic审查、
     * Coder修改代码（如有）、Executor整合答案的完整过程，带来沉浸式的AI协作体验。
     * 接口路径：GET /api/multi-agent/stream
     *
     * @param task           用户任务描述，必填
     * @param conversationId 会话ID，可选
     * @return Flux&lt;String&gt; SSE事件流，每个事件是JSON格式，包含type、agent、content字段
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "多Agent协作执行（SSE流式）", description = "实时看到5个Agent协作过程，支持动态路由和Critic迭代代码审查")
    public Flux<String> stream(
            @Parameter(description = "任务描述", required = true)
            @RequestParam String task,
            @Parameter(description = "会话ID，为空时自动创建")
            @RequestParam(required = false) Long conversationId) {
        UserPrincipal user = getCurrentUser();
        AgentChatRequest req = new AgentChatRequest();
        req.setTask(task);
        req.setConversationId(conversationId);
        return multiAgentService.streamCollaborativeExecute(req, user);
    }

    /**
     * 多Agent协作SSE流式执行接口（POST方式）
     * POST方式的SSE流式接口，参数通过JSON Body传递。
     * 接口路径：POST /api/multi-agent/stream
     *
     * @param request Agent聊天请求
     * @return Flux&lt;String&gt; SSE事件流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "多Agent协作执行（SSE流式POST）", description = "POST方式的多Agent流式协作")
    public Flux<String> streamPost(@Valid @RequestBody AgentChatRequest request) {
        UserPrincipal user = getCurrentUser();
        return multiAgentService.streamCollaborativeExecute(request, user);
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
