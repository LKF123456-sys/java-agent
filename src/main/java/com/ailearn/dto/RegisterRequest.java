package com.ailearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求DTO
 * 用于封装用户注册时提交的账号信息
 */
@Data
@Schema(description = "用户注册请求")
public class RegisterRequest {

    /**
     * 用户名
     * 注册账号，不能为空，3-20位字母数字下划线
     */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "用户名3-20位字母数字下划线")
    @Schema(description = "用户名（3-20位字母数字下划线）", example = "user001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    /**
     * 密码
     * 注册密码，不能为空，长度6-50位
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度6-50位")
    @Schema(description = "密码（6-50位）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    /**
     * 用户角色
     * 可选，默认为user(普通用户)
     */
    @Schema(description = "用户角色", example = "user", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String role = "user";

    /**
     * 用户昵称
     * 用户显示名称
     */
    @Schema(description = "用户昵称", example = "测试用户", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String nickname;
}
