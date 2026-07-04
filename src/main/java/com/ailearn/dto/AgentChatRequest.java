package com.ailearn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent智能体聊天请求DTO
 * 用于封装用户发送Agent任务请求时的参数
 */
@Data
public class AgentChatRequest {

    /**
     * 任务描述
     * 需要Agent执行的任务内容，不能为空，最大长度5000字
     */
    @NotBlank(message = "任务描述不能为空")
    @Size(max = 5000, message = "任务描述不能超过5000字")
    private String task;

    /**
     * 会话ID
     * 可选，指定任务所属的会话；为空时创建新会话
     */
    private Long conversationId;
}
