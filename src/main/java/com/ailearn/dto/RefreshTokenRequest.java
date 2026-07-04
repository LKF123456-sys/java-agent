package com.ailearn.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新Token请求DTO
 * 用于封装刷新JWT令牌时提交的refreshToken参数
 */
@Data
public class RefreshTokenRequest {

    /**
     * 刷新令牌
     * 用于获取新的访问令牌，不能为空
     */
    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
