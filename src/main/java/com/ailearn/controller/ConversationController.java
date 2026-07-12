package com.ailearn.controller;

import com.ailearn.common.Result;
import com.ailearn.entity.ChatMessage;
import com.ailearn.entity.Conversation;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "会话管理", description = "统一的会话CRUD接口")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @Operation(summary = "获取会话列表", description = "获取当前用户的所有会话列表，可按类型筛选")
    public Result<List<Conversation>> listConversations(
            @Parameter(description = "会话类型: chat/memory/rag/agent/multi-agent/structured")
            @RequestParam(required = false, defaultValue = "chat") String type) {
        UserPrincipal user = getCurrentUser();
        List<Conversation> conversations = conversationService.listConversations(user.getUserId(), type);
        return Result.success(conversations);
    }

    @PostMapping
    @Operation(summary = "创建新会话", description = "创建一个新的聊天会话，指定标题和会话类型")
    public Result<Conversation> createConversation(@RequestBody Map<String, String> body) {
        UserPrincipal user = getCurrentUser();
        String title = body.getOrDefault("title", "新对话");
        String type = body.getOrDefault("type", "chat");
        Conversation conversation = conversationService.createConversation(user.getUserId(), title, type);
        return Result.success(conversation);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除会话", description = "删除指定ID的会话及其所有消息")
    public Result<Void> deleteConversation(
            @Parameter(description = "会话ID", required = true)
            @PathVariable Long id) {
        UserPrincipal user = getCurrentUser();
        conversationService.deleteConversation(user.getUserId(), id);
        return Result.success();
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "获取会话消息历史", description = "获取指定会话的所有历史消息记录")
    public Result<List<ChatMessage>> getMessages(
            @Parameter(description = "会话ID", required = true)
            @PathVariable Long id) {
        UserPrincipal user = getCurrentUser();
        List<ChatMessage> messages = conversationService.getMessages(user.getUserId(), id);
        return Result.success(messages);
    }

    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
