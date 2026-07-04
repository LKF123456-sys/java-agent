package com.ailearn.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 核心配置
 *
 * 1. VectorStore：为 RAG 模块提供内存向量数据库
 *    SimpleVectorStore 适合学习，不依赖外部服务
 *    生产环境替换为 PgVector / Milvus / Pinecone 等
 *
 * 2. Function Beans：工具函数通过 @Component + @Description 自动注册
 *    Bean 名称即 .functions("beanName") 中使用的名称
 */
@Configuration
public class AiConfig {

    /**
     * 内存向量存储
     * EmbeddingModel 由 Spring AI AutoConfiguration 自动提供
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}