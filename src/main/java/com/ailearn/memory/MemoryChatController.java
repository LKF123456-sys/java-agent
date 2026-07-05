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

/**
 * 记忆对话REST控制器
 * 提供带持久化记忆的多轮对话HTTP接口，包括同步发送消息、SSE流式对话、
 * 会话管理（创建、列表、删除）和消息历史查询等功能
 * 所有接口路径以/api/memory开头
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
@Tag(name = "记忆对话", description = "带持久化记忆的多轮对话接口")
public class MemoryChatController {

    /**
     * 记忆聊天服务
     * 提供同步和流式对话、记忆清除等核心业务逻辑
     */
    private final MemoryChatService memoryChatService;

    /**
     * 会话服务
     * 提供会话CRUD和消息管理功能
     */
    private final ConversationService conversationService;

    /**
     * 发送消息（同步方式）
     * POST接口，接收JSON格式的聊天请求，等待AI完整回复后返回结果
     *
     * @param request 聊天请求体，包含会话ID（可选）和消息内容
     * @return Result&lt;Map&lt;String, Object&gt;&gt; 统一响应结果，包含conversationId和reply字段
     */
    @PostMapping("/send")
    @Operation(summary = "发送消息（同步）")
    public Result<Map<String, Object>> send(@Valid @RequestBody MemoryChatRequest request) {
        // 获取当前登录用户信息
        UserPrincipal user = getCurrentUser();
        // 调用服务执行同步对话
        Map<String, Object> result = memoryChatService.chat(request, user);
        return Result.success(result);
    }

    /**
     * 发送消息（SSE流式GET方式）
     * GET接口，通过URL参数传递消息，以Server-Sent Events方式实时返回AI回复
     * 适用于简单场景的流式对话
     *
     * @param message        用户消息内容（必填）
     * @param conversationId 会话ID（可选，不传则自动创建新会话）
     * @return Flux&lt;String&gt; SSE响应流，实时推送AI生成的文本片段
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式）")
    public Flux<String> stream(
            @RequestParam String message,
            @RequestParam(required = false) Long conversationId) {
        // 获取当前登录用户信息
        UserPrincipal user = getCurrentUser();
        // 构建请求对象
        MemoryChatRequest req = new MemoryChatRequest();
        req.setMessage(message);
        req.setConversationId(conversationId);
        // 调用服务执行流式对话
        return memoryChatService.streamChat(req, user);
    }

    /**
     * 发送消息（SSE流式POST方式）
     * POST接口，接收JSON格式请求体，以Server-Sent Events方式实时返回AI回复
     * 适用于需要传递复杂参数的流式对话场景
     *
     * @param request 聊天请求体，包含会话ID（可选）和消息内容
     * @return Flux&lt;String&gt; SSE响应流，实时推送AI生成的文本片段
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式POST）")
    public Flux<String> streamPost(@Valid @RequestBody MemoryChatRequest request) {
        // 获取当前登录用户信息
        UserPrincipal user = getCurrentUser();
        // 调用服务执行流式对话
        return memoryChatService.streamChat(request, user);
    }

    /**
     * 获取会话列表
     * GET接口，查询指定类型的所有会话，按更新时间倒序排列
     *
     * @param type 会话类型，默认为"memory"（记忆对话）
     * @return Result&lt;List&lt;Conversation&gt;&gt; 统一响应结果，包含会话列表
     */
    @GetMapping("/conversations")
    @Operation(summary = "获取会话列表")
    public Result<List<Conversation>> listConversations(
            @RequestParam(required = false, defaultValue = "memory") String type) {
        return Result.success(conversationService.listConversations(type));
    }

    /**
     * 创建新会话
     * POST接口，创建一个新的空对话会话
     *
     * @param body 请求体，包含title字段（可选，默认为"新对话"）
     * @return Result&lt;Conversation&gt; 统一响应结果，包含新创建的会话信息
     */
    @PostMapping("/conversations")
    @Operation(summary = "创建新会话")
    public Result<Conversation> createConversation(@RequestBody Map<String, String> body) {
        // 从请求体获取标题，默认使用"新对话"
        String title = body.getOrDefault("title", "新对话");
        return Result.success(conversationService.createConversation(title, "memory"));
    }

    /**
     * 删除会话
     * DELETE接口，删除指定会话及其关联的所有消息，同时清除记忆
     *
     * @param id 要删除的会话ID（路径参数）
     * @return Result&lt;Void&gt; 统一响应结果
     */
    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "删除会话")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        // 先清除会话记忆
        memoryChatService.clearMemory(id);
        // 再删除会话及其消息
        conversationService.deleteConversation(id);
        return Result.success();
    }

    /**
     * 获取会话消息历史
     * GET接口，查询指定会话的所有聊天消息，按时间正序排列
     *
     * @param id 会话ID（路径参数）
     * @return Result&lt;List&lt;ChatMessage&gt;&gt; 统一响应结果，包含该会话的所有消息列表
     */
    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "获取会话消息历史")
    public Result<List<ChatMessage>> getMessages(@PathVariable Long id) {
        return Result.success(conversationService.getMessages(id));
    }

    /**
     * 获取当前登录用户信息
     * 从Spring Security上下文获取认证信息，提取UserPrincipal
     *
     * @return UserPrincipal 当前用户认证主体，未登录则返回null
     */
    private UserPrincipal getCurrentUser() {
        // 从SecurityContextHolder获取认证对象
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 验证认证对象存在且主体是UserPrincipal类型
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
