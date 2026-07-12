package com.ailearn.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息实体类
 * 对应数据库表：chat_message
 * 用于存储会话中的单条聊天消息，包括用户提问和AI回复
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage {

    /**
     * 消息唯一标识ID
     * 主键，自增策略
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     * 关联所属的会话，对应conversation表的id字段
     */
    private Long conversationId;

    /**
     * 所属用户ID
     * 关联sys_user表的id字段，实现用户数据隔离
     */
    private Long userId;

    /**
     * 消息角色
     * 标识消息发送者身份：user(用户)、assistant(AI助手)、system(系统)
     */
    private String role;

    /**
     * 消息内容
     * 具体的文本消息内容
     */
    private String content;

    /**
     * 创建时间
     * 记录消息插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
