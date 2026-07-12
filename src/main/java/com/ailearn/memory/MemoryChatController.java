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
    @Operation(summary = "发送消息（同步）", description = "带长期记忆的同步对话，AI会记住之前的对话内容")
    public Result<Map<String, Object>> send(@Valid @RequestBody MemoryChatRequest request) {
        UserPrincipal user = getCurrentUser();
        Map<String, Object> result = memoryChatService.chat(request, user);
        return Result.success(result);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式）", description = "带长期记忆的SSE流式对话，实时推送AI回复")
    public Flux<String> stream(
            @Parameter(description = "用户消息内容", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID，为空时自动创建新会话")
            @RequestParam(required = false) Long conversationId) {
        UserPrincipal user = getCurrentUser();
        MemoryChatRequest req = new MemoryChatRequest();
        req.setMessage(message);
        req.setConversationId(conversationId);
        return memoryChatService.streamChat(req, user);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式POST）", description = "POST方式的带记忆SSE流式对话")
    public Flux<String> streamPost(@Valid @RequestBody MemoryChatRequest request) {
        UserPrincipal user = getCurrentUser();
        return memoryChatService.streamChat(request, user);
    }

    @GetMapping("/conversations")
    @Operation(summary = "获取会话列表", description = "获取记忆对话类型的会话列表")
    public Result<List<Conversation>> listConversations(
            @Parameter(description = "会话类型，默认memory")
            @RequestParam(required = false, defaultValue = "memory") String type) {
        UserPrincipal user = getCurrentUser();
        return Result.success(conversationService.listConversations(user.getUserId(), type));
    }

    @PostMapping("/conversations")
    @Operation(summary = "创建新会话", description = "创建一个新的记忆对话会话")
    public Result<Conversation> createConversation(@RequestBody Map<String, String> body) {
        UserPrincipal user = getCurrentUser();
        String title = body.getOrDefault("title", "新对话");
        return Result.success(conversationService.createConversation(user.getUserId(), title, "memory"));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "删除会话", description = "删除记忆对话会话并清除相关记忆")
    public Result<Void> deleteConversation(
            @Parameter(description = "会话ID", required = true)
            @PathVariable Long id) {
        UserPrincipal user = getCurrentUser();
        memoryChatService.clearMemory(user.getUserId(), id);
        conversationService.deleteConversation(user.getUserId(), id);
        return Result.success();
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "获取会话消息历史", description = "获取记忆对话的历史消息记录")
    public Result<List<ChatMessage>> getMessages(
            @Parameter(description = "会话ID", required = true)
            @PathVariable Long id) {
        UserPrincipal user = getCurrentUser();
        return Result.success(conversationService.getMessages(user.getUserId(), id));
    }

    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
