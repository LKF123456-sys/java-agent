package com.ailearn.agent; // 声明包名

import com.ailearn.common.Result; // 统一响应
import com.ailearn.dto.AgentChatRequest; // Agent请求DTO
import com.ailearn.security.UserPrincipal; // 用户主体
import io.swagger.v3.oas.annotations.Operation; // OpenAPI操作注解
import io.swagger.v3.oas.annotations.Parameter; // OpenAPI参数注解
import io.swagger.v3.oas.annotations.tags.Tag; // OpenAPI分组
import jakarta.validation.Valid; // 参数校验
import lombok.RequiredArgsConstructor; // 构造器注入
import lombok.extern.slf4j.Slf4j; // 日志
import org.springframework.http.MediaType; // 媒体类型
import org.springframework.security.core.Authentication; // 认证接口
import org.springframework.security.core.context.SecurityContextHolder; // 安全上下文
import org.springframework.web.bind.annotation.*; // Web注解
import reactor.core.publisher.Flux; // 响应式流

import java.util.HashMap; // 哈希表
import java.util.Map; // Map接口

/**
 * 单智能体（Agent）控制器
 * 提供具备工具调用能力的单Agent对话REST API接口
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log
@RestController // REST控制器
@RequestMapping("/api/agent") // 根路径
@RequiredArgsConstructor // 构造器注入
@Tag(name = "智能体", description = "单Agent工具调用对话") // API分组
public class AgentController { // Agent控制器

    /** Agent服务 */
    private final AgentService agentService; // 注入

    /**
     * Agent同步对话接口
     * 接口路径：POST /api/agent/chat
     *
     * @param request Agent请求
     * @return Result<Map> 包含conversationId和reply
     */
    @PostMapping("/chat") // POST映射
    @Operation(summary = "Agent对话（同步）", description = "与具备工具调用能力的Agent对话") // API描述
    public Result<Map<String, Object>> chat(@Valid @RequestBody AgentChatRequest request) { // 同步对话方法
        UserPrincipal user = getCurrentUser(); // 取当前用户
        String response = agentService.callWithTools(request, user); // 调用Agent服务（含工具调用）
        Map<String, Object> data = new HashMap<>(); // 结果Map
        data.put("conversationId", request.getConversationId()); // 会话ID
        data.put("reply", response); // Agent回复
        return Result.success(data); // 返回
    }

    /**
     * Agent SSE流式对话接口（GET方式）
     * 接口路径：GET /api/agent/stream
     *
     * @param task           任务描述
     * @param conversationId 会话ID（可选）
     * @return Flux<String> SSE数据流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // GET映射，SSE流
    @Operation(summary = "Agent对话（SSE流式）", description = "SSE流式输出Agent回复") // API描述
    public Flux<String> stream( // SSE流式方法
            @Parameter(description = "任务描述", required = true) // 参数描述
            @RequestParam String task, // 绑定task参数
            @Parameter(description = "会话ID，为空时自动创建") // 参数描述
            @RequestParam(required = false) Long conversationId) { // 可选会话ID
        UserPrincipal user = getCurrentUser(); // 取用户
        AgentChatRequest req = new AgentChatRequest(); // 创建DTO
        req.setTask(task); // 设置任务
        req.setConversationId(conversationId); // 设置会话ID
        return agentService.streamCallWithTools(req, user); // 返回流式结果
    }

    /**
     * Agent SSE流式对话接口（POST方式）
     * 接口路径：POST /api/agent/stream
     *
     * @param request Agent请求
     * @return Flux<String> SSE数据流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // POST映射，SSE流
    @Operation(summary = "Agent对话（SSE流式POST）", description = "POST方式的Agent SSE流式对话") // API描述
    public Flux<String> streamPost(@Valid @RequestBody AgentChatRequest request) { // POST版流式
        UserPrincipal user = getCurrentUser(); // 取用户
        return agentService.streamCallWithTools(request, user); // 返回流式结果
    }

    /**
     * 旅游规划专用接口
     * 接口路径：POST /api/agent/travel-plan
     *
     * @param destination 目的地城市
     * @param days        旅游天数
     * @return Result<Map> 包含目的地、天数和规划方案
     */
    @PostMapping("/travel-plan") // POST映射
    @Operation(summary = "旅游规划", description = "Agent自动查询天气、计算费用，生成完整旅游计划") // API描述
    public Result<Map<String, Object>> travelPlan( // 旅游规划方法
            @Parameter(description = "目的地城市", required = true) // 参数描述
            @RequestParam String destination, // 目的地
            @Parameter(description = "旅游天数", required = true) // 参数描述
            @RequestParam int days) { // 天数
        log.info("收到旅游规划请求: destination={}, days={}", destination, days); // 业务日志
        String plan = agentService.planTravel(destination, days); // 调用Agent生成规划
        Map<String, Object> data = new HashMap<>(); // 结果Map
        data.put("destination", destination); // 目的地
        data.put("days", days); // 天数
        data.put("plan", plan); // 规划方案
        return Result.success(data); // 返回
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
} // AgentController类结束
