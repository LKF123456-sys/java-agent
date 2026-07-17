package com.ailearn.memory;

// 导入统一响应结果封装类
import com.ailearn.common.Result;
// 导入记忆对话请求DTO
import com.ailearn.dto.MemoryChatRequest;
// 导入聊天消息实体类
import com.ailearn.entity.ChatMessage;
// 导入会话实体类
import com.ailearn.entity.Conversation;
// 导入用户安全主体
import com.ailearn.security.UserPrincipal;
// 导入会话管理服务
import com.ailearn.service.ConversationService;
// 导入Swagger注解
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
// 导入Jakarta参数校验注解
import jakarta.validation.Valid;
// 导入Lombok注解
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// 导入SSE媒体类型常量
import org.springframework.http.MediaType;
// 导入Spring Security认证相关类
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
// 导入Spring MVC注解
import org.springframework.web.bind.annotation.*;
// 导入Reactor Flux响应式流
import reactor.core.publisher.Flux;

// 导入List集合
import java.util.List;
// 导入Map集合
import java.util.Map;

/**
 * 带持久化记忆的多轮对话控制器
 * 提供基于数据库聊天记忆的多轮对话REST API接口。
 * AI能够记住对话中的所有历史上下文，实现连贯的多轮交互体验。
 *
 * <p>接口列表：
 * <ul>
 *   <li>POST /api/memory/send - 同步对话（带记忆）</li>
 *   <li>GET /api/memory/stream - SSE流式对话（GET方式）</li>
 *   <li>POST /api/memory/stream - SSE流式对话（POST方式）</li>
 *   <li>GET /api/memory/conversations - 获取会话列表</li>
 *   <li>POST /api/memory/conversations - 创建新会话</li>
 *   <li>DELETE /api/memory/conversations/{id} - 删除会话并清除记忆</li>
 *   <li>GET /api/memory/conversations/{id}/messages - 获取历史消息</li>
 * </ul>
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
     * 记忆对话服务，提供带上下文记忆的多轮对话业务逻辑
     */
    private final MemoryChatService memoryChatService;

    /**
     * 会话管理服务，管理会话的创建、查询和删除
     */
    private final ConversationService conversationService;

    /**
     * 带记忆的同步对话接口
     * AI会基于对话历史提供连贯回答，记住之前的对话内容
     * 接口路径：POST /api/memory/send
     *
     * @param request 记忆聊天请求，包含message和可选conversationId
     * @return Result&lt;Map&gt; 包含conversationId和AI回复reply
     */
    @PostMapping("/send")
    @Operation(summary = "发送消息（同步）", description = "带长期记忆的同步对话，AI会记住之前的对话内容")
    public Result<Map<String, Object>> send(@Valid @RequestBody MemoryChatRequest request) {
        // 获取当前登录用户
        UserPrincipal user = getCurrentUser();
        // 调用记忆对话服务
        Map<String, Object> result = memoryChatService.chat(request, user);
        return Result.success(result);
    }

    /**
     * SSE流式对话接口（GET方式）
     * 实时推送带记忆的AI回复token，提供打字机效果
     * 接口路径：GET /api/memory/stream
     *
     * @param message        用户消息内容，必填
     * @param conversationId 会话ID，为空时自动创建新会话
     * @return Flux&lt;String&gt; SSE数据流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式）", description = "带长期记忆的SSE流式对话，实时推送AI回复")
    public Flux<String> stream(
            @Parameter(description = "用户消息内容", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID，为空时自动创建新会话")
            @RequestParam(required = false) Long conversationId) {
        UserPrincipal user = getCurrentUser();
        // 将GET参数封装为请求DTO
        MemoryChatRequest req = new MemoryChatRequest();
        req.setMessage(message);
        req.setConversationId(conversationId);
        return memoryChatService.streamChat(req, user);
    }

    /**
     * SSE流式对话接口（POST方式）
     * POST方式的流式对话，参数通过JSON Body传递
     * 接口路径：POST /api/memory/stream
     *
     * @param request 记忆聊天请求
     * @return Flux&lt;String&gt; SSE数据流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式POST）", description = "POST方式的带记忆SSE流式对话")
    public Flux<String> streamPost(@Valid @RequestBody MemoryChatRequest request) {
        UserPrincipal user = getCurrentUser();
        return memoryChatService.streamChat(request, user);
    }

    /**
     * 获取会话列表接口
     * 返回当前用户指定类型的会话列表
     * 接口路径：GET /api/memory/conversations
     *
     * @param type 会话类型，默认memory
     * @return Result&lt;List&lt;Conversation&gt;&gt; 会话列表
     */
    @GetMapping("/conversations")
    @Operation(summary = "获取会话列表", description = "获取记忆对话类型的会话列表")
    public Result<List<Conversation>> listConversations(
            @Parameter(description = "会话类型，默认memory")
            @RequestParam(required = false, defaultValue = "memory") String type) {
        UserPrincipal user = getCurrentUser();
        return Result.success(conversationService.listConversations(user.getUserId(), type));
    }

    /**
     * 创建新会话接口
     * 创建一个新的记忆对话会话
     * 接口路径：POST /api/memory/conversations
     *
     * @param body 请求体JSON，包含可选的title字段
     * @return Result&lt;Conversation&gt; 新创建的会话
     */
    @PostMapping("/conversations")
    @Operation(summary = "创建新会话", description = "创建一个新的记忆对话会话")
    public Result<Conversation> createConversation(@RequestBody Map<String, String> body) {
        UserPrincipal user = getCurrentUser();
        // 从请求体获取标题，默认为"新对话"
        String title = body.getOrDefault("title", "新对话");
        return Result.success(conversationService.createConversation(user.getUserId(), title, "memory"));
    }

    /**
     * 删除会话接口
     * 删除指定会话并清除相关记忆数据
     * 接口路径：DELETE /api/memory/conversations/{id}
     *
     * @param id 要删除的会话ID
     * @return Result&lt;Void&gt; 成功响应
     */
    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "删除会话", description = "删除记忆对话会话并清除相关记忆")
    public Result<Void> deleteConversation(
            @Parameter(description = "会话ID", required = true)
            @PathVariable Long id) {
        UserPrincipal user = getCurrentUser();
        // 先清除聊天记忆
        memoryChatService.clearMemory(user.getUserId(), id);
        // 再删除会话及其关联数据
        conversationService.deleteConversation(user.getUserId(), id);
        return Result.success();
    }

    /**
     * 获取会话历史消息接口
     * 返回指定会话的所有历史聊天记录
     * 接口路径：GET /api/memory/conversations/{id}/messages
     *
     * @param id 会话ID
     * @return Result&lt;List&lt;ChatMessage&gt;&gt; 历史消息列表
     */
    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "获取会话消息历史", description = "获取记忆对话的历史消息记录")
    public Result<List<ChatMessage>> getMessages(
            @Parameter(description = "会话ID", required = true)
            @PathVariable Long id) {
        UserPrincipal user = getCurrentUser();
        return Result.success(conversationService.getMessages(user.getUserId(), id));
    }

    /**
     * 获取当前登录用户的私有辅助方法
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
