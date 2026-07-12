package com.ailearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 结构化输出请求DTO
 * 用于封装需要AI返回结构化数据（如书籍、电影信息）的请求参数
 */
@Data
@Schema(description = "结构化输出请求")
public class StructuredRequest {

    /**
     * 内容描述
     * 需要结构化提取的文本内容，不能为空，最大长度5000字
     */
    @NotBlank(message = "内容不能为空")
    @Size(max = 5000, message = "内容长度不能超过5000字")
    @Schema(description = "需要结构化提取的文本内容", example = "《三体》是刘慈欣创作的科幻小说，由重庆出版社于2008年1月出版，ISBN 9787536692930", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    /**
     * 结构化类型
     * 指定输出的结构化数据类型，如：book(书籍信息)、movie(电影信息)
     */
    @NotBlank(message = "类型不能为空")
    @Schema(description = "结构化数据类型：book(书籍)、movie(电影)", example = "book", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;
}
