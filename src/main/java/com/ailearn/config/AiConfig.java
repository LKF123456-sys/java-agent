package com.ailearn.config; // 声明当前类所在的包：config（配置层）

// 导入HikariCP连接池的数据源类（Spring Boot默认连接池），用于获取JDBC连接URL
import com.zaxxer.hikari.HikariDataSource;
// 导入Lombok日志注解，自动生成log对象
import lombok.extern.slf4j.Slf4j;
// 导入Spring AI的嵌入模型接口，负责把文本转成向量（本项目是Ollama的nomic-embed-text）
import org.springframework.ai.embedding.EmbeddingModel;
// 导入简单向量存储（内存版），本地开发环境用，重启后数据丢失
import org.springframework.ai.vectorstore.SimpleVectorStore;
// 导入向量存储接口，VectorStore是RAG检索的核心抽象
import org.springframework.ai.vectorstore.VectorStore;
// 导入PgVector向量存储（PostgreSQL持久化版），生产/Docker环境用
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
// 导入条件注解：只有当容器中缺少某个Bean时本配置才生效（避免覆盖自动配置）
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
// 导入条件注解：根据配置属性值决定Bean是否生效
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
// 导入@Bean注解，把方法返回值注册为Spring容器中的Bean
import org.springframework.context.annotation.Bean;
// 导入@Configuration注解，标记这是配置类
import org.springframework.context.annotation.Configuration;
// 导入@Primary注解（预留，当前未使用，多个同类型Bean时标记首选）
import org.springframework.context.annotation.Primary;
// 导入JdbcTemplate，Spring的JDBC操作模板（PgVectorStore需要它操作数据库）
import org.springframework.jdbc.core.JdbcTemplate;

// 导入DataSource接口，代表数据库连接池
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
@Slf4j // Lombok注解：自动生成log日志对象
@Configuration // 标记为Spring配置类，启动时被扫描并执行其中的@Bean方法
public class AiConfig { // 定义AI核心配置类

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
    @Bean // 把方法返回的VectorStore注册为Spring Bean，供RagService注入使用
    @ConditionalOnMissingBean(VectorStore.class) // 条件1：容器里还没有VectorStore时才执行（Docker下自动配置已创建，本方法跳过）
    @ConditionalOnProperty(name = "spring.ai.vectorstore.pgvector.enabled", havingValue = "false", matchIfMissing = true) // 条件2：pgvector明确关闭或未配置时才生效（matchIfMissing=true表示没配置该属性也算匹配）
    public VectorStore localVectorStore(DataSource dataSource, EmbeddingModel embeddingModel) { // 参数由Spring自动注入
        // 将通用DataSource强转为HikariDataSource以获取JDBC连接URL
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource; // Spring Boot默认用HikariCP，强转是安全的
        // 获取实际的JDBC连接URL字符串（如jdbc:mysql://...或jdbc:postgresql://...）
        String jdbcUrl = hikariDataSource.getJdbcUrl();
        // 记录当前检测到的数据库URL，便于排查配置问题
        log.info("检测到数据源JDBC URL: {}", jdbcUrl);

        // 判断是否为PostgreSQL数据库（PgVector是PostgreSQL的扩展）
        if (jdbcUrl != null && jdbcUrl.contains("postgresql")) { // URL中含postgresql关键字即为PG环境
            // 检测到PostgreSQL，创建PgVectorStore实例
            // 注意：在docker环境下，Spring AI自动配置会优先创建此Bean，@ConditionalOnMissingBean会确保此方法不执行
            // 此处作为fallback，当自动配置未生效时手动创建
            log.info("检测到PostgreSQL数据库，手动创建PgVectorStore持久化向量存储（dimensions=768, COSINE距离, HNSW索引）");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource); // 用数据源创建JdbcTemplate（PgVectorStore靠它执行SQL）
            return PgVectorStore.builder(jdbcTemplate, embeddingModel) // 用构建器模式创建PgVectorStore，传入JDBC模板和嵌入模型
                    .dimensions(768) // 向量维度768：必须和nomic-embed-text模型的输出维度一致，否则存取会报错
                    .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE) // 相似度用余弦距离：文本语义检索的标准选择（关注方向而非长度）
                    .indexType(PgVectorStore.PgIndexType.HNSW) // 索引用HNSW（分层导航小世界图）：近似最近邻搜索，百万级向量也能毫秒响应
                    .initializeSchema(true) // 自动建表：首次启动时自动创建vector_store表和索引，免去手工执行DDL
                    .build(); // 构建完成，返回PgVectorStore实例
        }

        // 非PostgreSQL数据库（MySQL、H2等），使用SimpleVectorStore内存存储
        // SimpleVectorStore将向量存储在内存中，重启后数据丢失，适合开发测试环境
        log.info("使用SimpleVectorStore内存向量存储（非PostgreSQL环境，向量数据不持久化）");
        log.info("提示：如需持久化向量存储，请使用PostgreSQL+PgVector（docker profile默认配置）");
        return SimpleVectorStore.builder(embeddingModel).build(); // 只需嵌入模型即可构建内存向量库（本地开发零依赖，开箱即用）
    }
}
