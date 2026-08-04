package com.ailearn.structured; // 声明包名

import com.ailearn.common.Result; // 统一响应
import com.ailearn.dto.StructuredRequest; // 结构化请求DTO
import com.ailearn.security.UserPrincipal; // 用户主体
import io.swagger.v3.oas.annotations.Operation; // OpenAPI操作注解
import io.swagger.v3.oas.annotations.Parameter; // OpenAPI参数注解
import io.swagger.v3.oas.annotations.responses.ApiResponse; // OpenAPI响应注解
import io.swagger.v3.oas.annotations.responses.ApiResponses; // OpenAPI响应组
import io.swagger.v3.oas.annotations.tags.Tag; // OpenAPI分组
import jakarta.validation.Valid; // 参数校验注解
import lombok.RequiredArgsConstructor; // 构造器注入
import lombok.extern.slf4j.Slf4j; // 日志
import org.springframework.security.core.Authentication; // 认证接口
import org.springframework.security.core.context.SecurityContextHolder; // 安全上下文
import org.springframework.web.bind.annotation.*; // Web注解

import java.util.List; // 列表

/**
 * 结构化输出控制器
 * 提供从非结构化文本中提取结构化信息的功能
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log
@RestController // REST控制器
@RequestMapping("/api/structured") // 根路径
@RequiredArgsConstructor // 构造器注入
@Tag(name = "结构化输出", description = "从文本中提取结构化信息（图书/电影等）") // API分组
public class StructuredOutputController { // 结构化输出控制器

    /** 结构化输出服务 */
    private final StructuredOutputService structuredOutputService; // 注入

    /**
     * 提取图书信息
     * 接口路径：POST /api/structured/extract/book
     *
     * @param request 结构化请求参数
     * @return Result<BookInfo> 提取结果
     */
    @PostMapping("/extract/book") // POST映射
    @Operation(summary = "提取图书信息", description = "从文本中提取图书结构化信息") // API描述
    @ApiResponses(value = { // 响应码
            @ApiResponse(responseCode = "200", description = "提取成功"), // 200
            @ApiResponse(responseCode = "400", description = "参数校验失败"), // 400
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"), // 401
            @ApiResponse(responseCode = "500", description = "信息提取失败") // 500
    })
    public Result<BookInfo> extractBook( // 提取图书方法
            @Parameter(description = "结构化提取请求参数，type应为book", required = true) // 参数描述
            @Valid @RequestBody StructuredRequest request) { // @Valid触发校验，@RequestBody绑定JSON
        log.info("收到图书信息提取请求: contentLength={}", // 业务日志
                request.getContent() != null ? request.getContent().length() : 0); // 内容长度
        BookInfo bookInfo = structuredOutputService.extractBookInfo(request.getContent()); // 调用服务提取
        return Result.success(bookInfo); // 返回结果
    }

    /**
     * 提取电影信息
     * 接口路径：POST /api/structured/extract/movie
     *
     * @param request 结构化请求参数
     * @return Result<List<MovieInfo>> 提取结果
     */
    @PostMapping("/extract/movie") // POST映射
    @Operation(summary = "提取电影信息", description = "从文本中提取电影结构化信息") // API描述
    @ApiResponses(value = { // 响应码
            @ApiResponse(responseCode = "200", description = "提取成功"), // 200
            @ApiResponse(responseCode = "400", description = "参数校验失败"), // 400
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"), // 401
            @ApiResponse(responseCode = "500", description = "信息提取失败") // 500
    })
    public Result<List<MovieInfo>> extractMovie( // 提取电影方法
            @Parameter(description = "结构化提取请求参数，type应为movie", required = true) // 参数描述
            @Valid @RequestBody StructuredRequest request) { // 校验+绑定
        log.info("收到电影信息提取请求: contentLength={}", // 业务日志
                request.getContent() != null ? request.getContent().length() : 0); // 内容长度
        MovieInfo movieInfo = structuredOutputService.extractMovieInfo(request.getContent()); // 调用服务提取
        return Result.success(List.of(movieInfo)); // 包装为单元素列表返回
    }

    /**
     * 从SecurityContext获取当前登录用户信息
     *
     * @return UserPrincipal 当前用户，未登录返回null
     */
    private UserPrincipal getCurrentUser() { // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // 取认证对象
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) { // 类型匹配
            return (UserPrincipal) authentication.getPrincipal(); // 强转返回
        }
        return null; // 未登录返回null
    }
} // StructuredOutputController类结束
