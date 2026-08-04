package com.ailearn.agent; // 声明当前类所在的包：agent（智能体模块）

// 导入统一响应封装类Result，所有接口返回值都用它包一层
import com.ailearn.common.Result;
// 导入Agent聊天请求DTO，包含task（问题）和可选conversationId
import com.ailearn.dto.AgentChatRequest;
// 导入自定义用户主体类，封装当前登录用户的ID、用户名等信息
import com.ailearn.security.UserPrincipal;
// 导入Swagger注解：@Operation描述单个接口、@Parameter描述参数、@Tag描述Controller分组
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
// 导入Spring Validation的@Valid注解，触发请求参数的JSR-303校验
import jakarta.validation.Valid;
// 导入Lombok的@RequiredArgsConstructor，自动生成final字段的构造器（替代@Autowired）
import lombok.RequiredArgsConstructor;
// 导入Lombok的@Slf4j，自动生成log日志对象
import lombok.extern.slf4j.Slf4j;
// 导入MediaType，用于指定SSE流式响应的Content-Type为text/event-stream
import org.springframework.http.MediaType;
// 导入Spring Security的Authentication接口，代表当前已认证的用户信息
import org.springframework.security.core.Authentication;
// 导入SecurityContextHolder，从ThreadLocal中获取当前请求的安全上下文
import org.springframework.security.core.context.SecurityContextHolder;
// 导入Spring Web的REST注解集
import org.springframework.web.bind.annotation.*;
// 导入Reactor的Flux，SSE流式响应的返回类型（0..N个元素的异步流）
import reactor.core.publisher.Flux;

// 导入HashMap，用于拼装返回给前端的键值对数据
import java.util.HashMap;
// 导入Map接口，作为返回数据的类型
import java.util.Map;

/**
 * 联网搜索智能体控制器
 * 提供具备自动联网搜索能力的Agent REST API接口。
 * 该Agent会先自动搜索互联网获取实时信息，再基于搜索结果给出准确回答，支持来源标注。
 *
 * <p>接口列表：
 * <ul>
 *   <li>POST /api/search-agent/chat - 搜索Agent同步对话</li>
 *   <li>GET /api/search-agent/stream - 搜索Agent SSE流式对话（GET方式）</li>
 *   <li>POST /api/search-agent/stream - 搜索Agent SSE流式对话（POST方式）</li>
 * </ul>
 *
 * @author AiLearn Platform
 */
@Slf4j // Lombok注解：自动生成log日志对象
@RestController // 标记为REST控制器，返回值自动序列化为JSON
@RequestMapping("/api/search-agent") // 类级路径前缀：所有接口以 /api/search-agent 开头
@RequiredArgsConstructor // Lombok为final字段生成构造器，Spring自动注入SearchAgentService
@Tag(name = "联网搜索Agent", description = "具备联网搜索能力的智能体，可实时搜索互联网获取最新信息") // Swagger分组
public class SearchAgentController { // 定义联网搜索智能体控制器类

    /**
     * 联网搜索Agent服务，提供先搜索后总结的问答业务逻辑
     */
    private final SearchAgentService searchAgentService; // 注入联网搜索Agent服务（final字段，Lombok构造器注入）

    /**
     * 搜索Agent同步对话接口
     * 发送问题后，系统自动联网搜索，然后基于搜索结果生成回答，同步返回完整结果。
     * 接口路径：POST /api/search-agent/chat
     *
     * @param request Agent聊天请求，包含task（问题）和可选conversationId
     * @return Result&lt;Map&lt;String, Object&gt;&gt; 包含conversationId和reply的统一响应
     */
    @PostMapping("/chat") // 映射POST请求到 /api/search-agent/chat
    @Operation(summary = "搜索Agent对话（同步）", description = "自动联网搜索后基于搜索结果回答，支持来源标注") // Swagger描述
    public Result<Map<String, Object>> chat(@Valid @RequestBody AgentChatRequest request) { // @Valid校验，@RequestBody接收JSON
        UserPrincipal user = getCurrentUser(); // 从安全上下文获取当前登录用户
        String response = searchAgentService.callWithSearch(request, user); // 调用搜索服务：先联网搜索，再基于结果生成回答
        Map<String, Object> data = new HashMap<>(); // 创建Map存放返回数据
        data.put("conversationId", request.getConversationId()); // 放入会话ID
        data.put("reply", response); // 放入回答内容
        return Result.success(data); // 统一封装返回成功
    }

    /**
     * 搜索Agent SSE流式对话接口（GET方式）
     * 实时推送搜索状态和回答token，用户可以看到"正在搜索"的提示以及后续的回答生成过程。
     * 接口路径：GET /api/search-agent/stream
     *
     * @param task           用户问题，必填
     * @param conversationId 会话ID，可选
     * @return Flux&lt;String&gt; SSE数据流，包含搜索状态提示和回答token
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // 映射GET请求，输出SSE事件流
    @Operation(summary = "搜索Agent对话（SSE流式GET）", description = "实时显示搜索状态和回答过程，带来源标注") // Swagger描述
    public Flux<String> stream( // 返回Flux<String>：SSE持续推送的异步流
            @Parameter(description = "搜索问题", required = true) // Swagger参数描述
            @RequestParam String task, // 从URL查询参数获取问题（必填）
            @Parameter(description = "会话ID，为空时自动创建") // Swagger参数描述
            @RequestParam(required = false) Long conversationId) { // 从URL查询参数获取会话ID（可选）
        UserPrincipal user = getCurrentUser(); // 从安全上下文获取当前登录用户
        AgentChatRequest req = new AgentChatRequest(); // 手动创建请求DTO（GET方式无请求体）
        req.setTask(task); // 设置问题
        req.setConversationId(conversationId); // 设置会话ID
        return searchAgentService.streamCallWithSearch(req, user); // 调用搜索服务的流式版本，返回SSE事件流
    }

    /**
     * 搜索Agent SSE流式对话接口（POST方式）
     * POST方式的SSE流式接口，参数通过JSON Body传递。
     * 接口路径：POST /api/search-agent/stream
     *
     * @param request Agent聊天请求
     * @return Flux&lt;String&gt; SSE数据流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // 映射POST请求，输出SSE事件流
    @Operation(summary = "搜索Agent对话（SSE流式POST）", description = "POST方式的搜索Agent流式对话") // Swagger描述
    public Flux<String> streamPost(@Valid @RequestBody AgentChatRequest request) { // @Valid校验，@RequestBody接收JSON
        UserPrincipal user = getCurrentUser(); // 从安全上下文获取当前登录用户
        return searchAgentService.streamCallWithSearch(request, user); // 调用搜索服务的流式版本，直接返回SSE事件流
    }

    /**
     * 获取当前登录用户信息的私有辅助方法
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
