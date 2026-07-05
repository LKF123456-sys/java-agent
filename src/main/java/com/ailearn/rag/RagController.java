package com.ailearn.rag;

import com.ailearn.common.Result;
import com.ailearn.dto.RagChatRequest;
import com.ailearn.entity.RagDocument;
import com.ailearn.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG知识库控制器
 * 提供检索增强生成（Retrieval-Augmented Generation）功能，包括：
 * - 文本文档上传和向量化存储
 * - 多格式文件上传（PDF/Word/Excel/PPT/HTML/MD/图片OCR/文本等）
 * - 基于知识库的问答（同步和SSE流式）
 * - 知识库统计信息查询
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Tag(name = "RAG知识库", description = "文档上传和检索增强问答")
public class RagController {

    /**
     * RAG服务，提供文档处理、向量化存储和检索增强问答能力
     */
    private final RagService ragService;

    /**
     * 上传文本到知识库
     * 接收纯文本内容，进行分块和向量化后存储到向量数据库，用于后续检索增强问答
     * 接口路径：POST /api/rag/upload/text
     *
     * @param content 要上传的文本内容，必填
     * @param source  文本来源标识，可选，用于标注文档来源（如URL、文件名等）
     * @return Result<Map> 上传结果，包含：
     *         - message: String 成功提示信息
     *         - source: String 文档来源
     */
    @PostMapping("/upload/text")
    @Operation(summary = "上传文本到知识库", description = "将纯文本内容分块向量化后存入知识库，用于后续检索增强问答")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "上传成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（内容为空）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "500", description = "文档处理失败")
    })
    public Result<Map<String, Object>> uploadText(
            @Parameter(description = "要上传的文本内容", required = true)
            @RequestParam String content,
            @Parameter(description = "文本来源标识（如URL、文件名）")
            @RequestParam(required = false) String source) {
        log.info("收到文本上传请求: source={}, contentLength={}", source, content != null ? content.length() : 0);
        ragService.addDocumentText(content, source);
        Map<String, Object> data = new HashMap<>();
        data.put("message", "文本已成功向量化并存入知识库");
        data.put("source", source);
        return Result.success(data);
    }

    /**
     * 上传文件到知识库
     * 支持多种文件格式：PDF、Word（doc/docx）、Excel（xls/xlsx）、PPT（ppt/pptx）、
     * HTML、Markdown、纯文本、图片（支持OCR文字识别）等
     * 文件会被自动解析、分块、向量化后存储到向量数据库
     * 接口路径：POST /api/rag/upload/file
     *
     * @param file   要上传的文件，必填，multipart/form-data格式
     * @param source 文件来源标识，可选
     * @return Result<Map> 上传结果，包含：
     *         - message: String 成功提示信息
     *         - filename: String 原始文件名
     *         - documentCount: int 分块后的文档数量
     *         - totalChars: int 总字符数
     *         - fileType: String 文件类型
     */
    @PostMapping(value = "/upload/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件到知识库（支持PDF/Word/Excel/PPT/HTML/MD/图片OCR/文本）",
            description = "上传多种格式文件，自动解析、分块、向量化后存入知识库。支持PDF、Word、Excel、PPT、HTML、Markdown、文本文件以及图片OCR识别")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "上传成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（文件为空、格式不支持、文件过大）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "500", description = "文件解析或向量化失败")
    })
    public Result<Map<String, Object>> uploadFile(
            @Parameter(description = "要上传的文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "文件来源标识")
            @RequestParam(required = false) String source) {
        log.info("收到文件上传请求: filename={}, size={}, source={}",
                file.getOriginalFilename(), file.getSize(), source);
        Map<String, Object> result = ragService.addDocumentFile(file, source);
        Map<String, Object> data = new HashMap<>();
        data.put("message", "文件上传成功");
        data.put("filename", file.getOriginalFilename());
        data.put("documentCount", result.get("documentCount"));
        data.put("totalChars", result.get("totalChars"));
        data.put("fileType", result.get("fileType"));
        return Result.success(data);
    }

    /**
     * 知识库问答（同步模式）
     * 基于已上传的知识库内容进行问答，系统会自动检索相关文档片段作为上下文，
     * 然后结合大模型生成准确的回答（检索增强生成）
     * 接口路径：POST /api/rag/ask
     *
     * @param request RAG问答请求参数，包含问题内容和可选的会话ID，使用@Valid自动校验
     * @return Result<Map> 问答结果，包含：
     *         - conversationId: Long 会话ID
     *         - reply: String 基于知识库的回答
     */
    @PostMapping("/ask")
    @Operation(summary = "知识库问答（同步）", description = "基于知识库内容进行问答，自动检索相关文档并结合大模型生成准确回答")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "问答成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（问题为空或过长）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "500", description = "检索或问答失败")
    })
    public Result<Map<String, Object>> ask(
            @Parameter(description = "知识库问答请求参数", required = true)
            @Valid @RequestBody RagChatRequest request) {
        log.info("收到RAG问答请求: conversationId={}, questionLength={}",
                request.getConversationId(), request.getQuestion() != null ? request.getQuestion().length() : 0);
        String response = ragService.askWithRag(request.getQuestion());
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", request.getConversationId());
        data.put("reply", response);
        return Result.success(data);
    }

    /**
     * 知识库问答（SSE流式模式）
     * 使用Server-Sent Events（SSE）实时推送问答结果，提供打字机效果
     * 系统同样会先检索知识库相关文档，然后流式输出生成的回答
     * SSE端点使用GET方法，参数通过@RequestParam接收
     * 接口路径：GET /api/rag/ask/stream
     *
     * @param question 用户问题，必填
     * @return Flux<String> SSE数据流，实时推送回答token
     */
    @GetMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "知识库问答（SSE流式）", description = "使用SSE流式输出基于知识库的问答结果，实时推送token。注意：此接口为GET请求，参数通过URL查询参数传递")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SSE连接建立成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（问题为空）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Flux<String> streamAsk(
            @Parameter(description = "用户问题", required = true)
            @RequestParam String question) {
        log.info("收到RAG流式问答请求: questionLength={}", question != null ? question.length() : 0);
        return ragService.streamAskWithRag(question);
    }

    /**
     * 获取知识库统计信息
     * 返回知识库的当前状态，包括文档数量、支持的文件格式、最大文件大小等
     * 接口路径：GET /api/rag/stats
     *
     * @return Result<Map> 知识库统计信息，包含：
     *         - estimatedDocumentCount: int 估计的文档分块数量
     *         - supportedFormats: Set<String> 支持的文件格式列表
     *         - maxFileSize: long 最大文件大小限制（字节）
     */
    @GetMapping("/stats")
    @Operation(summary = "获取知识库统计", description = "获取知识库的统计信息，包括文档数量、支持的文件格式等")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<Map<String, Object>> getStats() {
        log.debug("获取知识库统计信息");
        Map<String, Object> stats = ragService.getDocumentStats();
        return Result.success(stats);
    }

    @GetMapping("/documents")
    @Operation(summary = "获取文档列表", description = "获取知识库中所有已上传的文档列表，按上传时间倒序排列")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<List<RagDocument>> listDocuments() {
        log.debug("获取文档列表");
        List<RagDocument> docs = ragService.listDocuments();
        return Result.success(docs);
    }

    @DeleteMapping("/documents/{docId}")
    @Operation(summary = "删除文档", description = "从知识库中删除指定文档，同时删除关联的文件和向量数据")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "404", description = "文档不存在")
    })
    public Result<Map<String, Object>> deleteDocument(
            @Parameter(description = "文档ID（UUID）", required = true)
            @PathVariable String docId) {
        log.info("删除文档请求: docId={}", docId);
        boolean deleted = ragService.deleteDocument(docId);
        Map<String, Object> data = new HashMap<>();
        data.put("deleted", deleted);
        data.put("docId", docId);
        data.put("message", deleted ? "文档删除成功" : "文档不存在或已删除");
        return Result.success(data);
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
