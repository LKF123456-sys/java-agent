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
@RequestMapping("/api/multi-agent")
@RequiredArgsConstructor
@Tag(name = "多智能体协作", description = "Planner/Researcher/Coder/Critic/Executor多Agent协作")
public class MultiAgentController {

    private final MultiAgentService multiAgentService;

    @PostMapping("/execute")
    @Operation(summary = "多Agent协作执行（同步）")
    public Result<Map<String, Object>> execute(@Valid @RequestBody AgentChatRequest request) {
        UserPrincipal user = getCurrentUser();
        String response = multiAgentService.collaborativeExecute(request, user);
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", request.getConversationId());
        data.put("reply", response);
        return Result.success(data);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "多Agent协作执行（SSE流式）")
    public Flux<String> stream(
            @RequestParam String task,
            @RequestParam(required = false) Long conversationId) {
        UserPrincipal user = getCurrentUser();
        AgentChatRequest req = new AgentChatRequest();
        req.setTask(task);
        req.setConversationId(conversationId);
        return multiAgentService.streamCollaborativeExecute(req, user);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "多Agent协作执行（SSE流式POST）")
    public Flux<String> streamPost(@Valid @RequestBody AgentChatRequest request) {
        UserPrincipal user = getCurrentUser();
        return multiAgentService.streamCollaborativeExecute(request, user);
    }

    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
