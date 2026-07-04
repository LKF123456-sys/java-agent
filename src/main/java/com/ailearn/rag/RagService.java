package com.ailearn.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * RAG Service
 *
 * Spring AI RAG 核心组件：
 *
 * VectorStore - 向量数据库抽象接口
 *   - SimpleVectorStore（内存，无需外部依赖）
 *   - PgVectorStore（PostgreSQL + pgvector）
 *   - PineconeVectorStore（Pinecone 云服务）
 *   - ChromaVectorStore（Chroma 开源）
 *   - MilvusVectorStore（Milvus 开源）
 *
 * EmbeddingModel - 将文本转换为向量
 *   - OpenAI text-embedding-3-small/large
 *   - OllamaEmbeddingModel（本地模型）
 *   - TransformersEmbeddingModel（本地 Java 推理，无需GPU）
 *
 * QuestionAnswerAdvisor - RAG Advisor
 *   自动在每次对话前：
 *   1. 对用户问题进行向量化
 *   2. 在 VectorStore 中搜索相似文档
 *   3. 将检索结果注入 System Prompt
 */
@Service
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = builder
                .defaultSystem("""
                        你是一个专业的知识问答助手。
                        请仅基于提供的上下文信息回答问题。
                        如果上下文中没有相关信息，请明确告知用户你不知道，不要编造答案。
                        """)
                // QuestionAnswerAdvisor 自动完成检索+注入
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore,
                        SearchRequest.builder().topK(5).build()))  // 检索最相关的5个片段
                .build();
    }

    /**
     * 将文本文档添加到向量库
     * Document 是 Spring AI 的文档抽象，支持元数据
     */
    public void addDocument(String content, String source) {
        Document doc = new Document(
                content,
                Map.of("source", source, "timestamp", System.currentTimeMillis())
        );
        vectorStore.add(List.of(doc));
    }

    /**
     * 基于 RAG 的问答
     * QuestionAnswerAdvisor 会自动检索相关文档并注入上下文
     */
    public String askWithRag(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}