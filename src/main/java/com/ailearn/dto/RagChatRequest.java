package com.ailearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RAG检索增强问答请求DTO
 * 用于封装基于知识库检索增强的问答请求参数
 */
@Data
@Schema(description = "RAG知识库问答请求")
public class RagChatRequest {

    /**
     * 问题内容
     * 用户提出的问题，不能为空，最大长度10000字
     */
    @NotBlank(message = "问题不能为空")
    @Size(max = 10000, message = "问题长度不能超过10000字")
    @Schema(description = "用户提出的问题（基于知识库回答）", example = "文档中提到的Spring AI支持哪些向量数据库？", requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;

    /**
     * 会话ID
     * 可选，指定问题所属的会话；为空时创建新会话
     */
    @Schema(description = "会话ID（为空时自动创建新会话）", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long conversationId;
}
