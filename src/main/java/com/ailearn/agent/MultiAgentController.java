package com.ailearn.agent; // 声明当前类所在的包：agent（智能体模块，单Agent与多Agent协作）

// 导入统一响应封装类Result，所有接口返回值都用它包一层
import com.ailearn.common.Result;
// 导入Agent聊天请求DTO，包含task（任务描述）和可选conversationId
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
// 导入Spring Web的REST注解集：@RestController/@RequestMapping/@PostMapping/@GetMapping/@RequestBody/@RequestParam
import org.springframework.web.bind.annotation.*;
// 导入Reactor的Flux，SSE流式响应的返回类型（0..N个元素的异步流）
import reactor.core.publisher.Flux;

// 导入HashMap，用于拼装返回给前端的键值对数据
import java.util.HashMap;
// 导入Map接口，作为返回数据的类型
import java.util.Map;

/**
 * 多智能体协作控制器
 * 提供多个Agent角色协作完成复杂任务的REST API接口。
 * 包含Planner（规划）、Researcher（研究）、Coder（编码）、Critic（审查）、Executor（执行）五种角色，
 * 支持动态路由（根据任务复杂度决定启用哪些Agent）和Critic迭代优化（代码最多3轮审查修改）。
 *
 * <p>接口列表：
 * <ul>
 *   <li>POST /api/multi-agent/execute - 多Agent协作同步执行</li>
 *   <li>GET /api/multi-agent/stream - 多Agent协作SSE流式执行（GET方式）</li>
 *   <li>POST /api/multi-agent/stream - 多Agent协作SSE流式执行（POST方式）</li>
 * </ul>
 *
 * <p>SSE流式输出为结构化JSON事件，包含type字段标识事件类型：
 * <ul>
 *   <li>agent_start：某个Agent开始工作</li>
 *   <li>token：Agent输出的文本token</li>
 *   <li>agent_end：某个Agent工作结束</li>
 *   <li>info：系统提示信息（如跳过某个Agent、审查通过等）</li>
 *   <li>error：错误信息</li>
 *   <li>done：所有Agent协作完成</li>
 * </ul>
 *
 * @author AiLearn Platform
 */
@Slf4j // Lombok注解：自动生成private static final Logger log，可直接用log.info()打日志
@RestController // Spring注解：标记这是REST控制器，返回值自动序列化为JSON（@Controller+@ResponseBody的组合）
@RequestMapping("/api/multi-agent") // 类级路径前缀：本Controller所有接口都以 /api/multi-agent 开头
@RequiredArgsConstructor // Lombok注解：为所有final字段生成构造器，Spring自动注入MultiAgentService
@Tag(name = "多智能体协作", description = "Planner/Researcher/Coder/Critic/Executor多Agent协作") // Swagger分组标签
public class MultiAgentController { // 定义多智能体协作控制器类

    /**
     * 多Agent协作服务，提供协作执行的核心业务逻辑
     */
    private final MultiAgentService multiAgentService; // 注入多Agent协作服务（final字段，由Lombok构造器注入）

    /**
     * 多Agent协作同步执行接口
     * 所有Agent按顺序执行完成后，一次性返回完整协作结果（包含各Agent的输出过程）。
     * 接口路径：POST /api/multi-agent/execute
     *
     * @param request Agent聊天请求，包含task（任务描述）和可选conversationId
     * @return Result&lt;Map&lt;String, Object&gt;&gt; 包含conversationId和完整协作结果reply
     */
    @PostMapping("/execute") // 映射POST请求到 /api/multi-agent/execute
    @Operation(summary = "多Agent协作执行（同步）", description = "Planner规划→Researcher研究→Coder编码（编程任务）→Critic审查→Executor整合，一次性返回结果") // Swagger接口描述
    public Result<Map<String, Object>> execute(@Valid @RequestBody AgentChatRequest request) { // @Valid触发参数校验，@RequestBody把JSON反序列化为AgentChatRequest
        UserPrincipal user = getCurrentUser(); // 从安全上下文获取当前登录用户
        String response = multiAgentService.collaborativeExecute(request, user); // 调用协作服务执行完整流程，返回整合后的最终答案
        Map<String, Object> data = new HashMap<>(); // 创建Map存放返回数据
        data.put("conversationId", request.getConversationId()); // 放入会话ID（前端后续对话要带上它保持上下文）
        data.put("reply", response); // 放入协作结果（包含各Agent过程输出+最终整合答案）
        return Result.success(data); // 用统一响应封装返回成功结果
    }

    /**
     * 多Agent协作SSE流式执行接口（GET方式）
     * 实时推送每个Agent的工作过程，前端可以看到Planner规划、Researcher搜索、Coder写代码、Critic审查、
     * Coder修改代码（如有）、Executor整合答案的完整过程，带来沉浸式的AI协作体验。
     * 接口路径：GET /api/multi-agent/stream
     *
     * @param task           用户任务描述，必填
     * @param conversationId 会话ID，可选
     * @return Flux&lt;String&gt; SSE事件流，每个事件是JSON格式，包含type、agent、content字段
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // 映射GET请求，produces指定响应为SSE事件流（text/event-stream）
    @Operation(summary = "多Agent协作执行（SSE流式）", description = "实时看到5个Agent协作过程，支持动态路由和Critic迭代代码审查") // Swagger接口描述
    public Flux<String> stream( // 返回Flux<String>：Reactor的异步流，Spring会把它转成SSE持续推送
            @Parameter(description = "任务描述", required = true) // Swagger参数描述
            @RequestParam String task, // 从URL查询参数获取任务描述（必填）
            @Parameter(description = "会话ID，为空时自动创建") // Swagger参数描述
            @RequestParam(required = false) Long conversationId) { // 从URL查询参数获取会话ID（可选）
        UserPrincipal user = getCurrentUser(); // 从安全上下文获取当前登录用户
        AgentChatRequest req = new AgentChatRequest(); // 手动创建请求DTO（GET方式没有请求体，要手动拼装）
        req.setTask(task); // 设置任务描述
        req.setConversationId(conversationId); // 设置会话ID
        return multiAgentService.streamCollaborativeExecute(req, user); // 调用协作服务的流式版本，返回SSE事件流
    }

    /**
     * 多Agent协作SSE流式执行接口（POST方式）
     * POST方式的SSE流式接口，参数通过JSON Body传递。
     * 接口路径：POST /api/multi-agent/stream
     *
     * @param request Agent聊天请求
     * @return Flux&lt;String&gt; SSE事件流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // 映射POST请求，同样输出SSE事件流
    @Operation(summary = "多Agent协作执行（SSE流式POST）", description = "POST方式的多Agent流式协作") // Swagger接口描述
    public Flux<String> streamPost(@Valid @RequestBody AgentChatRequest request) { // @Valid校验，@RequestBody接收JSON请求体
        UserPrincipal user = getCurrentUser(); // 从安全上下文获取当前登录用户
        return multiAgentService.streamCollaborativeExecute(request, user); // 调用协作服务的流式版本，直接返回SSE事件流
    }

    /**
     * 获取当前登录用户信息的私有辅助方法
     *
     * @return UserPrincipal 当前用户主体，未登录时返回null
     */
    private UserPrincipal getCurrentUser() { // 私有方法：从Spring Security上下文提取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // 从ThreadLocal安全上下文获取认证对象
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) { // 判空+类型检查：确保已认证且主体是我们的UserPrincipal类型
            return (UserPrincipal) authentication.getPrincipal(); // 强转为UserPrincipal返回
        }
        return null; // 未认证或类型不符时返回null
    }
}
