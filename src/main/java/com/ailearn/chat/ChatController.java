package com.ailearn.chat;

import com.ailearn.common.Result;
import com.ailearn.dto.ChatRequest;
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

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "智能聊天", description = "基础AI对话接口")
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;

    @PostMapping("/send")
    @Operation(summary = "发送消息（同步）", description = "发送消息到AI助手，同步等待完整回复后返回")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "发送成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<Map<String, Object>> send(
            @Parameter(description = "聊天请求参数", required = true)
            @Valid @RequestBody ChatRequest request) {
        UserPrincipal user = getCurrentUser();
        Map<String, Object> result = chatService.chat(request, user);
        return Result.success(result);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式）", description = "使用SSE流式输出AI回复，实时推送token")
    public Flux<String> stream(
            @Parameter(description = "用户消息内容", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID，为空时自动创建新会话")
            @RequestParam(required = false) Long conversationId) {
        UserPrincipal user = getCurrentUser();
        ChatRequest req = new ChatRequest();
        req.setMessage(message);
        req.setConversationId(conversationId);
        return chatService.streamChat(req, user);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式POST版）", description = "POST方式的SSE流式输出")
    public Flux<String> streamPost(@Valid @RequestBody ChatRequest request) {
        UserPrincipal user = getCurrentUser();
        return chatService.streamChat(request, user);
    }

    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
