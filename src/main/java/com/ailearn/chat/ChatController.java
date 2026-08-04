package com.ailearn.chat; // 声明包名

import com.ailearn.common.Result; // 统一响应
import com.ailearn.dto.ChatRequest; // 聊天请求DTO
import com.ailearn.security.UserPrincipal; // 用户主体
import com.ailearn.service.ConversationService; // 会话服务
import io.swagger.v3.oas.annotations.Operation; // OpenAPI操作注解
import io.swagger.v3.oas.annotations.Parameter; // OpenAPI参数注解
import io.swagger.v3.oas.annotations.responses.ApiResponse; // OpenAPI响应注解
import io.swagger.v3.oas.annotations.responses.ApiResponses; // OpenAPI响应组
import io.swagger.v3.oas.annotations.tags.Tag; // OpenAPI分组
import jakarta.validation.Valid; // 参数校验注解
import lombok.RequiredArgsConstructor; // 构造器注入
import lombok.extern.slf4j.Slf4j; // 日志
import org.springframework.http.MediaType; // 媒体类型
import org.springframework.security.core.Authentication; // 认证接口
import org.springframework.security.core.context.SecurityContextHolder; // 安全上下文
import org.springframework.web.bind.annotation.*; // Web注解
import reactor.core.publisher.Flux; // 响应式流（SSE用）

import java.util.Map; // Map接口

/**
 * 基础聊天控制器
 * 提供基础AI对话的REST API接口，包括同步消息发送和SSE流式对话两种模式
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log
@RestController // REST控制器
@RequestMapping("/api/chat") // 根路径
@RequiredArgsConstructor // 构造器注入
@Tag(name = "智能聊天", description = "基础AI对话接口") // API分组
public class ChatController { // 聊天控制器类

    /** 聊天服务 */
    private final ChatService chatService; // 注入

    /** 会话服务（保留供扩展） */
    private final ConversationService conversationService; // 注入

    /**
     * 同步发送消息接口
     * 接口路径：POST /api/chat/send
     *
     * @param request 聊天请求参数
     * @return Result<Map> 包含conversationId和reply
     */
    @PostMapping("/send") // POST映射
    @Operation(summary = "发送消息（同步）", description = "发送消息到AI助手，同步等待完整回复后返回") // API描述
    @ApiResponses(value = { // 响应码
            @ApiResponse(responseCode = "200", description = "发送成功"), // 200
            @ApiResponse(responseCode = "400", description = "参数校验失败"), // 400
            @ApiResponse(responseCode = "401", description = "未登录或Token无效") // 401
    })
    public Result<Map<String, Object>> send( // 同步发送方法
            @Parameter(description = "聊天请求参数", required = true) // 参数描述
            @Valid @RequestBody ChatRequest request) { // @Valid校验，@RequestBody绑定JSON
        // 获取当前登录用户
        UserPrincipal user = getCurrentUser(); // 从安全上下文取用户
        // 调用聊天服务处理消息
        Map<String, Object> result = chatService.chat(request, user); // 委托Service
        return Result.success(result); // 返回成功响应
    }

    /**
     * SSE流式对话接口（GET方式）
     * 接口路径：GET /api/chat/stream
     *
     * @param message        用户消息内容
     * @param conversationId 会话ID（可选）
     * @return Flux<String> SSE数据流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // GET映射，返回SSE流
    @Operation(summary = "发送消息（SSE流式）", description = "使用SSE流式输出AI回复，实时推送token") // API描述
    public Flux<String> stream( // SSE流式方法
            @Parameter(description = "用户消息内容", required = true) // 参数描述
            @RequestParam String message, // 绑定查询参数message
            @Parameter(description = "会话ID，为空时自动创建新会话") // 参数描述
            @RequestParam(required = false) Long conversationId) { // 可选查询参数
        // 获取当前登录用户
        UserPrincipal user = getCurrentUser(); // 取用户
        // 构建请求对象
        ChatRequest req = new ChatRequest(); // 创建DTO
        req.setMessage(message); // 设置消息
        req.setConversationId(conversationId); // 设置会话ID
        // 调用流式聊天服务
        return chatService.streamChat(req, user); // 返回Flux流
    }

    /**
     * SSE流式对话接口（POST方式）
     * 接口路径：POST /api/chat/stream
     *
     * @param request 聊天请求参数
     * @return Flux<String> SSE数据流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // POST映射，返回SSE流
    @Operation(summary = "发送消息（SSE流式POST版）", description = "POST方式的SSE流式输出") // API描述
    public Flux<String> streamPost(@Valid @RequestBody ChatRequest request) { // POST版流式方法
        UserPrincipal user = getCurrentUser(); // 取用户
        return chatService.streamChat(request, user); // 返回Flux流
    }

    /**
     * 获取当前登录用户信息的私有辅助方法
     *
     * @return UserPrincipal 当前用户，未登录返回null
     */
    private UserPrincipal getCurrentUser() { // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // 取认证对象
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) { // 类型匹配
            return (UserPrincipal) authentication.getPrincipal(); // 强转返回
        }
        return null; // 未登录返回null
    }
} // ChatController类结束
