package com.ailearn.memory;

import com.ailearn.common.Result;
import com.ailearn.dto.MemoryChatRequest;
import com.ailearn.entity.ChatMessage;
import com.ailearn.entity.Conversation;
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

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
@Tag(name = "记忆对话", description = "带持久化记忆的多轮对话接口")
public class MemoryChatController {

    private final MemoryChatService memoryChatService;
    private final ConversationService conversationService;

    @PostMapping("/send")
    @Operation(summary = "发送消息（同步）")
    public Result<Map<String, Object>> send(@Valid @RequestBody MemoryChatRequest request) {
        UserPrincipal user = getCurrentUser();
        Map<String, Object> result = memoryChatService.chat(request, user);
        return Result.success(result);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式）")
    public Flux<String> stream(
            @RequestParam String message,
            @RequestParam(required = false) Long conversationId) {
        UserPrincipal user = getCurrentUser();
        MemoryChatRequest req = new MemoryChatRequest();
        req.setMessage(message);
        req.setConversationId(conversationId);
        return memoryChatService.streamChat(req, user);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式POST）")
    public Flux<String> streamPost(@Valid @RequestBody MemoryChatRequest request) {
        UserPrincipal user = getCurrentUser();
        return memoryChatService.streamChat(request, user);
    }

    @GetMapping("/conversations")
    @Operation(summary = "获取会话列表")
    public Result<List<Conversation>> listConversations(
            @RequestParam(required = false, defaultValue = "memory") String type) {
        return Result.success(conversationService.listConversations(type));
    }

    @PostMapping("/conversations")
    @Operation(summary = "创建新会话")
    public Result<Conversation> createConversation(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "新对话");
        return Result.success(conversationService.createConversation(title, "memory"));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "删除会话")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        memoryChatService.clearMemory(id);
        conversationService.deleteConversation(id);
        return Result.success();
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "获取会话消息历史")
    public Result<List<ChatMessage>> getMessages(@PathVariable Long id) {
        return Result.success(conversationService.getMessages(id));
    }

    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
