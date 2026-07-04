package com.ailearn.controller;

import com.ailearn.common.Result;
import com.ailearn.dto.LoginRequest;
import com.ailearn.dto.RefreshTokenRequest;
import com.ailearn.dto.RegisterRequest;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户认证控制器
 * 处理用户注册、登录、Token刷新、获取当前用户信息等认证相关接口
 * 实现双Token机制：accessToken（短期访问令牌）+ refreshToken（长期刷新令牌）
 * 登录注册接口配置为permitAll（SecurityConfig中已配置），无需认证即可访问
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "用户认证", description = "用户注册、登录、Token刷新、用户信息接口")
public class UserController {

    /**
     * 用户服务，提供注册、登录、Token刷新、用户查询等核心业务逻辑
     */
    private final UserService userService;

    /**
     * 用户注册接口
     * 接收用户注册信息，校验通过后创建新用户并返回双Token和用户信息
     * 接口路径：POST /api/auth/register
     * 权限：permitAll（无需认证即可访问）
     *
     * @param request 注册请求参数，包含用户名、密码、昵称等信息，使用@Valid自动校验参数
     * @return Result<Map> 注册成功结果，包含：
     *         - user: UserPrincipal 用户信息（不含密码）
     *         - accessToken: String 短期访问令牌
     *         - refreshToken: String 长期刷新令牌
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "新用户注册账号，成功后返回用户信息和双Token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "注册成功",
                    content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "参数校验失败（用户名格式错误、密码长度不符等）"),
            @ApiResponse(responseCode = "409", description = "用户名已存在")
    })
    public Result<Map<String, Object>> register(
            @Parameter(description = "注册请求参数", required = true)
            @Valid @RequestBody RegisterRequest request) {
        log.info("收到用户注册请求: username={}", request.getUsername());
        Map<String, Object> result = userService.register(request);
        return Result.success(result);
    }

    /**
     * 用户登录接口
     * 验证用户名和密码，登录成功后返回双Token和用户信息
     * 接口路径：POST /api/auth/login
     * 权限：permitAll（无需认证即可访问）
     *
     * @param request 登录请求参数，包含用户名和密码，使用@Valid自动校验参数
     * @return Result<Map> 登录成功结果，包含：
     *         - user: UserPrincipal 用户信息（不含密码）
     *         - accessToken: String 短期访问令牌
     *         - refreshToken: String 长期刷新令牌
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户使用用户名和密码登录，成功后返回用户信息和双Token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "登录成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败"),
            @ApiResponse(responseCode = "401", description = "用户名或密码错误")
    })
    public Result<Map<String, Object>> login(
            @Parameter(description = "登录请求参数", required = true)
            @Valid @RequestBody LoginRequest request) {
        log.info("收到用户登录请求: username={}", request.getUsername());
        Map<String, Object> result = userService.login(request);
        return Result.success(result);
    }

    /**
     * 刷新Token接口
     * 使用有效的refreshToken获取新的accessToken，用于accessToken过期后无感刷新
     * 接口路径：POST /api/auth/refresh
     * 权限：permitAll（无需认证即可访问，但需要有效的refreshToken）
     *
     * @param request 刷新Token请求参数，包含refreshToken，使用@Valid自动校验参数
     * @return Result<Map> 刷新成功结果，包含：
     *         - accessToken: String 新的访问令牌
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "使用有效的refreshToken获取新的accessToken，延长登录状态")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "刷新成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（refreshToken为空）"),
            @ApiResponse(responseCode = "401", description = "refreshToken无效或已过期")
    })
    public Result<Map<String, Object>> refreshToken(
            @Parameter(description = "刷新Token请求参数", required = true)
            @Valid @RequestBody RefreshTokenRequest request) {
        log.info("收到Token刷新请求");
        String newAccessToken = userService.refreshToken(request.getRefreshToken());
        Map<String, Object> data = Map.of("accessToken", newAccessToken);
        return Result.success(data);
    }

    /**
     * 获取当前登录用户信息接口
     * 从Spring Security的SecurityContext中获取当前认证用户的信息
     * 接口路径：GET /api/auth/me
     * 权限：需要认证（携带有效的accessToken）
     *
     * @return Result<UserPrincipal> 当前用户信息（不含密码）
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "获取当前已登录用户的详细信息，需要在请求头中携带有效的accessToken")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<UserPrincipal> getCurrentUser() {
        log.debug("获取当前用户信息请求");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = null;
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            principal = (UserPrincipal) authentication.getPrincipal();
        }
        return Result.success(principal);
    }
}
