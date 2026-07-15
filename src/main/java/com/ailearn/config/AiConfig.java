package com.ailearn.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * AI核心配置类
 * 负责配置Spring AI相关的核心Bean，主要包括向量存储（VectorStore）的配置
 * 支持两种运行模式：
 * 1. Docker环境（PostgreSQL+PgVector）：使用Spring AI自动配置PgVectorStore，本类作为fallback
 * 2. 本地开发环境（MySQL/内存）：手动创建SimpleVectorStore内存实例
 *
 * @author AiLearn Platform
 */
@Slf4j
@Configuration
public class AiConfig {

    /**
     * 创建VectorStore向量存储Bean
     *
     * 配置策略：
     * - 使用@ConditionalOnMissingBean确保只有当Spring AI自动配置没有创建VectorStore时才使用此配置
     * - 在Docker/PostgreSQL环境下：Spring AI PgVectorStoreAutoConfiguration会自动创建PgVectorStore
     * - 在本地开发/MySQL环境下：自动配置不会生效（pgvector.enabled=false），由本方法创建SimpleVectorStore
     *
     * PgVectorStore配置（在docker profile中通过application.yml配置）：
     * - dimensions: 向量维度，nomic-embed-text模型输出为768维
     * - distanceType: 使用余弦距离（COSINE_DISTANCE）计算向量相似度，适合文本语义匹配
     * - indexType: 使用HNSW索引，查询效率高
     * - initializeSchema: 自动创建向量表结构和索引
     *
     * @param dataSource     数据源（由Spring Boot自动注入，通常为HikariDataSource）
     * @param embeddingModel 嵌入模型（由Spring AI Ollama自动配置，用于文本向量化）
     * @return VectorStore 向量存储实例
     */
    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnProperty(name = "spring.ai.vectorstore.pgvector.enabled", havingValue = "false", matchIfMissing = true)
    public VectorStore localVectorStore(DataSource dataSource, EmbeddingModel embeddingModel) {
        // 将通用DataSource强转为HikariDataSource以获取JDBC连接URL
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        // 获取实际的JDBC连接URL字符串
        String jdbcUrl = hikariDataSource.getJdbcUrl();
        // 记录当前检测到的数据库URL，便于排查配置问题
        log.info("检测到数据源JDBC URL: {}", jdbcUrl);

        // 判断是否为PostgreSQL数据库（PgVector是PostgreSQL的扩展）
        if (jdbcUrl != null && jdbcUrl.contains("postgresql")) {
            // 检测到PostgreSQL，创建PgVectorStore实例
            // 注意：在docker环境下，Spring AI自动配置会优先创建此Bean，@ConditionalOnMissingBean会确保此方法不执行
            // 此处作为fallback，当自动配置未生效时手动创建
            log.info("检测到PostgreSQL数据库，手动创建PgVectorStore持久化向量存储（dimensions=768, COSINE距离, HNSW索引）");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                    .dimensions(768)
                    .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                    .indexType(PgVectorStore.PgIndexType.HNSW)
                    .initializeSchema(true)
                    .build();
        }

        // 非PostgreSQL数据库（MySQL、H2等），使用SimpleVectorStore内存存储
        // SimpleVectorStore将向量存储在内存中，重启后数据丢失，适合开发测试环境
        log.info("使用SimpleVectorStore内存向量存储（非PostgreSQL环境，向量数据不持久化）");
        log.info("提示：如需持久化向量存储，请使用PostgreSQL+PgVector（docker profile默认配置）");
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
