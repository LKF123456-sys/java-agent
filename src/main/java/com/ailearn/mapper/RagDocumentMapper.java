package com.ailearn.mapper;

import com.ailearn.entity.RagDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG知识库文档数据访问层接口
 * 继承MyBatis-Plus的BaseMapper，提供rag_document表的基础CRUD操作
 * 用于管理RAG（检索增强生成）知识库中的文档存储
 */
@Mapper
public interface RagDocumentMapper extends BaseMapper<RagDocument> {
}
