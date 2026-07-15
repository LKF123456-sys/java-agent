package com.ailearn.controller; // 声明包名，controller包存放所有REST控制器类

import com.ailearn.common.Result; // 统一响应结果封装类，用于标准化API返回格式
import com.ailearn.dto.LoginRequest; // 登录请求DTO，封装登录接口的请求参数
import com.ailearn.dto.RefreshTokenRequest; // 刷新Token请求DTO，封装刷新Token接口的请求参数
import com.ailearn.dto.RegisterRequest; // 注册请求DTO，封装注册接口的请求参数
import com.ailearn.security.UserPrincipal; // 用户主体类，实现UserDetails接口，封装当前登录用户信息
import com.ailearn.service.UserService; // 用户服务类，提供注册、登录、Token刷新等核心业务逻辑
import io.swagger.v3.oas.annotations.Operation; // OpenAPI注解，用于描述API操作的摘要和详细信息
import io.swagger.v3.oas.annotations.Parameter; // OpenAPI注解，用于描述API参数信息
import io.swagger.v3.oas.annotations.media.Content; // OpenAPI注解，用于描述响应内容类型
import io.swagger.v3.oas.annotations.media.Schema; // OpenAPI注解，用于描述数据模型结构
import io.swagger.v3.oas.annotations.responses.ApiResponse; // OpenAPI注解，用于描述单个API响应
import io.swagger.v3.oas.annotations.responses.ApiResponses; // OpenAPI注解，用于组合多个ApiResponse注解
import io.swagger.v3.oas.annotations.tags.Tag; // OpenAPI注解，用于对API进行分组和描述
import jakarta.validation.Valid; // Jakarta Validation注解，启用请求参数自动校验
import lombok.RequiredArgsConstructor; // Lombok注解，自动生成包含所有final字段的构造函数
import lombok.extern.slf4j.Slf4j; // Lombok注解，自动生成SLF4J日志对象log
import org.springframework.security.core.Authentication; // Spring Security接口，表示当前用户的认证信息
import org.springframework.security.core.context.SecurityContextHolder; // Spring Security类，提供对SecurityContext的访问
import org.springframework.web.bind.annotation.*; // Spring Web注解，包含@RestController、@RequestMapping、@GetMapping等常用注解

import java.util.HashMap;
import java.util.Map;

/**
 * 用户认证控制器
 * 处理用户注册、登录、Token刷新、获取当前用户信息等认证相关接口
 * 实现双Token机制：accessToken（短期访问令牌）+ refreshToken（长期刷新令牌）
 * 登录注册接口配置为permitAll（SecurityConfig中已配置），无需认证即可访问
 *
 * @author AiLearn Platform
 */
@Slf4j // Lombok注解，自动注入SLF4J日志记录器，可直接使用log变量记录日志
@RestController // Spring MVC注解，标记该类为REST控制器，方法返回值直接作为HTTP响应体（等同于@Controller + @ResponseBody）
@RequestMapping("/api/auth") // Spring MVC注解，指定该控制器的根路径为/api/auth，所有接口路径都以此为前缀
@RequiredArgsConstructor // Lombok注解，为所有final字段生成构造函数，用于构造函数注入依赖
@Tag(name = "用户认证", description = "用户注册、登录、Token刷新、用户信息接口") // OpenAPI注解，给该控制器的API分组，设置分组名称和描述
public class UserController { // 用户认证控制器类定义

    /**
     * 用户服务，提供注册、登录、Token刷新、用户查询等核心业务逻辑
     */
    private final UserService userService; // 用户服务接口，通过构造函数注入，提供用户相关业务逻辑

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
    @PostMapping("/register") // Spring MVC注解，映射HTTP POST请求到/register路径，完整路径为POST /api/auth/register
    @Operation(summary = "用户注册", description = "新用户注册账号，成功后返回用户信息和双Token") // OpenAPI注解，描述该接口的简要摘要和详细说明
    @ApiResponses(value = { // OpenAPI注解，组合多个响应状态说明
            @ApiResponse(responseCode = "200", description = "注册成功", // 描述200成功响应
                    content = @Content(schema = @Schema(implementation = Result.class))), // 指定响应内容的Schema为Result类
            @ApiResponse(responseCode = "400", description = "参数校验失败（用户名格式错误、密码长度不符等）"), // 描述400参数错误响应
            @ApiResponse(responseCode = "409", description = "用户名已存在") // 描述409冲突响应（用户名重复）
    })
    public Result<Map<String, Object>> register( // 注册接口方法定义，返回Result包装的Map对象
            @Parameter(description = "注册请求参数", required = true) // OpenAPI注解，描述请求参数
            @Valid @RequestBody RegisterRequest request) { // @Valid启用参数校验，@RequestBody将HTTP请求体JSON解析为RegisterRequest对象
        log.info("收到用户注册请求: username={}", request.getUsername()); // 记录INFO级别日志，输出收到的注册请求用户名
        Map<String, Object> result = userService.register(request); // 调用用户服务的注册方法，获取注册结果（包含用户信息和双Token）
        return Result.success(result); // 返回成功响应，将业务结果包装在Result统一响应格式中
    } // register方法结束

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
    @PostMapping("/login") // Spring MVC注解，映射HTTP POST请求到/login路径，完整路径为POST /api/auth/login
    @Operation(summary = "用户登录", description = "用户使用用户名和密码登录，成功后返回用户信息和双Token") // OpenAPI注解，描述登录接口
    @ApiResponses(value = { // OpenAPI注解，组合多个响应状态说明
            @ApiResponse(responseCode = "200", description = "登录成功"), // 描述200成功响应
            @ApiResponse(responseCode = "400", description = "参数校验失败"), // 描述400参数错误响应
            @ApiResponse(responseCode = "401", description = "用户名或密码错误") // 描述401未授权响应（登录失败）
    })
    public Result<Map<String, Object>> login( // 登录接口方法定义，返回Result包装的Map对象
            @Parameter(description = "登录请求参数", required = true) // OpenAPI注解，描述登录请求参数
            @Valid @RequestBody LoginRequest request) { // @Valid启用参数校验，@RequestBody将请求体解析为LoginRequest对象
        log.info("收到用户登录请求: username={}", request.getUsername()); // 记录INFO级别日志，输出登录请求的用户名
        Map<String, Object> result = userService.login(request); // 调用用户服务的登录方法，验证凭据并获取登录结果
        return Result.success(result); // 返回成功响应，包装登录结果
    } // login方法结束

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
    @PostMapping("/refresh") // Spring MVC注解，映射HTTP POST请求到/refresh路径，完整路径为POST /api/auth/refresh
    @Operation(summary = "刷新Token", description = "使用有效的refreshToken获取新的accessToken，延长登录状态") // OpenAPI注解，描述刷新Token接口
    @ApiResponses(value = { // OpenAPI注解，组合多个响应状态说明
            @ApiResponse(responseCode = "200", description = "刷新成功"), // 描述200成功响应
            @ApiResponse(responseCode = "400", description = "参数校验失败（refreshToken为空）"), // 描述400参数错误响应
            @ApiResponse(responseCode = "401", description = "refreshToken无效或已过期") // 描述401未授权响应（Token无效）
    })
    public Result<Map<String, Object>> refreshToken(
            @Parameter(description = "刷新Token请求参数", required = true)
            @Valid @RequestBody RefreshTokenRequest request) {
        log.info("收到Token刷新请求");
        Map<String, String> tokens = userService.refreshToken(request.getRefreshToken());
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", tokens.get("accessToken"));
        data.put("refreshToken", tokens.get("refreshToken"));
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
    @GetMapping("/me") // Spring MVC注解，映射HTTP GET请求到/me路径，完整路径为GET /api/auth/me
    @Operation(summary = "获取当前用户信息", description = "获取当前已登录用户的详细信息，需要在请求头中携带有效的accessToken") // OpenAPI注解，描述获取当前用户接口
    @ApiResponses(value = { // OpenAPI注解，组合多个响应状态说明
            @ApiResponse(responseCode = "200", description = "获取成功"), // 描述200成功响应
            @ApiResponse(responseCode = "401", description = "未登录或Token无效") // 描述401未授权响应（未登录或Token无效）
    })
    public Result<UserPrincipal> getCurrentUser() { // 获取当前用户信息接口方法定义，返回Result包装的UserPrincipal
        log.debug("获取当前用户信息请求"); // 记录DEBUG级别日志，输出获取当前用户信息请求（DEBUG级别日志生产环境通常关闭）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // 从SecurityContextHolder获取当前认证上下文，再获取Authentication对象
        UserPrincipal principal = null; // 初始化用户主体对象为null
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) { // 判断认证对象不为空且principal是UserPrincipal类型
            principal = (UserPrincipal) authentication.getPrincipal(); // 强制转换Authentication中的principal为UserPrincipal类型
        } // if判断结束
        return Result.success(principal); // 返回成功响应，包装当前用户主体对象（未登录时为null）
    } // getCurrentUser方法结束
} // UserController类结束
