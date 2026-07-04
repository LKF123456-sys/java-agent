package com.ailearn.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统用户实体类
 * 对应数据库表：sys_user
 * 用于存储系统用户的基本信息，包括登录凭证、昵称、角色等
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class User {

    /**
     * 用户唯一标识ID
     * 主键，自增策略
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     * 用于登录的唯一账号名称
     */
    private String username;

    /**
     * 密码
     * 加密存储的用户登录密码
     */
    private String password;

    /**
     * 用户昵称
     * 用于显示的用户友好名称
     */
    private String nickname;

    /**
     * 用户角色
     * 用于权限控制，如：admin(管理员)、user(普通用户)
     */
    private String role;

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
