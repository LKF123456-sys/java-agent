package com.ailearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新Token请求DTO
 * 用于封装刷新JWT令牌时提交的refreshToken参数
 */
@Data
@Schema(description = "刷新Token请求")
public class RefreshTokenRequest {

    /**
     * 刷新令牌
     * 用于获取新的访问令牌，不能为空
     */
    @NotBlank(message = "refreshToken不能为空")
    @Schema(description = "刷新令牌（用于获取新的accessToken）", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
