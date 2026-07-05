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

/**
 * 基础聊天控制器
 * 提供基础AI对话的REST API接口，包括同步消息发送和SSE流式对话两种模式。
 * 所有接口都需要用户认证，通过Spring Security获取当前登录用户信息。
 *
 * <p>接口列表：
 * <ul>
 *   <li>POST /api/chat/send - 同步发送消息，等待完整回复</li>
 *   <li>GET /api/chat/stream - SSE流式对话（GET方式，参数通过URL传递）</li>
 *   <li>POST /api/chat/stream - SSE流式对话（POST方式，参数通过Body传递）</li>
 * </ul>
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
     * 聊天服务，提供同步和流式对话的业务逻辑
     */
    private final ChatService chatService;

    /**
     * 会话服务，用于会话管理（此处注入但当前未直接使用，保留供扩展）
     */
    private final ConversationService conversationService;

    /**
     * 同步发送消息接口
     * 发送消息到AI助手，同步等待AI完整回复后返回结果。
     * 接口路径：POST /api/chat/send
     *
     * @param request 聊天请求参数，包含message（消息内容）和可选的conversationId（会话ID）
     * @return Result&lt;Map&lt;String, Object&gt;&gt; 统一响应结果，包含conversationId和reply字段
     */
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
        // 获取当前登录用户
        UserPrincipal user = getCurrentUser();
        // 调用聊天服务处理消息
        Map<String, Object> result = chatService.chat(request, user);
        return Result.success(result);
    }

    /**
     * SSE流式对话接口（GET方式）
     * 使用Server-Sent Events流式输出AI回复，实时推送token，提供打字机效果。
     * 参数通过URL查询参数传递，适合EventSource等简单客户端接入。
     * 接口路径：GET /api/chat/stream
     *
     * @param message        用户消息内容，必填
     * @param conversationId 会话ID，可选，为空时自动创建新会话
     * @return Flux&lt;String&gt; SSE数据流，每个元素是AI生成的一个token
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式）", description = "使用SSE流式输出AI回复，实时推送token")
    public Flux<String> stream(
            @Parameter(description = "用户消息内容", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID，为空时自动创建新会话")
            @RequestParam(required = false) Long conversationId) {
        // 获取当前登录用户
        UserPrincipal user = getCurrentUser();
        // 构建请求对象
        ChatRequest req = new ChatRequest();
        req.setMessage(message);
        req.setConversationId(conversationId);
        // 调用流式聊天服务
        return chatService.streamChat(req, user);
    }

    /**
     * SSE流式对话接口（POST方式）
     * 使用POST方式的SSE流式输出，参数通过JSON Body传递，适合复杂参数场景。
     * 接口路径：POST /api/chat/stream
     *
     * @param request 聊天请求参数，包含message和可选conversationId
     * @return Flux&lt;String&gt; SSE数据流，实时推送token
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式POST版）", description = "POST方式的SSE流式输出")
    public Flux<String> streamPost(@Valid @RequestBody ChatRequest request) {
        UserPrincipal user = getCurrentUser();
        return chatService.streamChat(request, user);
    }

    /**
     * 获取当前登录用户信息的私有辅助方法
     * 从Spring Security的SecurityContextHolder中提取认证信息，
     * 并转换为UserPrincipal对象。
     *
     * @return UserPrincipal 当前登录用户主体，未登录时返回null
     */
    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
