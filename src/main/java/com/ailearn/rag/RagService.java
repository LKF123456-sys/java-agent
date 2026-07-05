package com.ailearn.rag;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.entity.RagDocument;
import com.ailearn.mapper.RagDocumentMapper;
import com.ailearn.memory.DatabaseChatMemory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
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
import java.util.Comparator;
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
     * 基于ChatModel构建，配置了ChatMemory支持多轮对话，用于调用大模型生成RAG回答
     */
    private final ChatClient chatClient;

    /**
     * 向量存储
     * 用于存储文档向量嵌入和执行相似度检索，是RAG的核心组件
     */
    private final VectorStore vectorStore;

    /**
     * 数据库聊天记忆实现
     * 支持RAG对话的多轮上下文管理
     */
    private final DatabaseChatMemory chatMemory;

    /**
     * RAG文档MyBatis Mapper
     * 用于知识库文档元数据的数据库CRUD操作
     */
    private final RagDocumentMapper ragDocumentMapper;

    /**
     * 文件上传目录
     * 从配置文件读取，默认为uploads目录，用于保存用户上传的原始文件
     */
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * 文档缓存
     * Key为文档ID，Value为切分后的文档片段列表，用于快速访问已处理文档
     */
    private final Map<String, List<Document>> documentCache = new HashMap<>();

    /**
     * 文档文件路径映射
     * Key为文档ID，Value为原始文件在本地文件系统的存储路径，用于文档删除时清理文件
     */
    private final Map<String, String> documentFilePaths = new HashMap<>();

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
    public RagService(ChatModel chatModel, VectorStore vectorStore, DatabaseChatMemory chatMemory, RagDocumentMapper ragDocumentMapper) {
        this.vectorStore = vectorStore;
        this.chatMemory = chatMemory;
        this.ragDocumentMapper = ragDocumentMapper;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
        loadExistingDocuments();
        log.info("RAG服务初始化完成");
    }

    /**
     * 服务启动时加载现有文档记录
     * 从数据库查询所有已上传的文档元数据，重建documentFilePaths映射，
     * 确保服务重启后仍能正确关联文档ID和文件路径
     */
    private void loadExistingDocuments() {
        try {
            List<RagDocument> docs = ragDocumentMapper.selectList(
                    new LambdaQueryWrapper<RagDocument>().orderByDesc(RagDocument::getCreatedAt)
            );
            for (RagDocument doc : docs) {
                if (doc.getFilePath() != null) {
                    documentFilePaths.put(doc.getDocId(), doc.getFilePath());
                }
            }
            log.info("已加载 {} 个文档记录", docs.size());
        } catch (Exception e) {
            log.warn("加载现有文档记录失败: {}", e.getMessage());
        }
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
            Path path;
            if (filePath.startsWith("file:")) {
                resource = new UrlResource(filePath);
                path = Paths.get(new java.net.URI(filePath));
            } else {
                path = Paths.get(filePath);
                resource = new UrlResource(path.toUri());
            }
            if (!resource.exists()) {
                throw new BusinessException(ErrorCode.RAG_DOCUMENT_NOT_FOUND);
            }
            String fileName = path.getFileName() != null ? path.getFileName().toString() : filePath;
            long fileSize = Files.exists(path) ? Files.size(path) : 0;
            return processAndStoreDocument(resource, fileName, filePath, fileSize);
        } catch (BusinessException e) {
            throw e;
        } catch (MalformedURLException | java.net.URISyntaxException e) {
            throw new BusinessException(ErrorCode.RAG_FILE_READ_FAILED, e);
        } catch (IOException e) {
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
            String docId = processAndStoreDocument(resource, originalFilename, filePath.toString(), file.getSize());
            return docId;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.RAG_FILE_READ_FAILED, e);
        }
    }

    /**
     * 处理文档并存储到向量数据库（核心私有方法）
     * 完整的文档入库流程：解析文档 → 按Token切分 → 添加元数据 → 向量化存储 → 保存元数据到数据库
     *
     * @param resource     文档资源
     * @param documentName 文档名称
     * @param filePath     原始文件存储路径
     * @param fileSize     文件大小（字节）
     * @return String 生成的文档ID（UUID）
     * @throws BusinessException 文档解析或存储失败时抛出异常
     */
    private String processAndStoreDocument(Resource resource, String documentName, String filePath, Long fileSize) {
        try {
            // 生成唯一文档ID
            String documentId = UUID.randomUUID().toString();
            // 第一步：解析文档（支持PDF/Word/文本格式）
            List<Document> documents = parseDocument(resource, documentName);

            // 第二步：使用TokenTextSplitter按Token数量切分文档，避免超出模型上下文窗口
            TokenTextSplitter textSplitter = new TokenTextSplitter();
            List<Document> splitDocuments = textSplitter.split(documents);

            // 第三步：为每个文档片段添加元数据，便于检索时溯源
            for (Document doc : splitDocuments) {
                doc.getMetadata().put("documentId", documentId);
                doc.getMetadata().put("documentName", documentName);
            }

            // 第四步：将文档片段向量化并存入VectorStore
            vectorStore.add(splitDocuments);
            // 更新本地缓存
            documentCache.put(documentId, splitDocuments);
            if (filePath != null) {
                documentFilePaths.put(documentId, filePath);
            }

            // 统计总字符数
            int totalChars = 0;
            for (Document doc : splitDocuments) {
                totalChars += doc.getText() != null ? doc.getText().length() : 0;
            }

            // 提取文件类型
            String fileType = "";
            if (documentName != null && documentName.contains(".")) {
                fileType = documentName.substring(documentName.lastIndexOf(".") + 1).toLowerCase();
            }

            // 第五步：保存文档元数据到关系型数据库
            RagDocument ragDoc = new RagDocument();
            ragDoc.setDocId(documentId);
            ragDoc.setFileName(documentName);
            ragDoc.setFileType(fileType);
            ragDoc.setFileSize(fileSize);
            ragDoc.setChunkCount(splitDocuments.size());
            ragDoc.setTotalChars((long) totalChars);
            ragDoc.setFilePath(filePath);
            ragDocumentMapper.insert(ragDoc);

            log.info("文档处理完成: {}, 共 {} 个片段, 总字符: {}", documentName, splitDocuments.size(), totalChars);
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
        boolean deleted = false;
        if (StringUtils.hasText(documentId)) {
            documentCache.remove(documentId);
            String filePath = documentFilePaths.remove(documentId);
            if (filePath != null) {
                try {
                    Path path = Paths.get(filePath);
                    if (Files.exists(path)) {
                        Files.delete(path);
                        log.info("文件已删除: {}", filePath);
                    }
                } catch (IOException e) {
                    log.warn("文件删除失败: {}", e.getMessage());
                }
            }
            RagDocument doc = ragDocumentMapper.selectOne(
                    new LambdaQueryWrapper<RagDocument>().eq(RagDocument::getDocId, documentId)
            );
            if (doc != null) {
                ragDocumentMapper.deleteById(doc.getId());
                deleted = true;
            }
            log.info("文档已删除: {}", documentId);
        }
        return deleted;
    }

    /**
     * 获取知识库文档总数
     *
     * @return int 文档记录数量
     */
    public int getDocumentCount() {
        Long count = ragDocumentMapper.selectCount(null);
        return count != null ? count.intValue() : documentCache.size();
    }

    /**
     * 获取知识库所有文档列表
     * 按创建时间倒序排列
     *
     * @return List&lt;RagDocument&gt; 文档元数据列表
     */
    public List<RagDocument> listDocuments() {
        return ragDocumentMapper.selectList(
                new LambdaQueryWrapper<RagDocument>().orderByDesc(RagDocument::getCreatedAt)
        );
    }

    /**
     * 添加纯文本内容到知识库
     * 直接接收文本字符串，分块向量化后存入知识库，无需上传文件
     *
     * @param content 要添加的文本内容
     * @param source  文本来源标识（可选）
     * @throws BusinessException 内容为空时抛出异常
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

            // 对文本进行Token切分
            TokenTextSplitter textSplitter = new TokenTextSplitter();
            List<Document> splitDocuments = textSplitter.split(List.of(doc));

            // 为每个片段添加元数据
            for (Document splitDoc : splitDocuments) {
                splitDoc.getMetadata().put("documentId", docId);
                splitDoc.getMetadata().put("documentName", source != null ? source : "text-upload");
            }

            // 向量化存储
            vectorStore.add(splitDocuments);
            documentCache.put(docId, splitDocuments);

            // 保存元数据到数据库
            int totalChars = content.length();
            RagDocument ragDoc = new RagDocument();
            ragDoc.setDocId(docId);
            ragDoc.setFileName(source != null ? source : "文本内容-" + docId.substring(0, 8));
            ragDoc.setFileType("txt");
            ragDoc.setFileSize((long) content.length());
            ragDoc.setChunkCount(splitDocuments.size());
            ragDoc.setTotalChars((long) totalChars);
            ragDoc.setFilePath(null);
            ragDocumentMapper.insert(ragDoc);

            log.info("文本内容已添加到知识库: source={}, 共 {} 个片段", source, splitDocuments.size());
        } catch (Exception e) {
            log.error("文本内容添加失败", e);
            throw new BusinessException(ErrorCode.RAG_DOCUMENT_PARSE_FAILED, e);
        }
    }

    /**
     * 上传文件并添加到知识库（Controller专用，返回详细结果）
     * 调用uploadDocument处理文件，然后统计文档分块信息返回
     *
     * @param file   上传的文件
     * @param source 文件来源标识
     * @return Map&lt;String, Object&gt; 包含文档ID、文件名、类型、分块数、总字符数
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
        result.put("filename", originalFilename);
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
