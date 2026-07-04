package com.ailearn.mapper;

import com.ailearn.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话数据访问层接口
 * 继承MyBatis-Plus的BaseMapper，提供conversation表的基础CRUD操作
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
