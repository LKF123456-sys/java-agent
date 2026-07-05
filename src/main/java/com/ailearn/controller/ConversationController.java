package com.ailearn.controller;

import com.ailearn.common.Result;
import com.ailearn.entity.ChatMessage;
import com.ailearn.entity.Conversation;
import com.ailearn.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @Operation(summary = "获取会话列表")
    public Result<List<Conversation>> listConversations(
            @Parameter(description = "会话类型: chat/memory/rag/agent/multi-agent/structured")
            @RequestParam(required = false, defaultValue = "chat") String type) {
        List<Conversation> conversations = conversationService.listConversations(type);
        return Result.success(conversations);
    }

    @PostMapping
    @Operation(summary = "创建新会话")
    public Result<Conversation> createConversation(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "新对话");
        String type = body.getOrDefault("type", "chat");
        Conversation conversation = conversationService.createConversation(title, type);
        return Result.success(conversation);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除会话")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return Result.success();
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "获取会话消息历史")
    public Result<List<ChatMessage>> getMessages(@PathVariable Long id) {
        List<ChatMessage> messages = conversationService.getMessages(id);
        return Result.success(messages);
    }
}
