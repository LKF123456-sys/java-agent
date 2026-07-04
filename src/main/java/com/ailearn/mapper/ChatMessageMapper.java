package com.ailearn.mapper;

import com.ailearn.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息数据访问层接口
 * 继承MyBatis-Plus的BaseMapper，提供chat_message表的基础CRUD操作
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
