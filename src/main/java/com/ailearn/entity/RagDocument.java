package com.ailearn.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RAG知识库文档实体类
 * 对应数据库表：rag_document
 * 用于存储上传到RAG知识库的文档元数据信息，包括文件名称、类型、大小、分块信息等
 *
 * @author AiLearn Platform
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("rag_document")
public class RagDocument {

    /**
     * 文档唯一标识ID
     * 主键，自增策略
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文档业务ID
     * 用于RAG系统内部标识文档的唯一字符串ID，通常为UUID或类似格式
     */
    private String docId;

    /**
     * 文件原始名称
     * 用户上传时的原始文件名，包含扩展名
     */
    private String fileName;

    /**
     * 文件类型
     * 文件的MIME类型或扩展名，如：pdf、docx、txt、md等
     */
    private String fileType;

    /**
     * 文件大小
     * 文件的字节大小
     */
    private Long fileSize;

    /**
     * 文档分块数量
     * 文档被切分后的文本块（chunk）总数，用于向量化存储和检索
     */
    private Integer chunkCount;

    /**
     * 文档总字符数
     * 文档解析后的总字符数量，用于统计和展示
     */
    private Long totalChars;

    /**
     * 文件存储路径
     * 文档在服务器文件系统中的存储路径
     */
    private String filePath;

    /**
     * 创建时间
     * 记录文档上传插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
