package com.ailearn.agent;

import com.ailearn.common.Result;
import com.ailearn.dto.AgentChatRequest;
import com.ailearn.security.UserPrincipal;
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
import java.util.Map;

/**
 * 多智能体协作控制器
 * 提供多Agent协作完成复杂任务的功能，包含以下角色：
 * - Planner（规划专家）：分析需求，分解任务步骤
 * - Researcher（研究员）：收集信息，调用工具获取数据
 * - Coder（编程专家）：生成代码（编程任务时启用）
 * - Critic（审查专家）：评估输出质量，提出改进建议
 * - Executor（执行专家）：整合所有结果，给出最终答案
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/multi-agent")
@RequiredArgsConstructor
@Tag(name = "多智能体协作", description = "Planner/Researcher/Coder/Critic/Executor多Agent协作完成复杂任务")
public class MultiAgentController {

    /**
     * 多Agent服务，提供多智能体协作执行能力
     */
    private final MultiAgentService multiAgentService;

    /**
     * 多Agent协作执行（同步模式）
     * 接收复杂任务，多个Agent按顺序协作完成，最后返回完整执行结果
     * 执行流程：Planner规划 → Researcher研究 → Coder编码（编程任务）→ Critic审查 → Executor整合
     * 接口路径：POST /api/multi-agent/execute
     *
     * @param request Agent聊天请求参数，包含任务描述和可选的会话ID，使用@Valid自动校验
     * @return Result<Map> 执行结果，包含：
     *         - conversationId: String 会话ID
     *         - reply: String 多Agent协作完成后的最终结果
     */
    @PostMapping("/execute")
    @Operation(summary = "多Agent协作执行（同步）", description = "多个智能体（Planner/Researcher/Coder/Critic/Executor）协作完成复杂任务，同步等待最终结果")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "执行成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（任务内容为空或过长）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "500", description = "多Agent协作执行失败")
    })
    public Result<Map<String, Object>> execute(
            @Parameter(description = "多Agent执行请求参数", required = true)
            @Valid @RequestBody AgentChatRequest request) {
        log.info("收到多Agent协作执行请求: conversationId={}, taskLength={}",
                request.getConversationId(), request.getTask() != null ? request.getTask().length() : 0);

        String task = request.getTask();
        String convId = request.getConversationId() != null
                ? String.valueOf(request.getConversationId())
                : String.valueOf(System.currentTimeMillis());

        String response = multiAgentService.collaborativeExecute(task, convId);

        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", convId);
        data.put("reply", response);
        return Result.success(data);
    }

    /**
     * 多Agent协作执行（SSE流式模式）
     * 使用Server-Sent Events（SSE）实时推送每个Agent的执行过程和输出token，
     * 可以看到每个Agent的启动、工作和完成状态，实现Token级实时输出
     * SSE端点使用GET方法，参数通过@RequestParam接收
     * 接口路径：GET /api/multi-agent/stream
     *
     * @param task           用户任务描述，必填
     * @param conversationId 会话ID，可选，为空时自动生成
     * @return Flux<String> SSE数据流，包含各Agent的实时输出和状态事件
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "多Agent协作执行（SSE流式，Token级实时输出）", description = "使用SSE流式输出多Agent协作过程，实时推送每个Agent的思考和输出token，可看到完整协作流程。注意：此接口为GET请求，参数通过URL查询参数传递")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SSE连接建立成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（任务内容为空）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Flux<String> stream(
            @Parameter(description = "任务描述", required = true)
            @RequestParam String task,
            @Parameter(description = "会话ID，为空时自动生成")
            @RequestParam(required = false) String conversationId) {
        log.info("收到多Agent流式协作请求: conversationId={}, taskLength={}",
                conversationId, task != null ? task.length() : 0);

        String convId = conversationId != null ? conversationId : String.valueOf(System.currentTimeMillis());
        return multiAgentService.streamCollaborativeExecute(task, convId);
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
