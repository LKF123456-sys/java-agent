package com.ailearn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RAG检索增强问答请求DTO
 * 用于封装基于知识库检索增强的问答请求参数
 */
@Data
public class RagChatRequest {

    /**
     * 问题内容
     * 用户提出的问题，不能为空，最大长度10000字
     */
    @NotBlank(message = "问题不能为空")
    @Size(max = 10000, message = "问题长度不能超过10000字")
    private String question;

    /**
     * 会话ID
     * 可选，指定问题所属的会话；为空时创建新会话
     */
    private Long conversationId;
}
