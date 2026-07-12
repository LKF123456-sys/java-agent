package com.ailearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent智能体聊天请求DTO
 * 用于封装用户发送Agent任务请求时的参数
 */
@Data
@Schema(description = "Agent智能体聊天请求")
public class AgentChatRequest {

    /**
     * 任务描述
     * 需要Agent执行的任务内容，不能为空，最大长度5000字
     */
    @NotBlank(message = "任务描述不能为空")
    @Size(max = 5000, message = "任务描述不能超过5000字")
    @Schema(description = "需要Agent执行的任务描述", example = "帮我查询北京今天的天气", requiredMode = Schema.RequiredMode.REQUIRED)
    private String task;

    /**
     * 会话ID
     * 可选，指定任务所属的会话；为空时创建新会话
     */
    @Schema(description = "会话ID（为空时自动创建新会话）", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long conversationId;
}
