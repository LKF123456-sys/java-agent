package com.ailearn.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("rag_document")
public class RagDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String docId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private Integer chunkCount;

    private Long totalChars;

    private String filePath;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
