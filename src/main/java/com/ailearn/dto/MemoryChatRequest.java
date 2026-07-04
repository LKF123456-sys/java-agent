package com.ailearn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 记忆聊天请求DTO
 * 用于封装带记忆功能的聊天请求参数
 */
@Data
public class MemoryChatRequest {

    /**
     * 消息内容
     * 用户发送的聊天消息，不能为空，最大长度10000字
     */
    @NotBlank(message = "消息不能为空")
    @Size(max = 10000, message = "消息长度不能超过10000字")
    private String message;

    /**
     * 会话ID
     * 可选，指定消息所属的会话；为空时创建新会话
     */
    private Long conversationId;
}
