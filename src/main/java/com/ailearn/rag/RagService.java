package com.ailearn.rag; // 声明当前类所在的包：rag（检索增强生成知识库模块）

// 导入业务异常类，用于抛出业务错误
import com.ailearn.common.BusinessException;
// 导入错误码枚举，定义了RAG相关的错误码
import com.ailearn.common.ErrorCode;
// 导入RAG文档实体类，记录已上传文档的元数据（文件名/类型/大小/分块数等）
import com.ailearn.entity.RagDocument;
// 导入RAG文档的MyBatis-Plus Mapper，用于文档元数据的数据库CRUD
import com.ailearn.mapper.RagDocumentMapper;
// 导入数据库聊天记忆实现，支持RAG对话的多轮上下文
import com.ailearn.memory.DatabaseChatMemory;
// 导入MyBatis-Plus的Lambda查询包装器，用方法引用写类型安全的查询条件
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// 导入Lombok日志注解，自动生成log对象
import lombok.extern.slf4j.Slf4j;
// 导入Spring AI的ChatClient，流式API调用大模型的入口
import org.springframework.ai.chat.client.ChatClient;
// 导入消息记忆顾问，自动把历史对话注入上下文
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
// 导入ChatMemory接口，含CONVERSATION_ID常量
import org.springframework.ai.chat.memory.ChatMemory;
// 导入ChatModel接口，代表底层大模型（Ollama）
import org.springframework.ai.chat.model.ChatModel;
// 导入Spring AI的Document类，表示一个文档片段（文本+元数据）
import org.springframework.ai.document.Document;
// 导入PDF文档阅读器，按页解析PDF文件
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
// 导入PDF阅读器的配置类
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
// 导入Token文本切分器，按Token数量把长文档切成小块（避免超出模型上下文窗口）
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
// 导入向量检索请求类，封装查询文本、TopK、相似度阈值
import org.springframework.ai.vectorstore.SearchRequest;
// 导入向量存储接口，本项目实现是PgVectorStore（PostgreSQL+pgvector）
import org.springframework.ai.vectorstore.VectorStore;
// 导入@Value注解，从配置文件注入属性值
import org.springframework.beans.factory.annotation.Value;
// 导入Spring的Resource抽象，统一表示文件/URL/类路径等资源
import org.springframework.core.io.Resource;
// 导入UrlResource，用URL形式表示文件资源
import org.springframework.core.io.UrlResource;
// 导入Spring的@Service注解
import org.springframework.stereotype.Service;
// 导入字符串工具类
import org.springframework.util.StringUtils;
// 导入MultipartFile，接收上传的文件
import org.springframework.web.multipart.MultipartFile;
// 导入Reactor Flux，流式响应类型
import reactor.core.publisher.Flux;

// 导入IO异常类
import java.io.IOException;
// 导入URL格式异常类
import java.net.MalformedURLException;
// 导入NIO文件操作类：Files（文件工具）、Path（路径）、Paths（路径工厂）、StandardCopyOption（复制选项）
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
// 导入常用集合类
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// 导入UUID，生成文档唯一ID
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
@Slf4j // Lombok注解：自动生成log日志对象
@Service // Spring注解：标记为业务服务Bean
public class RagService { // 定义RAG知识库服务类

    /**
     * AI聊天客户端
     * 基于ChatModel构建，配置了ChatMemory支持多轮对话，用于调用大模型生成RAG回答
     */
    private final ChatClient chatClient; // 预配置的ChatClient（只挂了记忆顾问，系统提示词在每次调用时动态注入检索结果）

    /**
     * 向量存储
     * 用于存储文档向量嵌入和执行相似度检索，是RAG的核心组件
     */
    private final VectorStore vectorStore; // 向量数据库（PgVector），存文档片段的向量并做相似度检索

    /**
     * 数据库聊天记忆实现
     * 支持RAG对话的多轮上下文管理
     */
    private final DatabaseChatMemory chatMemory; // 数据库聊天记忆（构造时传给记忆顾问）

    /**
     * RAG文档MyBatis Mapper
     * 用于知识库文档元数据的数据库CRUD操作
     */
    private final RagDocumentMapper ragDocumentMapper; // 文档元数据表的数据库访问对象

    /**
     * 文件上传目录
     * 从配置文件读取，默认为uploads目录，用于保存用户上传的原始文件
     */
    @Value("${app.upload.dir:uploads}") // 从application.yml读app.upload.dir，没有配置时用默认值uploads
    private String uploadDir; // 上传文件的本地保存目录

    /**
     * 文档缓存
     * Key为文档ID，Value为切分后的文档片段列表，用于快速访问已处理文档
     */
    private final Map<String, List<Document>> documentCache = new HashMap<>(); // 内存缓存：文档ID → 该文档的所有分块

    /**
     * 文档文件路径映射
     * Key为文档ID，Value为原始文件在本地文件系统的存储路径，用于文档删除时清理文件
     */
    private final Map<String, String> documentFilePaths = new HashMap<>(); // 内存映射：文档ID → 原始文件路径

    /**
     * RAG系统提示词
     * 指导大模型基于检索到的上下文信息回答问题，不编造答案
     */
    // RAG专用系统提示词模板（static final：全局一份，节省内存）；{context}是占位符，调用时会替换成实际检索到的文档片段
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
     * @param chatModel         AI大模型客户端
     * @param vectorStore       向量存储实现
     * @param chatMemory        数据库聊天记忆实现
     * @param ragDocumentMapper 文档元数据Mapper
     */
    public RagService(ChatModel chatModel, VectorStore vectorStore, DatabaseChatMemory chatMemory, RagDocumentMapper ragDocumentMapper) { // 构造器注入4个依赖
        this.vectorStore = vectorStore; // 保存向量存储引用
        this.chatMemory = chatMemory; // 保存聊天记忆引用
        this.ragDocumentMapper = ragDocumentMapper; // 保存文档Mapper引用
        this.chatClient = ChatClient.builder(chatModel) // 基于底层模型构建ChatClient
                .defaultAdvisors( // 注册默认顾问
                        MessageChatMemoryAdvisor.builder(chatMemory).build() // 只挂记忆顾问：RAG问答也能记住多轮对话
                )
                .build(); // 构建完成
        loadExistingDocuments(); // 启动时从数据库加载已有文档记录，重建文件路径映射
        log.info("RAG服务初始化完成"); // 打印初始化日志
    }

    /**
     * 服务启动时加载现有文档记录
     * 从数据库查询所有已上传的文档元数据，重建documentFilePaths映射，
     * 确保服务重启后仍能正确关联文档ID和文件路径
     */
    private void loadExistingDocuments() { // 私有方法：重启后恢复文档路径映射
        try {
            List<RagDocument> docs = ragDocumentMapper.selectList( // 查询所有文档记录
                    new LambdaQueryWrapper<RagDocument>().orderByDesc(RagDocument::getCreatedAt) // 按创建时间倒序
            );
            for (RagDocument doc : docs) { // 遍历每条文档记录
                if (doc.getFilePath() != null) { // 只处理有文件路径的（纯文本上传的没有文件路径）
                    documentFilePaths.put(doc.getDocId(), doc.getFilePath()); // 重建 文档ID→文件路径 映射
                }
            }
            log.info("已加载 {} 个文档记录", docs.size()); // 打印加载数量
        } catch (Exception e) { // 捕获一切异常（比如数据库还没初始化好）
            log.warn("加载现有文档记录失败: {}", e.getMessage()); // 只记警告，不阻断启动
        }
    }

    /**
     * 从向量存储中检索与问题相关的文档片段
     * 使用余弦相似度检索最相关的Top-K个文档
     *
     * @param question 用户问题
     * @return String 拼接后的相关文档上下文
     */
    private String retrieveRelevantContext(String question) { // 私有方法：RAG中的"R"（Retrieval检索）
        try {
            List<Document> relevantDocs = vectorStore.similaritySearch( // 调用向量数据库做相似度检索
                    SearchRequest.builder() // 构建检索请求
                            .query(question) // 查询文本：用户问题（会被embedding模型转成向量再比对）
                            .topK(5) // 返回最相似的前5个文档片段
                            .similarityThreshold(0.7) // 相似度阈值0.7：低于此相似度的片段直接丢弃（防止答非所问）
                            .build() // 构建完成
            );
            if (relevantDocs == null || relevantDocs.isEmpty()) { // 没检索到任何相关片段
                return "未找到相关的知识库内容。"; // 返回明确的"没找到"提示（LLM看到后会如实告知用户）
            }
            StringBuilder contextBuilder = new StringBuilder(); // 用StringBuilder拼接上下文（比String拼接高效）
            for (int i = 0; i < relevantDocs.size(); i++) { // 遍历每个检索到的片段
                Document doc = relevantDocs.get(i); // 取出第i个片段
                contextBuilder.append("【文档片段").append(i + 1).append("】\n"); // 加编号标题，方便LLM引用
                contextBuilder.append(doc.getText()).append("\n\n"); // 追加片段正文，空两行分隔
            }
            return contextBuilder.toString(); // 返回拼接好的上下文
        } catch (Exception e) { // 捕获检索异常（向量库连接失败、embedding服务不可用等）
            log.warn("向量检索失败，将不使用知识库回答", e); // 记录警告日志
            return "知识库检索暂时不可用。"; // 优雅降级：返回不可用提示，让LLM基于自身知识回答
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
    public String addDocumentFromPath(String filePath) { // 公开方法：从本地路径添加文档（管理后台/测试用）
        try {
            Resource resource; // 声明资源对象
            Path path; // 声明路径对象
            if (filePath.startsWith("file:")) { // 情况1：传入的是file://协议的URL
                resource = new UrlResource(filePath); // 直接用URL创建资源
                path = Paths.get(new java.net.URI(filePath)); // 把URI转成Path
            } else { // 情况2：传入的是普通本地路径
                path = Paths.get(filePath); // 直接解析路径
                resource = new UrlResource(path.toUri()); // 转成URI再创建资源
            }
            if (!resource.exists()) { // 文件不存在时
                throw new BusinessException(ErrorCode.RAG_DOCUMENT_NOT_FOUND); // 抛出"文档不存在"业务异常
            }
            String fileName = path.getFileName() != null ? path.getFileName().toString() : filePath; // 提取文件名（防空指针）
            long fileSize = Files.exists(path) ? Files.size(path) : 0; // 获取文件大小（字节）
            return processAndStoreDocument(resource, fileName, filePath, fileSize); // 走统一的"解析→切分→向量化→入库"流程
        } catch (BusinessException e) { // 业务异常直接透传
            throw e;
        } catch (MalformedURLException | java.net.URISyntaxException e) { // URL格式错误（多异常捕获写法）
            throw new BusinessException(ErrorCode.RAG_FILE_READ_FAILED, e); // 包装为"文件读取失败"
        } catch (IOException e) { // IO读取错误
            throw new BusinessException(ErrorCode.RAG_FILE_READ_FAILED, e); // 包装为"文件读取失败"
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
    public String uploadDocument(MultipartFile file) { // 公开方法：处理前端上传的文件
        if (file == null || file.isEmpty()) { // 文件为空校验
            throw new BusinessException(ErrorCode.RAG_DOCUMENT_EMPTY); // 抛出"文档为空"业务异常
        }
        try {
            Path uploadPath = Paths.get(uploadDir); // 把上传目录字符串转成Path
            if (!Files.exists(uploadPath)) { // 目录不存在时
                Files.createDirectories(uploadPath); // 递归创建目录（含父目录）
            }
            String originalFilename = file.getOriginalFilename(); // 获取原始文件名（用户上传时的名字）
            String fileExtension = ""; // 声明文件扩展名
            if (originalFilename != null && originalFilename.contains(".")) { // 文件名含点号时
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".")); // 截取最后一个点号后的扩展名（含点，如.pdf）
            }
            String uniqueFilename = UUID.randomUUID() + fileExtension; // 用UUID生成唯一文件名（防止同名文件互相覆盖）
            Path filePath = uploadPath.resolve(uniqueFilename); // 拼出完整保存路径
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING); // 把上传的文件流写入本地磁盘（已存在则覆盖）
            log.info("文件已保存: {}", filePath); // 打印保存日志
            Resource resource = new UrlResource(filePath.toUri()); // 把保存后的文件包装成Resource
            String docId = processAndStoreDocument(resource, originalFilename, filePath.toString(), file.getSize()); // 走统一入库流程
            return docId; // 返回文档ID
        } catch (IOException e) { // 捕获IO异常
            log.error("文件上传失败", e); // 记录错误日志
            throw new BusinessException(ErrorCode.RAG_FILE_READ_FAILED, e); // 包装为业务异常
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
    private String processAndStoreDocument(Resource resource, String documentName, String filePath, Long fileSize) { // 核心私有方法：文档入库全流程
        try {
            // 生成唯一文档ID
            String documentId = UUID.randomUUID().toString(); // UUID作为文档全局唯一标识
            // 第一步：解析文档（支持PDF/Word/文本格式）
            List<Document> documents = parseDocument(resource, documentName); // 按文件类型解析成Document列表

            // 第二步：使用TokenTextSplitter按Token数量切分文档，避免超出模型上下文窗口
            TokenTextSplitter textSplitter = new TokenTextSplitter(); // 创建切分器（默认每块约800 token，块间有重叠）
            List<Document> splitDocuments = textSplitter.split(documents); // 执行切分，得到文档片段列表

            // 第三步：为每个文档片段添加元数据，便于检索时溯源
            for (Document doc : splitDocuments) { // 遍历每个片段
                doc.getMetadata().put("documentId", documentId); // 打上文档ID标签（删除/溯源时用）
                doc.getMetadata().put("documentName", documentName); // 打上文档名标签（回答时引用来源用）
            }

            // 第四步：将文档片段向量化并存入VectorStore
            vectorStore.add(splitDocuments); // 内部流程：每个片段→embedding模型生成向量→存入PgVector
            // 更新本地缓存
            documentCache.put(documentId, splitDocuments); // 缓存分块列表（统计字符数、快速访问用）
            if (filePath != null) { // 有文件路径时（纯文本上传没有）
                documentFilePaths.put(documentId, filePath); // 记录文件路径映射（删除时清理文件用）
            }

            // 统计总字符数
            int totalChars = 0; // 初始化计数器
            for (Document doc : splitDocuments) { // 遍历每个片段
                totalChars += doc.getText() != null ? doc.getText().length() : 0; // 累加字符数（防空指针）
            }

            // 提取文件类型
            String fileType = ""; // 声明文件类型
            if (documentName != null && documentName.contains(".")) { // 文件名含点号时
                fileType = documentName.substring(documentName.lastIndexOf(".") + 1).toLowerCase(); // 取扩展名并转小写（如pdf/docx）
            }

            // 第五步：保存文档元数据到关系型数据库（向量存PgVector，元数据存业务表，双写）
            RagDocument ragDoc = new RagDocument(); // 创建文档元数据实体
            ragDoc.setDocId(documentId); // 设置文档ID
            ragDoc.setFileName(documentName); // 设置文件名
            ragDoc.setFileType(fileType); // 设置文件类型
            ragDoc.setFileSize(fileSize); // 设置文件大小
            ragDoc.setChunkCount(splitDocuments.size()); // 设置分块数量
            ragDoc.setTotalChars((long) totalChars); // 设置总字符数（int转long）
            ragDoc.setFilePath(filePath); // 设置文件路径
            ragDocumentMapper.insert(ragDoc); // 插入数据库

            log.info("文档处理完成: {}, 共 {} 个片段, 总字符: {}", documentName, splitDocuments.size(), totalChars); // 打印完成日志
            return documentId; // 返回文档ID
        } catch (Exception e) { // 捕获一切异常
            log.error("文档处理失败: {}", documentName, e); // 记录错误日志
            throw new BusinessException(ErrorCode.RAG_DOCUMENT_PARSE_FAILED, e); // 包装为"文档解析失败"业务异常
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
    private List<Document> parseDocument(Resource resource, String documentName) throws IOException { // 私有方法：按格式解析文档
        String filename = documentName.toLowerCase(); // 文件名转小写，统一比较扩展名（防止PDF/Pdf混写）
        List<Document> documents = new ArrayList<>(); // 创建空的文档列表

        if (filename.endsWith(".pdf")) { // 情况1：PDF文件
            // 使用PagePdfDocumentReader解析PDF，按页拆分并保留页码信息
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder() // 构建PDF读取配置
                    .withPagesPerDocument(1) // 每1页作为一个Document（保留页级粒度，检索更精准）
                    .build(); // 构建完成
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config); // 创建PDF阅读器
            documents = pdfReader.get(); // 执行解析，得到按页拆分的Document列表
            log.info("PDF解析完成: {}, 共 {} 页", documentName, documents.size()); // 打印解析日志
        } else if (filename.endsWith(".docx") || filename.endsWith(".doc")) { // 情况2：Word文件
            // 使用Apache POI解析Word文档
            try {
                String content = parseWordDocument(resource); // 调用POI解析方法提取纯文本
                Document doc = new Document(content); // 包装成单个Document
                doc.getMetadata().put("source", documentName); // 元数据记录来源
                doc.getMetadata().put("type", "word"); // 元数据记录类型
                documents.add(doc); // 加入列表
                log.info("Word文档解析完成: {}", documentName); // 打印日志
            } catch (Exception e) { // POI解析失败时（如老版本.doc格式不兼容）
                log.warn("Word解析失败，尝试作为文本解析: {}", e.getMessage()); // 降级：尝试按纯文本读取
                String content = new String(resource.getInputStream().readAllBytes()); // 直接读原始字节转字符串
                Document doc = new Document(content); // 包装成Document
                doc.getMetadata().put("source", documentName); // 记录来源
                documents.add(doc); // 加入列表
            }
        } else { // 情况3：其他格式（txt/md/html等），默认按纯文本解析
            // 默认作为纯文本解析
            String content = new String(resource.getInputStream().readAllBytes()); // 读全部字节转字符串
            Document doc = new Document(content); // 包装成Document
            doc.getMetadata().put("source", documentName); // 记录来源
            doc.getMetadata().put("type", "text"); // 记录类型
            documents.add(doc); // 加入列表
            log.info("文本文件解析完成: {}", documentName); // 打印日志
        }

        return documents; // 返回解析结果
    }

    /**
     * 解析Word文档（.docx格式）
     * 使用Apache POI提取Word文档中的所有段落文本
     *
     * @param resource Word文档资源
     * @return String 提取的纯文本内容
     * @throws Exception 文档解析失败时抛出
     */
    private String parseWordDocument(Resource resource) throws Exception { // 私有方法：POI解析Word
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = // try-with-resources：自动关闭文档释放资源
                     new org.apache.poi.xwpf.usermodel.XWPFDocument(resource.getInputStream())) { // 从输入流创建XWPF文档对象
            StringBuilder content = new StringBuilder(); // 用StringBuilder累积文本
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph para : doc.getParagraphs()) { // 遍历Word中的每个段落
                String text = para.getText(); // 提取段落文本
                if (StringUtils.hasText(text)) { // 跳过空段落
                    content.append(text).append("\n"); // 追加文本并换行
                }
            }
            return content.toString(); // 返回拼接好的全文
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
    public String chat(String question, String conversationId) { // 公开方法：RAG同步问答
        if (!StringUtils.hasText(question)) { // 问题为空校验
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY); // 抛出"消息为空"业务异常
        }
        String convId = StringUtils.hasText(conversationId) ? conversationId : "rag-" + UUID.randomUUID(); // 有会话ID用传入的，没有则生成rag-前缀的随机ID
        // 第一步：检索相关文档构建上下文（RAG的R）
        String context = retrieveRelevantContext(question); // 向量检索Top5相关片段
        // 第二步：构建系统提示词，注入检索到的上下文（RAG的A：Augmented增强）
        String systemPrompt = RAG_SYSTEM_PROMPT.replace("{context}", context); // 把占位符替换成实际检索结果
        // 第三步：调用大模型生成回答（RAG的G：Generation生成）
        return chatClient.prompt() // 构建AI调用
                .system(systemPrompt) // 设置含检索上下文的系统提示词（每次调用动态设置，因为检索结果随问题变化）
                .user(question) // 设置用户问题
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId)) // 指定记忆会话ID
                .call() // 同步调用
                .content(); // 返回回答文本
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
    public Flux<String> streamChat(String question, String conversationId) { // 公开方法：RAG流式问答
        if (!StringUtils.hasText(question)) { // 问题为空校验
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        String convId = StringUtils.hasText(conversationId) ? conversationId : "rag-" + UUID.randomUUID(); // 会话ID处理同同步版
        // 第一步：检索相关文档构建上下文
        String context = retrieveRelevantContext(question); // 向量检索
        // 第二步：构建系统提示词，注入检索到的上下文
        String systemPrompt = RAG_SYSTEM_PROMPT.replace("{context}", context); // 替换占位符
        // 第三步：流式调用大模型生成回答
        return chatClient.prompt() // 构建AI调用
                .system(systemPrompt) // 设置含上下文的系统提示词
                .user(question) // 设置用户问题
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId)) // 指定记忆会话ID
                .stream() // 流式调用
                .content(); // 返回token流
    }

    /**
     * 从知识库删除文档
     * 注意：当前VectorStore接口可能不支持直接删除，此方法仅清除本地缓存
     *
     * @param documentId 文档ID
     * @return boolean 删除是否成功
     */
    public boolean deleteDocument(String documentId) { // 公开方法：删除文档
        boolean deleted = false; // 删除结果标记
        if (StringUtils.hasText(documentId)) { // 文档ID非空时才处理
            documentCache.remove(documentId); // 第一步：清除内存缓存中的分块
            String filePath = documentFilePaths.remove(documentId); // 第二步：取出并移除文件路径映射
            if (filePath != null) { // 有原始文件时（纯文本上传的没有）
                try {
                    Path path = Paths.get(filePath); // 转成Path
                    if (Files.exists(path)) { // 文件存在时
                        Files.delete(path); // 删除本地原始文件
                        log.info("文件已删除: {}", filePath); // 打印日志
                    }
                } catch (IOException e) { // 删除文件失败（如文件被占用）
                    log.warn("文件删除失败: {}", e.getMessage()); // 只记警告，继续删数据库记录
                }
            }
            RagDocument doc = ragDocumentMapper.selectOne( // 第三步：查数据库里的元数据记录
                    new LambdaQueryWrapper<RagDocument>().eq(RagDocument::getDocId, documentId) // 按文档ID精确匹配
            );
            if (doc != null) { // 记录存在时
                ragDocumentMapper.deleteById(doc.getId()); // 按主键删除数据库记录
                deleted = true; // 标记删除成功
            }
            log.info("文档已删除: {}", documentId); // 打印日志
        }
        return deleted; // 返回删除结果
    }

    /**
     * 获取知识库文档总数
     *
     * @return int 文档记录数量
     */
    public int getDocumentCount() { // 公开方法：统计文档数量
        Long count = ragDocumentMapper.selectCount(null); // selectCount(null)表示无条件统计全表
        return count != null ? count.intValue() : documentCache.size(); // 数据库查询失败时降级用缓存大小
    }

    /**
     * 获取知识库所有文档列表
     * 按创建时间倒序排列
     *
     * @return List&lt;RagDocument&gt; 文档元数据列表
     */
    public List<RagDocument> listDocuments() { // 公开方法：列出所有文档
        return ragDocumentMapper.selectList( // 查询所有记录
                new LambdaQueryWrapper<RagDocument>().orderByDesc(RagDocument::getCreatedAt) // 按创建时间倒序（最新上传的排前面）
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
    public void addDocumentText(String content, String source) { // 公开方法：纯文本直接入库
        if (!StringUtils.hasText(content)) { // 内容为空校验
            throw new BusinessException(ErrorCode.RAG_DOCUMENT_EMPTY); // 抛出"文档为空"业务异常
        }
        try {
            String docId = UUID.randomUUID().toString(); // 生成文档ID
            Document doc = new Document(content); // 把文本包装成Document
            doc.getMetadata().put("documentId", docId); // 元数据：文档ID
            doc.getMetadata().put("documentName", source != null ? source : "text-upload"); // 元数据：文档名（无来源时用默认名）
            doc.getMetadata().put("type", "text"); // 元数据：类型标记为text

            // 对文本进行Token切分
            TokenTextSplitter textSplitter = new TokenTextSplitter(); // 创建切分器
            List<Document> splitDocuments = textSplitter.split(List.of(doc)); // List.of把单个文档包成列表再切分

            // 为每个片段添加元数据（切分后的片段是新对象，要重新打标签）
            for (Document splitDoc : splitDocuments) { // 遍历每个片段
                splitDoc.getMetadata().put("documentId", docId); // 打上文档ID
                splitDoc.getMetadata().put("documentName", source != null ? source : "text-upload"); // 打上文档名
            }

            // 向量化存储
            vectorStore.add(splitDocuments); // 生成向量并存入PgVector
            documentCache.put(docId, splitDocuments); // 更新内存缓存

            // 保存元数据到数据库
            int totalChars = content.length(); // 总字符数就是原文长度
            RagDocument ragDoc = new RagDocument(); // 创建元数据实体
            ragDoc.setDocId(docId); // 设置文档ID
            ragDoc.setFileName(source != null ? source : "文本内容-" + docId.substring(0, 8)); // 设置显示名（无来源时用"文本内容-UUID前8位"）
            ragDoc.setFileType("txt"); // 类型固定为txt
            ragDoc.setFileSize((long) content.length()); // 大小即字符数
            ragDoc.setChunkCount(splitDocuments.size()); // 分块数
            ragDoc.setTotalChars((long) totalChars); // 总字符数
            ragDoc.setFilePath(null); // 纯文本没有文件路径
            ragDocumentMapper.insert(ragDoc); // 插入数据库

            log.info("文本内容已添加到知识库: source={}, 共 {} 个片段", source, splitDocuments.size()); // 打印日志
        } catch (Exception e) { // 捕获一切异常
            log.error("文本内容添加失败", e); // 记录错误
            throw new BusinessException(ErrorCode.RAG_DOCUMENT_PARSE_FAILED, e); // 包装为业务异常
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
    public Map<String, Object> addDocumentFile(MultipartFile file, String source) { // 公开方法：上传文件并返回统计信息
        if (file == null || file.isEmpty()) { // 文件为空校验
            throw new BusinessException(ErrorCode.RAG_DOCUMENT_EMPTY);
        }
        String documentId = uploadDocument(file); // 复用uploadDocument完成保存+解析+入库
        List<Document> docs = documentCache.get(documentId); // 从缓存取出该文档的分块列表
        Map<String, Object> result = new HashMap<>(); // 创建返回Map
        int documentCount = docs != null ? docs.size() : 0; // 分块数量（防空指针）
        int totalChars = 0; // 初始化总字符数
        if (docs != null) { // 有分块时统计字符数
            for (Document doc : docs) { // 遍历每个分块
                totalChars += doc.getText() != null ? doc.getText().length() : 0; // 累加字符数
            }
        }
        String originalFilename = file.getOriginalFilename(); // 取原始文件名
        String fileType = "unknown"; // 默认类型
        if (originalFilename != null && originalFilename.contains(".")) { // 文件名含点号时提取扩展名
            fileType = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase(); // 取扩展名转小写
        }
        result.put("documentCount", documentCount); // 放入分块数
        result.put("totalChars", totalChars); // 放入总字符数
        result.put("fileType", fileType); // 放入文件类型
        result.put("documentId", documentId); // 放入文档ID
        result.put("filename", originalFilename); // 放入文件名
        return result; // 返回统计结果
    }

    /**
     * 基于知识库问答（Controller专用简化方法）
     * 自动生成会话ID，不保留多轮对话上下文
     *
     * @param question 用户问题
     * @return String AI回答
     */
    public String askWithRag(String question) { // 简化版同步问答：无需传会话ID
        return chat(question, null); // 委托给完整版chat，会话ID传null（内部自动生成随机ID）
    }

    /**
     * 基于知识库流式问答（Controller专用简化方法）
     * 自动生成会话ID，不保留多轮对话上下文
     *
     * @param question 用户问题
     * @return Flux<String> 回答token流
     */
    public Flux<String> streamAskWithRag(String question) { // 简化版流式问答
        return streamChat(question, null); // 委托给完整版streamChat
    }

    /**
     * 获取知识库统计信息（Controller专用方法）
     * 返回文档数量、支持格式、文件大小限制等信息
     *
     * @return Map<String, Object> 统计信息
     */
    public Map<String, Object> getDocumentStats() { // 公开方法：知识库统计
        Map<String, Object> stats = new HashMap<>(); // 创建统计Map
        stats.put("estimatedDocumentCount", getDocumentCount()); // 放入文档总数
        stats.put("supportedFormats", java.util.Set.of("pdf", "doc", "docx", "txt", "md", "html")); // 放入支持的格式集合
        stats.put("maxFileSize", 10 * 1024 * 1024); // 放入最大文件限制（10MB，单位字节）
        return stats; // 返回统计信息
    }
}
