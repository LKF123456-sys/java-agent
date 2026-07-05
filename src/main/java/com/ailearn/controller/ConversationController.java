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

/**
 * 会话管理控制器
 * 处理AI对话会话的CRUD（创建、读取、更新、删除）操作，支持多种会话类型
 * 所有接口都需要认证（携带有效的accessToken），仅能访问当前登录用户自己的会话
 *
 * <p>支持的会话类型：
 * <ul>
 *   <li><b>chat</b>：基础AI聊天对话</li>
 *   <li><b>memory</b>：带持久化记忆的多轮对话</li>
 *   <li><b>rag</b>：RAG知识库检索增强问答对话</li>
 *   <li><b>agent</b>：单智能体工具调用对话</li>
 *   <li><b>multi-agent</b>：多智能体协作对话</li>
 *   <li><b>structured</b>：结构化输出提取对话</li>
 * </ul>
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "会话管理", description = "统一的会话CRUD接口")
public class ConversationController {

    /**
     * 会话服务，提供会话的创建、查询、删除及消息历史查询等核心业务逻辑
     */
    private final ConversationService conversationService;

    /**
     * 获取会话列表接口
     * 根据会话类型查询当前登录用户的所有会话
     * 接口路径：GET /api/conversations
     * 权限：需要认证（携带有效的accessToken）
     *
     * @param type 会话类型筛选参数，可选值：chat/memory/rag/agent/multi-agent/structured，默认为chat
     * @return Result&lt;List&lt;Conversation&gt;&gt; 会话列表，包含会话ID、标题、类型、创建时间、更新时间等信息
     */
    @GetMapping
    @Operation(summary = "获取会话列表")
    public Result<List<Conversation>> listConversations(
            @Parameter(description = "会话类型: chat/memory/rag/agent/multi-agent/structured")
            @RequestParam(required = false, defaultValue = "chat") String type) {
        List<Conversation> conversations = conversationService.listConversations(type);
        return Result.success(conversations);
    }

    /**
     * 创建新会话接口
     * 为当前登录用户创建一个新的AI对话会话，可指定会话标题和类型
     * 接口路径：POST /api/conversations
     * 权限：需要认证（携带有效的accessToken）
     *
     * @param body 创建会话请求体，包含：
     *             - title: String 会话标题，默认为"新对话"
     *             - type: String 会话类型，默认为"chat"
     * @return Result&lt;Conversation&gt; 创建成功的会话信息，包含自动生成的会话ID
     */
    @PostMapping
    @Operation(summary = "创建新会话")
    public Result<Conversation> createConversation(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "新对话");
        String type = body.getOrDefault("type", "chat");
        Conversation conversation = conversationService.createConversation(title, type);
        return Result.success(conversation);
    }

    /**
     * 删除会话接口
     * 根据会话ID删除指定会话，同时级联删除该会话下的所有消息记录
     * 接口路径：DELETE /api/conversations/{id}
     * 权限：需要认证（携带有效的accessToken），且只能删除自己的会话
     *
     * @param id 要删除的会话ID，路径参数
     * @return Result&lt;Void&gt; 删除成功的空响应
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除会话")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return Result.success();
    }

    /**
     * 获取会话消息历史接口
     * 查询指定会话下的所有聊天消息记录，按时间顺序排列
     * 接口路径：GET /api/conversations/{id}/messages
     * 权限：需要认证（携带有效的accessToken），且只能访问自己的会话消息
     *
     * @param id 会话ID，路径参数
     * @return Result&lt;List&lt;ChatMessage&gt;&gt; 消息列表，包含每条消息的角色（user/assistant）、内容、时间戳等
     */
    @GetMapping("/{id}/messages")
    @Operation(summary = "获取会话消息历史")
    public Result<List<ChatMessage>> getMessages(@PathVariable Long id) {
        List<ChatMessage> messages = conversationService.getMessages(id);
        return Result.success(messages);
    }
}
