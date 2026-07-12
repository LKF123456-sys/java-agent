package com.ailearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户登录请求DTO
 * 用于封装用户登录时提交的用户名和密码参数
 */
@Data
@Schema(description = "用户登录请求")
public class LoginRequest {

    /**
     * 用户名
     * 登录账号，不能为空
     */
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名（登录账号）", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    /**
     * 密码
     * 登录密码，不能为空，长度6-50位
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度6-50位")
    @Schema(description = "登录密码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
