package com.ailearn.rag; // 声明当前类所在的包：rag（检索增强生成知识库模块）

// 导入统一响应封装类Result
import com.ailearn.common.Result;
// 导入RAG问答请求DTO，包含question和可选conversationId
import com.ailearn.dto.RagChatRequest;
// 导入RAG文档实体类，记录已上传文档的元数据
import com.ailearn.entity.RagDocument;
// 导入用户安全主体
import com.ailearn.security.UserPrincipal;
// 导入Swagger注解集：接口描述、参数描述、响应描述、分组
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
// 导入Jakarta参数校验注解
import jakarta.validation.Valid;
// 导入Lombok注解：构造器注入和日志
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// 导入MediaType，指定multipart文件上传和SSE的Content-Type
import org.springframework.http.MediaType;
// 导入Spring Security认证相关类
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
// 导入Spring MVC的REST注解集
import org.springframework.web.bind.annotation.*;
// 导入MultipartFile，接收multipart/form-data上传的文件
import org.springframework.web.multipart.MultipartFile;
// 导入Reactor Flux，SSE流式响应返回类型
import reactor.core.publisher.Flux;

// 导入HashMap/List/Map集合类
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
@Slf4j // Lombok注解：自动生成log日志对象
@RestController // 标记为REST控制器
@RequestMapping("/api/rag") // 类级路径前缀：所有接口以 /api/rag 开头
@RequiredArgsConstructor // Lombok为final字段生成构造器，注入RagService
@Tag(name = "RAG知识库", description = "文档上传和检索增强问答") // Swagger分组
public class RagController { // 定义RAG知识库控制器类

    /**
     * RAG服务，提供文档处理、向量化存储和检索增强问答能力
     */
    private final RagService ragService; // 注入RAG服务（文档解析/向量化/检索问答全链路）

    /**
     * 上传文本到知识库
     * 接收纯文本内容，进行分块和向量化后存储到向量数据库，用于后续检索增强问答
     * 接口路径：POST /api/rag/upload/text
     *
     * @param content 要上传的文本内容，必填
     * @param source  文本来源标识，可选，用于标注文档来源（如URL、文件名等）
     * @return Result<Map> 上传结果
     */
    @PostMapping("/upload/text") // 映射POST请求到 /api/rag/upload/text
    @Operation(summary = "上传文本到知识库", description = "将纯文本内容分块向量化后存入知识库，用于后续检索增强问答") // Swagger描述
    @ApiResponses(value = { // Swagger响应码说明
            @ApiResponse(responseCode = "200", description = "上传成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（内容为空）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "500", description = "文档处理失败")
    })
    public Result<Map<String, Object>> uploadText( // 返回上传结果
            @Parameter(description = "要上传的文本内容", required = true) // Swagger参数描述
            @RequestParam String content, // 从请求参数获取文本内容（必填）
            @Parameter(description = "文本来源标识（如URL、文件名）") // Swagger参数描述
            @RequestParam(required = false) String source) { // 从请求参数获取来源标识（可选）
        log.info("收到文本上传请求: source={}, contentLength={}", source, content != null ? content.length() : 0); // 打印上传日志（防空指针）
        ragService.addDocumentText(content, source); // 调用RAG服务：分块+向量化+存储
        Map<String, Object> data = new HashMap<>(); // 创建返回Map
        data.put("message", "文本已成功向量化并存入知识库"); // 放入成功提示
        data.put("source", source); // 放入来源标识
        return Result.success(data); // 统一封装返回
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
     * @return Result<Map> 上传结果
     */
    @PostMapping(value = "/upload/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) // 映射POST请求，consumes声明接收multipart文件上传
    @Operation(summary = "上传文件到知识库（支持PDF/Word/Excel/PPT/HTML/MD/图片OCR/文本）",
            description = "上传多种格式文件，自动解析、分块、向量化后存入知识库。支持PDF、Word、Excel、PPT、HTML、Markdown、文本文件以及图片OCR识别") // Swagger描述
    @ApiResponses(value = { // Swagger响应码说明
            @ApiResponse(responseCode = "200", description = "上传成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（文件为空、格式不支持、文件过大）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "500", description = "文件解析或向量化失败")
    })
    public Result<Map<String, Object>> uploadFile( // 返回上传结果（含分块统计）
            @Parameter(description = "要上传的文件", required = true) // Swagger参数描述
            @RequestParam("file") MultipartFile file, // 接收表单中名为file的上传文件
            @Parameter(description = "文件来源标识") // Swagger参数描述
            @RequestParam(required = false) String source) { // 可选来源标识
        log.info("收到文件上传请求: filename={}, size={}, source={}",
                file.getOriginalFilename(), file.getSize(), source); // 打印文件名、大小、来源
        Map<String, Object> result = ragService.addDocumentFile(file, source); // 调用RAG服务：解析+分块+向量化+存储，返回统计信息
        Map<String, Object> data = new HashMap<>(); // 创建返回Map
        data.put("message", "文件上传成功"); // 放入成功提示
        data.put("filename", file.getOriginalFilename()); // 放入原始文件名
        data.put("documentCount", result.get("documentCount")); // 放入分块数量（从Service结果取）
        data.put("totalChars", result.get("totalChars")); // 放入总字符数
        data.put("fileType", result.get("fileType")); // 放入文件类型
        return Result.success(data); // 统一封装返回
    }

    /**
     * 知识库问答（同步模式）
     * 基于已上传的知识库内容进行问答，系统会自动检索相关文档片段作为上下文，
     * 然后结合大模型生成准确的回答（检索增强生成）
     * 接口路径：POST /api/rag/ask
     *
     * @param request RAG问答请求参数，包含问题内容和可选的会话ID，使用@Valid自动校验
     * @return Result<Map> 问答结果
     */
    @PostMapping("/ask") // 映射POST请求到 /api/rag/ask
    @Operation(summary = "知识库问答（同步）", description = "基于知识库内容进行问答，自动检索相关文档并结合大模型生成准确回答") // Swagger描述
    @ApiResponses(value = { // Swagger响应码说明
            @ApiResponse(responseCode = "200", description = "问答成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（问题为空或过长）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "500", description = "检索或问答失败")
    })
    public Result<Map<String, Object>> ask( // 返回问答结果
            @Parameter(description = "知识库问答请求参数", required = true) // Swagger参数描述
            @Valid @RequestBody RagChatRequest request) { // @Valid校验，@RequestBody接收JSON
        log.info("收到RAG问答请求: conversationId={}, questionLength={}",
                request.getConversationId(), request.getQuestion() != null ? request.getQuestion().length() : 0); // 打印请求日志
        String response = ragService.askWithRag(request.getQuestion()); // 调用RAG服务：检索+增强+生成
        Map<String, Object> data = new HashMap<>(); // 创建返回Map
        data.put("conversationId", request.getConversationId()); // 放入会话ID
        data.put("reply", response); // 放入基于知识库的回答
        return Result.success(data); // 统一封装返回
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
    @GetMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // 映射GET请求，输出SSE事件流
    @Operation(summary = "知识库问答（SSE流式）", description = "使用SSE流式输出基于知识库的问答结果，实时推送token。注意：此接口为GET请求，参数通过URL查询参数传递") // Swagger描述
    @ApiResponses(value = { // Swagger响应码说明
            @ApiResponse(responseCode = "200", description = "SSE连接建立成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（问题为空）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Flux<String> streamAsk( // 返回Flux<String>：SSE持续推送的异步流
            @Parameter(description = "用户问题", required = true) // Swagger参数描述
            @RequestParam String question) { // 从URL查询参数获取问题（必填）
        log.info("收到RAG流式问答请求: questionLength={}", question != null ? question.length() : 0); // 打印请求日志
        return ragService.streamAskWithRag(question); // 调用RAG服务的流式版本，返回SSE事件流
    }

    /**
     * 获取知识库统计信息
     * 返回知识库的当前状态，包括文档数量、支持的文件格式、最大文件大小等
     * 接口路径：GET /api/rag/stats
     *
     * @return Result<Map> 知识库统计信息
     */
    @GetMapping("/stats") // 映射GET请求到 /api/rag/stats
    @Operation(summary = "获取知识库统计", description = "获取知识库的统计信息，包括文档数量、支持的文件格式等") // Swagger描述
    @ApiResponses(value = { // Swagger响应码说明
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<Map<String, Object>> getStats() { // 返回统计信息
        log.debug("获取知识库统计信息"); // 打印debug日志（高频接口用debug级别避免日志刷屏）
        Map<String, Object> stats = ragService.getDocumentStats(); // 调用RAG服务获取统计
        return Result.success(stats); // 统一封装返回
    }

    /**
     * 获取知识库文档列表接口
     * 返回知识库中所有已上传文档的元数据列表，按上传时间倒序排列。
     * 接口路径：GET /api/rag/documents
     *
     * @return Result&lt;List&lt;RagDocument&gt;&gt; 文档列表
     */
    @GetMapping("/documents") // 映射GET请求到 /api/rag/documents
    @Operation(summary = "获取文档列表", description = "获取知识库中所有已上传的文档列表，按上传时间倒序排列") // Swagger描述
    @ApiResponses(value = { // Swagger响应码说明
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<List<RagDocument>> listDocuments() { // 返回文档实体列表
        log.debug("获取文档列表"); // 打印debug日志
        List<RagDocument> docs = ragService.listDocuments(); // 调用RAG服务查询所有文档
        return Result.success(docs); // 统一封装返回
    }

    /**
     * 删除知识库文档接口
     * 根据文档ID删除知识库中的文档，同时清理本地缓存、删除原始文件、删除数据库记录。
     * 注意：当前VectorStore实现可能不支持向量数据的物理删除。
     * 接口路径：DELETE /api/rag/documents/{docId}
     *
     * @param docId 要删除的文档ID（UUID格式）
     * @return Result&lt;Map&lt;String, Object&gt;&gt; 包含deleted布尔值、docId和结果消息
     */
    @DeleteMapping("/documents/{docId}") // 映射DELETE请求，{docId}是路径变量
    @Operation(summary = "删除文档", description = "从知识库中删除指定文档，同时删除关联的文件和向量数据") // Swagger描述
    @ApiResponses(value = { // Swagger响应码说明
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效"),
            @ApiResponse(responseCode = "404", description = "文档不存在")
    })
    public Result<Map<String, Object>> deleteDocument( // 返回删除结果
            @Parameter(description = "文档ID（UUID）", required = true) // Swagger参数描述
            @PathVariable String docId) { // 从路径中取文档ID
        log.info("删除文档请求: docId={}", docId); // 打印删除日志
        boolean deleted = ragService.deleteDocument(docId); // 调用RAG服务删除，返回是否成功
        Map<String, Object> data = new HashMap<>(); // 创建返回Map
        data.put("deleted", deleted); // 放入删除结果布尔值
        data.put("docId", docId); // 放入文档ID
        data.put("message", deleted ? "文档删除成功" : "文档不存在或已删除"); // 根据结果放入不同提示语
        return Result.success(data); // 统一封装返回
    }

    /**
     * 从SecurityContext获取当前登录用户信息
     * 私有辅助方法，用于在各个接口中获取当前用户
     *
     * @return UserPrincipal 当前用户主体，未登录时返回null
     */
    private UserPrincipal getCurrentUser() { // 私有方法：从Spring Security上下文提取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // 从ThreadLocal安全上下文获取认证对象
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) { // 判空+类型检查
            return (UserPrincipal) authentication.getPrincipal(); // 强转为UserPrincipal返回
        }
        return null; // 未认证时返回null
    }
}
