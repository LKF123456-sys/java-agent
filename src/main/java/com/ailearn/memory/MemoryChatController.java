package com.ailearn.memory; // 声明当前类所在的包：memory（记忆对话模块）

// 导入统一响应结果封装类，所有接口返回值都用它包一层
import com.ailearn.common.Result;
// 导入记忆对话请求DTO，包含message和可选conversationId
import com.ailearn.dto.MemoryChatRequest;
// 导入聊天消息实体类，对应数据库的消息表
import com.ailearn.entity.ChatMessage;
// 导入会话实体类，对应数据库的会话表
import com.ailearn.entity.Conversation;
// 导入用户安全主体，封装当前登录用户信息
import com.ailearn.security.UserPrincipal;
// 导入会话管理服务，管理会话的创建、查询和删除
import com.ailearn.service.ConversationService;
// 导入Swagger注解：@Operation描述接口、@Parameter描述参数、@ApiResponse(s)描述响应、@Tag分组
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
// 导入Jakarta参数校验注解，触发JSR-303校验
import jakarta.validation.Valid;
// 导入Lombok注解：自动生成构造器和日志对象
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// 导入SSE媒体类型常量，指定流式响应的Content-Type为text/event-stream
import org.springframework.http.MediaType;
// 导入Spring Security认证相关类，从安全上下文获取当前用户
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
// 导入Spring MVC的REST注解集
import org.springframework.web.bind.annotation.*;
// 导入Reactor Flux响应式流，SSE流式响应的返回类型
import reactor.core.publisher.Flux;

// 导入List集合，返回多条数据时用
import java.util.List;
// 导入Map集合，返回键值对数据时用
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
@Slf4j // Lombok注解：自动生成log日志对象
@RestController // 标记为REST控制器，返回值自动序列化为JSON
@RequestMapping("/api/memory") // 类级路径前缀：所有接口以 /api/memory 开头
@RequiredArgsConstructor // Lombok为final字段生成构造器，Spring自动注入两个Service
@Tag(name = "记忆对话", description = "带持久化记忆的多轮对话接口") // Swagger分组
public class MemoryChatController { // 定义记忆对话控制器类

    /**
     * 记忆对话服务，提供带上下文记忆的多轮对话业务逻辑
     */
    private final MemoryChatService memoryChatService; // 注入记忆对话服务（对话逻辑）

    /**
     * 会话管理服务，管理会话的创建、查询和删除
     */
    private final ConversationService conversationService; // 注入会话管理服务（会话CRUD）

    /**
     * 带记忆的同步对话接口
     * AI会基于对话历史提供连贯回答，记住之前的对话内容
     * 接口路径：POST /api/memory/send
     *
     * @param request 记忆聊天请求，包含message和可选conversationId
     * @return Result&lt;Map&gt; 包含conversationId和AI回复reply
     */
    @PostMapping("/send") // 映射POST请求到 /api/memory/send
    @Operation(summary = "发送消息（同步）", description = "带长期记忆的同步对话，AI会记住之前的对话内容") // Swagger描述
    public Result<Map<String, Object>> send(@Valid @RequestBody MemoryChatRequest request) { // @Valid校验，@RequestBody接收JSON
        // 获取当前登录用户
        UserPrincipal user = getCurrentUser(); // 从安全上下文提取用户
        // 调用记忆对话服务（Service返回的Map已包含conversationId和reply）
        Map<String, Object> result = memoryChatService.chat(request, user);
        return Result.success(result); // 统一封装返回
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
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // 映射GET请求，输出SSE事件流
    @Operation(summary = "发送消息（SSE流式）", description = "带长期记忆的SSE流式对话，实时推送AI回复") // Swagger描述
    public Flux<String> stream( // 返回Flux<String>：SSE持续推送的异步流
            @Parameter(description = "用户消息内容", required = true) // Swagger参数描述
            @RequestParam String message, // 从URL查询参数获取消息（必填）
            @Parameter(description = "会话ID，为空时自动创建新会话") // Swagger参数描述
            @RequestParam(required = false) Long conversationId) { // 从URL查询参数获取会话ID（可选）
        UserPrincipal user = getCurrentUser(); // 获取当前登录用户
        // 将GET参数封装为请求DTO（GET方式没有请求体，要手动拼装）
        MemoryChatRequest req = new MemoryChatRequest(); // 创建空的请求对象
        req.setMessage(message); // 设置消息内容
        req.setConversationId(conversationId); // 设置会话ID
        return memoryChatService.streamChat(req, user); // 调用流式对话服务，返回SSE事件流
    }

    /**
     * SSE流式对话接口（POST方式）
     * POST方式的流式对话，参数通过JSON Body传递
     * 接口路径：POST /api/memory/stream
     *
     * @param request 记忆聊天请求
     * @return Flux&lt;String&gt; SSE数据流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // 映射POST请求，输出SSE事件流
    @Operation(summary = "发送消息（SSE流式POST）", description = "POST方式的带记忆SSE流式对话") // Swagger描述
    public Flux<String> streamPost(@Valid @RequestBody MemoryChatRequest request) { // @Valid校验，@RequestBody接收JSON
        UserPrincipal user = getCurrentUser(); // 获取当前登录用户
        return memoryChatService.streamChat(request, user); // 调用流式对话服务，直接返回SSE事件流
    }

    /**
     * 获取会话列表接口
     * 返回当前用户指定类型的会话列表
     * 接口路径：GET /api/memory/conversations
     *
     * @param type 会话类型，默认memory
     * @return Result&lt;List&lt;Conversation&gt;&gt; 会话列表
     */
    @GetMapping("/conversations") // 映射GET请求到 /api/memory/conversations
    @Operation(summary = "获取会话列表", description = "获取记忆对话类型的会话列表") // Swagger描述
    public Result<List<Conversation>> listConversations( // 返回会话实体列表
            @Parameter(description = "会话类型，默认memory") // Swagger参数描述
            @RequestParam(required = false, defaultValue = "memory") String type) { // 可选参数，默认查memory类型会话
        UserPrincipal user = getCurrentUser(); // 获取当前登录用户
        return Result.success(conversationService.listConversations(user.getUserId(), type)); // 按用户ID+类型查询会话列表并返回
    }

    /**
     * 创建新会话接口
     * 创建一个新的记忆对话会话
     * 接口路径：POST /api/memory/conversations
     *
     * @param body 请求体JSON，包含可选的title字段
     * @return Result&lt;Conversation&gt; 新创建的会话
     */
    @PostMapping("/conversations") // 映射POST请求到 /api/memory/conversations
    @Operation(summary = "创建新会话", description = "创建一个新的记忆对话会话") // Swagger描述
    public Result<Conversation> createConversation(@RequestBody Map<String, String> body) { // 用Map接收简单JSON请求体（只有title一个字段，不值得建DTO）
        UserPrincipal user = getCurrentUser(); // 获取当前登录用户
        // 从请求体获取标题，默认为"新对话"（getOrDefault：key不存在时返回默认值）
        String title = body.getOrDefault("title", "新对话");
        return Result.success(conversationService.createConversation(user.getUserId(), title, "memory")); // 创建memory类型会话并返回
    }

    /**
     * 删除会话接口
     * 删除指定会话并清除相关记忆数据
     * 接口路径：DELETE /api/memory/conversations/{id}
     *
     * @param id 要删除的会话ID
     * @return Result&lt;Void&gt; 成功响应
     */
    @DeleteMapping("/conversations/{id}") // 映射DELETE请求，{id}是路径变量占位符
    @Operation(summary = "删除会话", description = "删除记忆对话会话并清除相关记忆") // Swagger描述
    public Result<Void> deleteConversation( // 返回Void表示无业务数据，只返回操作成功状态
            @Parameter(description = "会话ID", required = true) // Swagger参数描述
            @PathVariable Long id) { // @PathVariable把URL路径中的{id}绑定到方法参数
        UserPrincipal user = getCurrentUser(); // 获取当前登录用户
        // 先清除聊天记忆（ChatMemory里的历史）
        memoryChatService.clearMemory(user.getUserId(), id);
        // 再删除会话及其关联数据（数据库里的会话和消息记录）
        conversationService.deleteConversation(user.getUserId(), id);
        return Result.success(); // 返回成功（无数据）
    }

    /**
     * 获取会话历史消息接口
     * 返回指定会话的所有历史聊天记录
     * 接口路径：GET /api/memory/conversations/{id}/messages
     *
     * @param id 会话ID
     * @return Result&lt;List&lt;ChatMessage&gt;&gt; 历史消息列表
     */
    @GetMapping("/conversations/{id}/messages") // 映射GET请求到嵌套路径
    @Operation(summary = "获取会话消息历史", description = "获取记忆对话的历史消息记录") // Swagger描述
    public Result<List<ChatMessage>> getMessages( // 返回消息实体列表
            @Parameter(description = "会话ID", required = true) // Swagger参数描述
            @PathVariable Long id) { // 从路径中取会话ID
        UserPrincipal user = getCurrentUser(); // 获取当前登录用户
        return Result.success(conversationService.getMessages(user.getUserId(), id)); // 按用户ID+会话ID查询历史消息（带归属校验）
    }

    /**
     * 获取当前登录用户的私有辅助方法
     *
     * @return UserPrincipal 当前用户主体，未登录时返回null
     */
    private UserPrincipal getCurrentUser() { // 私有方法：从Spring Security上下文提取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // 从ThreadLocal安全上下文获取认证对象
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) { // 判空+类型检查
            return (UserPrincipal) authentication.getPrincipal(); // 强转为UserPrincipal返回
        }
        return null; // 未认证时返回null
    }
}
