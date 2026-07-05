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

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "智能体", description = "单Agent工具调用对话")
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")
    @Operation(summary = "Agent对话（同步）")
    public Result<Map<String, Object>> chat(@Valid @RequestBody AgentChatRequest request) {
        UserPrincipal user = getCurrentUser();
        String response = agentService.callWithTools(request, user);
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", request.getConversationId());
        data.put("reply", response);
        return Result.success(data);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Agent对话（SSE流式）")
    public Flux<String> stream(
            @RequestParam String task,
            @RequestParam(required = false) Long conversationId) {
        UserPrincipal user = getCurrentUser();
        AgentChatRequest req = new AgentChatRequest();
        req.setTask(task);
        req.setConversationId(conversationId);
        return agentService.streamCallWithTools(req, user);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Agent对话（SSE流式POST）")
    public Flux<String> streamPost(@Valid @RequestBody AgentChatRequest request) {
        UserPrincipal user = getCurrentUser();
        return agentService.streamCallWithTools(request, user);
    }

    @PostMapping("/travel-plan")
    @Operation(summary = "旅游规划")
    public Result<Map<String, Object>> travelPlan(
            @RequestParam String destination,
            @RequestParam int days) {
        log.info("收到旅游规划请求: destination={}, days={}", destination, days);
        String plan = agentService.planTravel(destination, days);
        Map<String, Object> data = new HashMap<>();
        data.put("destination", destination);
        data.put("days", days);
        data.put("plan", plan);
        return Result.success(data);
    }

    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
