-- ============================================
-- MySQL 业务表 (当前默认使用)
-- ============================================

CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL COMMENT '会话标题',
    type VARCHAR(50) NOT NULL COMMENT '会话类型：chat/memory/rag/agent/structured',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(50) NOT NULL COMMENT '角色：user/assistant/system',
    content TEXT NOT NULL COMMENT '消息内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL DEFAULT '' COMMENT '密码（BCrypt加密）',
    nickname VARCHAR(100) COMMENT '昵称',
    role VARCHAR(50) NOT NULL DEFAULT 'user' COMMENT '角色：admin/user',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS rag_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id VARCHAR(64) NOT NULL UNIQUE COMMENT '文档UUID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type VARCHAR(50) COMMENT '文件类型(pdf/docx/txt/md等)',
    file_size BIGINT COMMENT '文件大小(字节)',
    chunk_count INT DEFAULT 0 COMMENT '分块数量',
    total_chars BIGINT DEFAULT 0 COMMENT '总字符数',
    file_path VARCHAR(512) COMMENT '存储路径',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG知识库文档表';

-- ============================================
-- PostgreSQL + pgvector 向量存储参考 (切换数据库时使用)
-- ============================================
-- 注意：以下为 PostgreSQL 语法，在使用 PgVector 时执行
-- Spring AI PgVectorStore 会自动创建表结构，以下仅作参考

-- 1. 启用 vector 扩展（需要先安装 pgvector）
-- CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Spring AI 自动创建的向量表结构参考
-- CREATE TABLE IF NOT EXISTS vector_store (
--     id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
--     content text,
--     metadata json,
--     embedding vector(768)  -- 维度需与 embedding 模型匹配 (nomic-embed-text: 768)
-- );

-- 3. 创建向量索引（用于相似度搜索加速）
-- CREATE INDEX ON vector_store USING hnsw (embedding vector_cosine_ops);
