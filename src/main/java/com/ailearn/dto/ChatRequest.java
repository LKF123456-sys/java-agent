package com.ailearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 普通聊天请求DTO
 * 用于封装用户发送普通聊天消息时的请求参数
 */
@Data
@Schema(description = "基础聊天请求")
public class ChatRequest {

    /**
     * 消息内容
     * 用户发送的聊天消息，不能为空，最大长度10000字
     */
    @NotBlank(message = "消息不能为空")
    @Size(max = 10000, message = "消息长度不能超过10000字")
    @Schema(description = "用户发送的消息内容", example = "你好，请介绍一下你自己", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    /**
     * 会话ID
     * 可选，指定消息所属的会话；为空时创建新会话
     */
    @Schema(description = "会话ID（为空时自动创建新会话）", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long conversationId;
}
