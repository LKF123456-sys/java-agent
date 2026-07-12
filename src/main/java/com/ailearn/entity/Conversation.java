package com.ailearn.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话实体类
 * 对应数据库表：conversation
 * 用于存储用户与AI的对话会话信息，一个会话包含多条聊天消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("conversation")
public class Conversation {

    /**
     * 会话唯一标识ID
     * 主键，自增策略
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     * 关联sys_user表的id字段，实现用户数据隔离
     */
    private Long userId;

    /**
     * 会话标题
     * 用于展示的会话名称，通常由用户自定义或系统生成
     */
    private String title;

    /**
     * 会话类型
     * 区分不同类型的对话场景，如：chat(普通聊天)、agent(Agent任务)、rag(RAG问答)等
     */
    private String type;

    /**
     * 创建时间
     * 记录首次插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * 记录插入和更新时自动填充
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
