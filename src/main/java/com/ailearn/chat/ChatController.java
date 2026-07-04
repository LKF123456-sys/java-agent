package com.ailearn.chat;

import com.ailearn.common.Result;
import com.ailearn.dto.ChatRequest;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能聊天控制器
 * 提供基础AI对话功能，包括同步消息发送、SSE流式对话、会话管理等接口
 * 普通聊天模式无上下文记忆，每次对话独立
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "智能聊天", description = "基础AI对话接口")
public class ChatController {

    /**
     * 聊天服务，提供同步和流式AI对话能力
     */
    private final ChatService chatService;

    /**
     * 会话服务，提供会话创建、查询、删除和消息管理功能
     */
    private final ConversationService conversationService;

    /**
     * 发送消息（同步模式）
     * 接收用户消息，调用AI生成回复后同步返回完整结果
     * 自动创建会话（如果conversationId为空）并保存对话历史
     * 接口路径：POST /api/chat/send
     *
     * @param request 聊天请求参数，包含消息内容和可选的会话ID，使用@Valid自动校验
     * @return Result<Map> 聊天结果，包含：
     *         - conversationId: Long 会话ID（新创建或已存在的）
     *         - reply: String AI回复内容
     */
    @PostMapping("/send")
    @Operation(summary = "发送消息（同步）", description = "发送消息到AI助手，同步等待完整回复后返回")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "发送成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（消息为空或过长）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<Map<String, Object>> send(
            @Parameter(description = "聊天请求参数", required = true)
            @Valid @RequestBody ChatRequest request) {
        log.info("收到同步聊天请求: conversationId={}, messageLength={}",
                request.getConversationId(), request.getMessage() != null ? request.getMessage().length() : 0);

        Long conversationId = request.getConversationId();
        String message = request.getMessage();
        UserPrincipal user = getCurrentUser();

        if (conversationId == null) {
            String title = message.length() > 20 ? message.substring(0, 20) + "..." : message;
            Conversation conversation = conversationService.createConversation(title, "chat");
            conversationId = conversation.getId();
            log.info("自动创建新会话: conversationId={}", conversationId);
        } else {
            conversationService.getConversationById(conversationId);
        }

        conversationService.saveMessage(conversationId, "user", message);
        String response = chatService.chat(message);
        conversationService.saveMessage(conversationId, "assistant", response);

        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("reply", response);
        return Result.success(data);
    }

    /**
     * 发送消息（SSE流式模式）
     * 使用Server-Sent Events（SSE）实时推送AI生成的token，提供打字机效果
     * SSE端点使用GET方法，因为浏览器EventSource API仅支持GET请求
     * 参数通过@RequestParam接收
     * 接口路径：GET /api/chat/stream
     *
     * @param message        用户消息内容，必填
     * @param conversationId 会话ID，可选，为空时自动创建新会话
     * @return Flux<String> SSE数据流，每个元素为SSE格式的JSON字符串
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式）", description = "使用SSE流式输出AI回复，实时推送token，提供打字机效果。注意：此接口为GET请求，参数通过URL查询参数传递")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SSE连接建立成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（消息为空）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Flux<String> stream(
            @Parameter(description = "用户消息内容", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID，为空时自动创建新会话")
            @RequestParam(required = false) Long conversationId) {
        log.info("收到流式聊天请求: conversationId={}, messageLength={}",
                conversationId, message != null ? message.length() : 0);

        Long finalConversationId = conversationId;
        if (finalConversationId == null) {
            String title = message.length() > 20 ? message.substring(0, 20) + "..." : message;
            Conversation conversation = conversationService.createConversation(title, "chat");
            finalConversationId = conversation.getId();
            log.info("流式对话自动创建新会话: conversationId={}", finalConversationId);
        }
        Long savedConversationId = finalConversationId;
        conversationService.saveMessage(savedConversationId, "user", message);

        StringBuilder fullResponse = new StringBuilder();
        return chatService.streamChat(message)
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    conversationService.saveMessage(savedConversationId, "assistant", fullResponse.toString());
                    log.info("流式对话完成: conversationId={}, replyLength={}", savedConversationId, fullResponse.length());
                });
    }

    /**
     * 获取会话列表
     * 查询指定类型的所有会话，按更新时间倒序排列
     * 接口路径：GET /api/chat/conversations
     *
     * @param type 会话类型，可选，默认为"chat"（普通聊天）
     * @return Result<List<Conversation>> 会话列表
     */
    @GetMapping("/conversations")
    @Operation(summary = "获取会话列表", description = "获取当前用户的所有聊天会话列表，按最新更新时间倒序排列")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<List<Conversation>> listConversations(
            @Parameter(description = "会话类型，默认为chat")
            @RequestParam(required = false, defaultValue = "chat") String type) {
        log.debug("获取会话列表: type={}", type);
        List<Conversation> conversations = conversationService.listConversations(type);
        return Result.success(conversations);
    }

    /**
     * 删除会话
     * 删除指定ID的会话及其关联的所有聊天消息（级联删除）
     * 接口路径：DELETE /api/chat/conversations/{id}
     *
     * @param id 要删除的会话ID，路径参数
     * @return Result<Void> 删除成功结果
     */
    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "删除会话", description = "删除指定的会话及其所有聊天消息记录")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "404", description = "会话不存在")
    })
    public Result<Void> deleteConversation(
            @Parameter(description = "会话ID", required = true)
            @PathVariable Long id) {
        log.info("删除会话: conversationId={}", id);
        conversationService.deleteConversation(id);
        return Result.success();
    }

    /**
     * 获取会话消息历史
     * 查询指定会话中的所有聊天消息，按创建时间正序排列（对话时间顺序）
     * 接口路径：GET /api/chat/conversations/{id}/messages
     *
     * @param id 会话ID，路径参数
     * @return Result<List<ChatMessage>> 消息列表
     */
    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "获取会话消息历史", description = "获取指定会话中的所有聊天消息，按时间顺序排列")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "404", description = "会话不存在")
    })
    public Result<List<ChatMessage>> getMessages(
            @Parameter(description = "会话ID", required = true)
            @PathVariable Long id) {
        log.debug("获取会话消息: conversationId={}", id);
        List<ChatMessage> messages = conversationService.getMessages(id);
        return Result.success(messages);
    }

    /**
     * 从SecurityContext获取当前登录用户信息
     * 私有辅助方法，用于在各个接口中获取当前用户
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
