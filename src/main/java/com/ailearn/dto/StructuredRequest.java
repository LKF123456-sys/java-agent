package com.ailearn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 结构化输出请求DTO
 * 用于封装需要AI返回结构化数据（如书籍、电影信息）的请求参数
 */
@Data
public class StructuredRequest {

    /**
     * 内容描述
     * 需要结构化提取的文本内容，不能为空，最大长度5000字
     */
    @NotBlank(message = "内容不能为空")
    @Size(max = 5000, message = "内容长度不能超过5000字")
    private String content;

    /**
     * 结构化类型
     * 指定输出的结构化数据类型，如：book(书籍信息)、movie(电影信息)
     */
    @NotBlank(message = "类型不能为空")
    private String type;
}
