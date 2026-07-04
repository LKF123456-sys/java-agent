package com.ailearn.rag;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.memory.DatabaseChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG（检索增强生成）知识库服务类
 * 提供完整的RAG功能实现，支持文档管理和基于知识库的智能问答：
 * <ul>
 *   <li><b>文档管理</b>：上传、解析、切分、向量化存储PDF/Word/文本文档</li>
 *   <li><b>智能问答</b>：基于检索到的相关文档片段，结合大模型生成准确回答</li>
 *   <li><b>多轮对话</b>：集成ChatMemory支持带上下文的RAG对话</li>
 *   <li><b>流式响应</b>：支持SSE流式输出回答</li>
 * </ul>
 *
 * <p>文档处理流程：上传文件 → 解析提取文本 → 按Token切分 → 生成向量 → 存入VectorStore
 * <p>问答流程：用户问题 → 向量检索相似文档 → 构建增强Prompt → 大模型生成回答
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
public class RagService {

    /**
     * AI聊天客户端
     * 由Spring AI自动注入，基于ChatModel构建，用于调用大模型生成回答
     */
    private final ChatClient chatClient;

    /**
     * 向量存储
     * 支持PgVector（PostgreSQL）和SimpleVectorStore（内存）两种实现，用于存储文档向量
     */
    private final VectorStore vectorStore;

    /**
     * 数据库聊天记忆实现
     * 用于持久化RAG对话历史，支持多轮对话上下文
     */
    private final DatabaseChatMemory chatMemory;

    /**
     * 文件上传存储路径
     * 从配置文件读取，默认为当前目录下的 uploads 文件夹
     */
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * 文档解析结果缓存
     * Key: 文档ID，Value: 解析切分后的文档片段列表，用于避免重复解析
     */
    private final Map<String, List<Document>> documentCache = new HashMap<>();

    /**
     * RAG系统提示词
     * 指导大模型基于检索到的上下文信息回答问题，不编造答案
     */
    private static final String RAG_SYSTEM_PROMPT = """
            你是一个知识库问答助手。请基于以下提供的上下文信息来回答用户的问题。
            如果上下文中没有相关信息，请明确说明你无法从提供的知识库中找到答案，不要编造信息。
            回答时请引用相关的内容来源，保持回答的准确性和条理性。
            
            上下文信息：
            {context}
            """;

    /**
     * 构造方法：初始化RAG服务
     * 注入所需的依赖组件并构建ChatClient（仅配置ChatMemory支持多轮对话）
     *
     * @param chatClientBuilder ChatClient构建器
     * @param vectorStore       向量存储实现
     * @param chatMemory        数据库聊天记忆实现
     */
    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, DatabaseChatMemory chatMemory) {
        this.vectorStore = vectorStore;
        this.chatMemory = chatMemory;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
        log.info("RAG服务初始化完成");
    }

    /**
     * 从向量存储中检索与问题相关的文档片段
     * 使用余弦相似度检索最相关的Top-K个文档
     *
     * @param question 用户问题
     * @return String 拼接后的相关文档上下文
     */
    private String retrieveRelevantContext(String question) {
        try {
            List<Document> relevantDocs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(5)
                            .similarityThreshold(0.7)
                            .build()
            );
            if (relevantDocs == null || relevantDocs.isEmpty()) {
                return "未找到相关的知识库内容。";
            }
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < relevantDocs.size(); i++) {
                Document doc = relevantDocs.get(i);
                contextBuilder.append("【文档片段").append(i + 1).append("】\n");
                contextBuilder.append(doc.getText()).append("\n\n");
            }
            return contextBuilder.toString();
        } catch (Exception e) {
            log.warn("向量检索失败，将不使用知识库回答", e);
            return "知识库检索暂时不可用。";
        }
    }

    /**
     * 从文件系统路径加载并解析文档
     * 支持PDF、Word、纯文本等格式，解析后自动切分并向量化存储
     *
     * @param filePath 文件路径（支持本地路径和file://协议URL）
     * @return String 文档ID，用于后续操作
     * @throws RuntimeException 文件不存在或解析失败时抛出异常
     */
    public String addDocumentFromPath(String filePath) {
        try {
            Resource resource;
            if (filePath.startsWith("file:")) {
                resource = new UrlResource(filePath);
            } else {
                Path path = Paths.get(filePath);
                resource = new UrlResource(path.toUri());
            }
            if (!resource.exists()) {
                throw new BusinessException(ErrorCode.RAG_DOCUMENT_NOT_FOUND);
            }
            return processAndStoreDocument(resource, filePath);
        } catch (BusinessException e) {
            throw e;
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.RAG_FILE_READ_FAILED, e);
        }
    }

    /**
     * 上传文件并添加到知识库
     * 处理前端上传的MultipartFile文件，保存到本地后解析入库
     *
     * @param file 上传的文件（支持PDF、Word、TXT等格式）
     * @return String 文档ID
     * @throws BusinessException 文件为空或处理失败时抛出异常
     */
    public String uploadDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.RAG_DOCUMENT_EMPTY);
        }
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID() + fileExtension;
            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件已保存: {}", filePath);
            Resource resource = new UrlResource(filePath.toUri());
            return processAndStoreDocument(resource, originalFilename);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.RAG_FILE_READ_FAILED, e);
        }
    }

    /**
     * 处理文档：解析文本 → 切分片段 → 向量化存储
     * 根据文件扩展名自动选择合适的解析器，使用TokenTextSplitter进行智能切分
     *
     * @param resource     Spring Resource资源对象
     * @param documentName 文档名称（用于元数据和日志）
     * @return String 生成的文档ID
     * @throws RuntimeException 文档解析失败时抛出异常
     */
    private String processAndStoreDocument(Resource resource, String documentName) {
        try {
            String documentId = UUID.randomUUID().toString();
            List<Document> documents = parseDocument(resource, documentName);

            // 使用TokenTextSplitter进行文本切分，默认参数：chunkSize=800, minChunkSize=100, keepSeparator=true
            TokenTextSplitter textSplitter = new TokenTextSplitter();
            List<Document> splitDocuments = textSplitter.split(documents);

            // 为每个文档片段添加元数据：文档ID和文档名称
            for (Document doc : splitDocuments) {
                doc.getMetadata().put("documentId", documentId);
                doc.getMetadata().put("documentName", documentName);
            }

            vectorStore.add(splitDocuments);
            documentCache.put(documentId, splitDocuments);
            log.info("文档处理完成: {}, 共 {} 个片段", documentName, splitDocuments.size());
            return documentId;
        } catch (Exception e) {
            log.error("文档处理失败: {}", documentName, e);
            throw new BusinessException(ErrorCode.RAG_DOCUMENT_PARSE_FAILED, e);
        }
    }

    /**
     * 解析不同格式的文档
     * 支持PDF（PagePdfDocumentReader）、Word（Apache POI）、纯文本等格式
     *
     * @param resource     文档资源
     * @param documentName 文档名称
     * @return List<Document> 解析后的文档列表（PDF按页，其他单文档）
     * @throws IOException 文件读取失败时抛出
     */
    private List<Document> parseDocument(Resource resource, String documentName) throws IOException {
        String filename = documentName.toLowerCase();
        List<Document> documents = new ArrayList<>();

        if (filename.endsWith(".pdf")) {
            // 使用PagePdfDocumentReader解析PDF，按页拆分并保留页码信息
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPagesPerDocument(1)
                    .build();
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
            documents = pdfReader.get();
            log.info("PDF解析完成: {}, 共 {} 页", documentName, documents.size());
        } else if (filename.endsWith(".docx") || filename.endsWith(".doc")) {
            // 使用Apache POI解析Word文档
            try {
                String content = parseWordDocument(resource);
                Document doc = new Document(content);
                doc.getMetadata().put("source", documentName);
                doc.getMetadata().put("type", "word");
                documents.add(doc);
                log.info("Word文档解析完成: {}", documentName);
            } catch (Exception e) {
                log.warn("Word解析失败，尝试作为文本解析: {}", e.getMessage());
                String content = new String(resource.getInputStream().readAllBytes());
                Document doc = new Document(content);
                doc.getMetadata().put("source", documentName);
                documents.add(doc);
            }
        } else {
            // 默认作为纯文本解析
            String content = new String(resource.getInputStream().readAllBytes());
            Document doc = new Document(content);
            doc.getMetadata().put("source", documentName);
            doc.getMetadata().put("type", "text");
            documents.add(doc);
            log.info("文本文件解析完成: {}", documentName);
        }

        return documents;
    }

    /**
     * 解析Word文档（.docx格式）
     * 使用Apache POI提取Word文档中的所有段落文本
     *
     * @param resource Word文档资源
     * @return String 提取的纯文本内容
     * @throws Exception 文档解析失败时抛出
     */
    private String parseWordDocument(Resource resource) throws Exception {
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                     new org.apache.poi.xwpf.usermodel.XWPFDocument(resource.getInputStream())) {
            StringBuilder content = new StringBuilder();
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (StringUtils.hasText(text)) {
                    content.append(text).append("\n");
                }
            }
            return content.toString();
        }
    }

    /**
     * 基于知识库的智能问答（同步模式）
     * 流程：先从VectorStore检索相关文档片段 → 构建增强Prompt → 调用大模型生成回答
     *
     * @param question       用户问题
     * @param conversationId 会话ID（用于多轮对话上下文）
     * @return String AI生成的回答（基于检索到的知识）
     * @throws BusinessException 问题为空时抛出异常
     */
    public String chat(String question, String conversationId) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        String convId = StringUtils.hasText(conversationId) ? conversationId : "rag-" + UUID.randomUUID();
        // 第一步：检索相关文档构建上下文
        String context = retrieveRelevantContext(question);
        // 第二步：构建系统提示词，注入检索到的上下文
        String systemPrompt = RAG_SYSTEM_PROMPT.replace("{context}", context);
        // 第三步：调用大模型生成回答
        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                .call()
                .content();
    }

    /**
     * 基于知识库的智能问答（流式模式）
     * 流程：先检索相关文档 → 构建增强Prompt → 以SSE流方式实时输出AI回答的token
     *
     * @param question       用户问题
     * @param conversationId 会话ID
     * @return Flux<String> 回答token流
     * @throws BusinessException 问题为空时抛出异常
     */
    public Flux<String> streamChat(String question, String conversationId) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        String convId = StringUtils.hasText(conversationId) ? conversationId : "rag-" + UUID.randomUUID();
        // 第一步：检索相关文档构建上下文
        String context = retrieveRelevantContext(question);
        // 第二步：构建系统提示词，注入检索到的上下文
        String systemPrompt = RAG_SYSTEM_PROMPT.replace("{context}", context);
        // 第三步：流式调用大模型生成回答
        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                .stream()
                .content();
    }

    /**
     * 从知识库删除文档
     * 注意：当前VectorStore接口可能不支持直接删除，此方法仅清除本地缓存
     *
     * @param documentId 文档ID
     * @return boolean 删除是否成功
     */
    public boolean deleteDocument(String documentId) {
        if (StringUtils.hasText(documentId) && documentCache.containsKey(documentId)) {
            documentCache.remove(documentId);
            log.info("文档已从缓存删除: {}", documentId);
            return true;
        }
        return false;
    }

    /**
     * 获取知识库中已缓存的文档数量
     *
     * @return int 已缓存的文档片段总数
     */
    public int getDocumentCount() {
        return documentCache.values().stream().mapToInt(List::size).sum();
    }

    /**
     * 添加纯文本内容到知识库（Controller专用方法）
     * 将纯文本内容包装为Document，进行切分和向量化后存储
     *
     * @param content 纯文本内容
     * @param source  文本来源标识
     */
    public void addDocumentText(String content, String source) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.RAG_DOCUMENT_EMPTY);
        }
        try {
            String docId = UUID.randomUUID().toString();
            Document doc = new Document(content);
            doc.getMetadata().put("documentId", docId);
            doc.getMetadata().put("documentName", source != null ? source : "text-upload");
            doc.getMetadata().put("type", "text");

            TokenTextSplitter textSplitter = new TokenTextSplitter();
            List<Document> splitDocuments = textSplitter.split(List.of(doc));

            for (Document splitDoc : splitDocuments) {
                splitDoc.getMetadata().put("documentId", docId);
                splitDoc.getMetadata().put("documentName", source != null ? source : "text-upload");
            }

            vectorStore.add(splitDocuments);
            documentCache.put(docId, splitDocuments);
            log.info("文本内容已添加到知识库: source={}, 共 {} 个片段", source, splitDocuments.size());
        } catch (Exception e) {
            log.error("文本内容添加失败", e);
            throw new BusinessException(ErrorCode.RAG_DOCUMENT_PARSE_FAILED, e);
        }
    }

    /**
     * 上传文件到知识库（Controller专用方法）
     * 上传文件并返回处理统计信息
     *
     * @param file   上传的文件
     * @param source 文件来源标识
     * @return Map<String, Object> 处理统计，包含documentCount、totalChars、fileType
     */
    public Map<String, Object> addDocumentFile(MultipartFile file, String source) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.RAG_DOCUMENT_EMPTY);
        }
        String documentId = uploadDocument(file);
        List<Document> docs = documentCache.get(documentId);
        Map<String, Object> result = new HashMap<>();
        int documentCount = docs != null ? docs.size() : 0;
        int totalChars = 0;
        if (docs != null) {
            for (Document doc : docs) {
                totalChars += doc.getText() != null ? doc.getText().length() : 0;
            }
        }
        String originalFilename = file.getOriginalFilename();
        String fileType = "unknown";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileType = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }
        result.put("documentCount", documentCount);
        result.put("totalChars", totalChars);
        result.put("fileType", fileType);
        result.put("documentId", documentId);
        return result;
    }

    /**
     * 基于知识库问答（Controller专用简化方法）
     * 自动生成会话ID，不保留多轮对话上下文
     *
     * @param question 用户问题
     * @return String AI回答
     */
    public String askWithRag(String question) {
        return chat(question, null);
    }

    /**
     * 基于知识库流式问答（Controller专用简化方法）
     * 自动生成会话ID，不保留多轮对话上下文
     *
     * @param question 用户问题
     * @return Flux<String> 回答token流
     */
    public Flux<String> streamAskWithRag(String question) {
        return streamChat(question, null);
    }

    /**
     * 获取知识库统计信息（Controller专用方法）
     * 返回文档数量、支持格式、文件大小限制等信息
     *
     * @return Map<String, Object> 统计信息
     */
    public Map<String, Object> getDocumentStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("estimatedDocumentCount", getDocumentCount());
        stats.put("supportedFormats", java.util.Set.of("pdf", "doc", "docx", "txt", "md", "html"));
        stats.put("maxFileSize", 10 * 1024 * 1024);
        return stats;
    }
}
