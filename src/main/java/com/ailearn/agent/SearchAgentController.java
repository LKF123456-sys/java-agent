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
 * 联网搜索智能体控制器
 * 提供具备自动联网搜索能力的Agent REST API接口。
 * 该Agent会先自动搜索互联网获取实时信息，再基于搜索结果给出准确回答，支持来源标注。
 *
 * <p>接口列表：
 * <ul>
 *   <li>POST /api/search-agent/chat - 搜索Agent同步对话</li>
 *   <li>GET /api/search-agent/stream - 搜索Agent SSE流式对话（GET方式）</li>
 *   <li>POST /api/search-agent/stream - 搜索Agent SSE流式对话（POST方式）</li>
 * </ul>
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/search-agent")
@RequiredArgsConstructor
@Tag(name = "联网搜索Agent", description = "具备联网搜索能力的智能体，可实时搜索互联网获取最新信息")
public class SearchAgentController {

    /**
     * 联网搜索Agent服务，提供先搜索后总结的问答业务逻辑
     */
    private final SearchAgentService searchAgentService;

    /**
     * 搜索Agent同步对话接口
     * 发送问题后，系统自动联网搜索，然后基于搜索结果生成回答，同步返回完整结果。
     * 接口路径：POST /api/search-agent/chat
     *
     * @param request Agent聊天请求，包含task（问题）和可选conversationId
     * @return Result&lt;Map&lt;String, Object&gt;&gt; 包含conversationId和reply的统一响应
     */
    @PostMapping("/chat")
    @Operation(summary = "搜索Agent对话（同步）", description = "自动联网搜索后基于搜索结果回答，支持来源标注")
    public Result<Map<String, Object>> chat(@Valid @RequestBody AgentChatRequest request) {
        UserPrincipal user = getCurrentUser();
        String response = searchAgentService.callWithSearch(request, user);
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", request.getConversationId());
        data.put("reply", response);
        return Result.success(data);
    }

    /**
     * 搜索Agent SSE流式对话接口（GET方式）
     * 实时推送搜索状态和回答token，用户可以看到"正在搜索"的提示以及后续的回答生成过程。
     * 接口路径：GET /api/search-agent/stream
     *
     * @param task           用户问题，必填
     * @param conversationId 会话ID，可选
     * @return Flux&lt;String&gt; SSE数据流，包含搜索状态提示和回答token
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "搜索Agent对话（SSE流式GET）", description = "实时显示搜索状态和回答过程，带来源标注")
    public Flux<String> stream(
            @Parameter(description = "搜索问题", required = true)
            @RequestParam String task,
            @Parameter(description = "会话ID，为空时自动创建")
            @RequestParam(required = false) Long conversationId) {
        UserPrincipal user = getCurrentUser();
        AgentChatRequest req = new AgentChatRequest();
        req.setTask(task);
        req.setConversationId(conversationId);
        return searchAgentService.streamCallWithSearch(req, user);
    }

    /**
     * 搜索Agent SSE流式对话接口（POST方式）
     * POST方式的SSE流式接口，参数通过JSON Body传递。
     * 接口路径：POST /api/search-agent/stream
     *
     * @param request Agent聊天请求
     * @return Flux&lt;String&gt; SSE数据流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "搜索Agent对话（SSE流式POST）", description = "POST方式的搜索Agent流式对话")
    public Flux<String> streamPost(@Valid @RequestBody AgentChatRequest request) {
        UserPrincipal user = getCurrentUser();
        return searchAgentService.streamCallWithSearch(request, user);
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
