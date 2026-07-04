package com.ailearn.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * AI核心配置类
 * 负责配置Spring AI相关的核心Bean，主要包括向量存储（VectorStore）的自动选择
 * 根据当前数据源类型自动判断使用PgVector（PostgreSQL）还是SimpleVectorStore（内存/MySQL等）
 *
 * @author AiLearn Platform
 */
@Slf4j
@Configuration
public class AiConfig {

    /**
     * 创建VectorStore向量存储Bean
     * 根据DataSource的JDBC URL自动判断底层数据库类型：
     * - 检测到PostgreSQL（URL包含"postgresql"）时，创建PgVectorStore实例，支持持久化向量存储
     * - 其他情况（MySQL、H2等）创建SimpleVectorStore内存实例，适合开发测试
     *
     * PgVectorStore配置说明：
     * - dimensions: 向量维度，nomic-embed-text模型输出为768维
     * - distanceType: 使用余弦距离（COSINE_DISTANCE）计算向量相似度，适合文本语义匹配
     * - indexType: 使用HNSW（Hierarchical Navigable Small World）索引，查询效率高
     * - initializeSchema: 自动创建向量表结构和索引，首次启动时设为true
     *
     * @Primary注解确保当存在多个VectorStore Bean时，优先注入此Bean
     *
     * @param dataSource     数据源（由Spring Boot自动注入，通常为HikariDataSource）
     * @param embeddingModel 嵌入模型（由Spring AI Ollama自动配置，用于文本向量化）
     * @return VectorStore 向量存储实例
     */
    @Bean
    @Primary
    public VectorStore vectorStore(DataSource dataSource, EmbeddingModel embeddingModel) {
        // 将通用DataSource强转为HikariDataSource以获取JDBC连接URL
        // Spring Boot默认使用HikariCP连接池，因此此转换在标准配置下是安全的
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        // 获取实际的JDBC连接URL字符串
        String jdbcUrl = hikariDataSource.getJdbcUrl();
        // 记录当前检测到的数据库URL，便于排查配置问题
        log.info("检测到数据源JDBC URL: {}", jdbcUrl);

        // 判断是否为PostgreSQL数据库（PgVector是PostgreSQL的扩展）
        if (jdbcUrl != null && jdbcUrl.contains("postgresql")) {
            // 检测到PostgreSQL，记录日志说明使用PgVector持久化存储
            log.info("检测到PostgreSQL数据库，使用PgVectorStore持久化向量存储（dimensions=768, COSINE距离, HNSW索引）");
            // 创建JdbcTemplate用于PgVectorStore执行SQL操作
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            // 使用Builder模式构建PgVectorStore实例
            return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                    // 设置向量维度为768，对应nomic-embed-text嵌入模型的输出维度
                    .dimensions(768)
                    // 设置距离类型为余弦距离，范围[-1,1]，值越大越相似，适合语义搜索
                    .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                    // 设置索引类型为HNSW，这是一种高效的近似最近邻搜索索引
                    // 相比IVFFlat索引，HNSW在高召回率下有更好的查询性能，但构建稍慢
                    .indexType(PgVectorStore.PgIndexType.HNSW)
                    // 设为true时，启动时自动创建vector_store表和向量索引
                    // 生产环境首次部署后可改为false以避免重复DDL执行
                    .initializeSchema(true)
                    // 构建并返回PgVectorStore实例
                    .build();
        }

        // 非PostgreSQL数据库（MySQL、H2等），使用SimpleVectorStore内存存储
        // SimpleVectorStore将向量存储在内存中，重启后数据丢失，适合开发测试环境
        // 注意：生产环境使用MySQL时，向量数据无法持久化，建议切换到PostgreSQL+PgVector
        log.info("使用SimpleVectorStore内存向量存储（非PostgreSQL环境，向量数据不持久化）");
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
