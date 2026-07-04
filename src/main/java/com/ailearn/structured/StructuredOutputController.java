package com.ailearn.structured;

import com.ailearn.common.Result;
import com.ailearn.dto.StructuredRequest;
import com.ailearn.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 结构化输出控制器
 * 提供从非结构化文本中提取结构化信息的功能，利用大语言模型的实体提取能力，
 * 将自然语言文本转换为强类型的Java对象，支持图书信息和电影信息提取
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/structured")
@RequiredArgsConstructor
@Tag(name = "结构化输出", description = "从文本中提取结构化信息（图书/电影等）")
public class StructuredOutputController {

    /**
     * 结构化输出服务，提供图书、电影等信息的结构化提取能力
     */
    private final StructuredOutputService structuredOutputService;

    /**
     * 提取图书信息
     * 从输入的文本内容中提取图书的结构化信息，包括书名、作者、ISBN、出版社、
     * 出版年份、分类、页数、价格、内容简介、标签、评分等字段
     * 接口路径：POST /api/structured/extract/book
     *
     * @param request 结构化请求参数，包含待提取的文本内容和类型标识（应为book），使用@Valid自动校验
     * @return Result<BookInfo> 提取结果，包含完整的图书结构化信息
     */
    @PostMapping("/extract/book")
    @Operation(summary = "提取图书信息", description = "从文本内容中提取图书的结构化信息，包括书名、作者、ISBN、出版社、出版年份、分类、页数、价格、简介、标签、评分等")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "提取成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（内容为空或过长、类型不匹配）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "500", description = "信息提取失败")
    })
    public Result<BookInfo> extractBook(
            @Parameter(description = "结构化提取请求参数，type应为book", required = true)
            @Valid @RequestBody StructuredRequest request) {
        log.info("收到图书信息提取请求: contentLength={}",
                request.getContent() != null ? request.getContent().length() : 0);
        BookInfo bookInfo = structuredOutputService.extractBookInfo(request.getContent());
        return Result.success(bookInfo);
    }

    /**
     * 提取电影信息
     * 从输入的文本内容中提取电影的结构化信息，包括片名、导演、主演、类型、
     * 上映年份、片长、国家、语言、评分、票房、剧情简介、标签等字段
     * 返回电影列表，支持从文本中提取多部电影信息
     * 接口路径：POST /api/structured/extract/movie
     *
     * @param request 结构化请求参数，包含待提取的文本内容和类型标识（应为movie），使用@Valid自动校验
     * @return Result<List<MovieInfo>> 提取结果，包含电影结构化信息列表
     */
    @PostMapping("/extract/movie")
    @Operation(summary = "提取电影信息", description = "从文本内容中提取电影的结构化信息，包括片名、导演、主演、类型、年份、片长、国家、语言、评分、票房、简介、标签等")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "提取成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（内容为空或过长、类型不匹配）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "500", description = "信息提取失败")
    })
    public Result<List<MovieInfo>> extractMovie(
            @Parameter(description = "结构化提取请求参数，type应为movie", required = true)
            @Valid @RequestBody StructuredRequest request) {
        log.info("收到电影信息提取请求: contentLength={}",
                request.getContent() != null ? request.getContent().length() : 0);
        MovieInfo movieInfo = structuredOutputService.extractMovieInfo(request.getContent());
        return Result.success(List.of(movieInfo));
    }

    /**
     * 从SecurityContext获取当前登录用户信息
     * 私有辅助方法，用于在各个接口中获取当前用户
     *
     * @return UserPrincipal 当前用户主体，未登录时返回null
     */
    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
