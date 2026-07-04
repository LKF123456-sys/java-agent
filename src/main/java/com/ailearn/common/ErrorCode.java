package com.ailearn.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码枚举类
 * 定义系统中所有的业务错误码，按照模块分类：
 * - 1xxx: 认证授权相关错误 (AUTH)
 * - 2xxx: 用户相关错误 (USER)
 * - 3xxx: 聊天相关错误 (CHAT)
 * - 4xxx: Agent相关错误 (AGENT)
 * - 5xxx: RAG知识库相关错误 (RAG)
 * - 6xxx: MCP协议相关错误 (MCP)
 * - 9xxx: 系统级错误 (SYSTEM)
 *
 * @author AiLearn Platform
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ==================== 1xxx: 认证授权相关错误 (AUTH) ====================
    /**
     * 认证失败：用户名或密码错误
     */
    AUTH_LOGIN_FAILED(1001, "用户名或密码错误"),

    /**
     * 认证失败：Token无效或已过期
     */
    AUTH_TOKEN_INVALID(1002, "Token无效或已过期"),

    /**
     * 认证失败：Token缺失
     */
    AUTH_TOKEN_MISSING(1003, "未提供认证Token"),

    /**
     * 认证失败：Token已被注销
     */
    AUTH_TOKEN_REVOKED(1004, "Token已被注销"),

    /**
     * 权限不足：访问被拒绝
     */
    AUTH_ACCESS_DENIED(1005, "权限不足，无法访问该资源"),

    /**
     * 认证失败：账号已被禁用
     */
    AUTH_ACCOUNT_DISABLED(1006, "账号已被禁用"),

    /**
     * 认证失败：账号已被锁定
     */
    AUTH_ACCOUNT_LOCKED(1007, "账号已被锁定"),

    /**
     * 认证失败：验证码错误
     */
    AUTH_CAPTCHA_ERROR(1008, "验证码错误或已过期"),

    // ==================== 2xxx: 用户相关错误 (USER) ====================
    /**
     * 用户不存在
     */
    USER_NOT_FOUND(2001, "用户不存在"),

    /**
     * 用户名已存在
     */
    USER_USERNAME_EXISTS(2002, "用户名已被注册"),

    /**
     * 邮箱已存在
     */
    USER_EMAIL_EXISTS(2003, "邮箱已被注册"),

    /**
     * 旧密码错误
     */
    USER_OLD_PASSWORD_ERROR(2004, "旧密码输入错误"),

    /**
     * 两次密码输入不一致
     */
    USER_PASSWORD_MISMATCH(2005, "两次输入的密码不一致"),

    /**
     * 用户资料更新失败
     */
    USER_UPDATE_FAILED(2006, "用户资料更新失败"),

    /**
     * 用户头像上传失败
     */
    USER_AVATAR_UPLOAD_FAILED(2007, "用户头像上传失败"),

    // ==================== 3xxx: 聊天相关错误 (CHAT) ====================
    /**
     * 会话不存在
     */
    CHAT_CONVERSATION_NOT_FOUND(3001, "会话不存在"),

    /**
     * 消息不存在
     */
    CHAT_MESSAGE_NOT_FOUND(3002, "消息不存在"),

    /**
     * 发送消息失败
     */
    CHAT_SEND_FAILED(3003, "消息发送失败"),

    /**
     * AI模型调用失败
     */
    CHAT_AI_CALL_FAILED(3004, "AI模型调用失败"),

    /**
     * 会话创建失败
     */
    CHAT_CONVERSATION_CREATE_FAILED(3005, "会话创建失败"),

    /**
     * 会话删除失败
     */
    CHAT_CONVERSATION_DELETE_FAILED(3006, "会话删除失败"),

    /**
     * 消息内容为空
     */
    CHAT_MESSAGE_EMPTY(3007, "消息内容不能为空"),

    /**
     * 消息内容过长
     */
    CHAT_MESSAGE_TOO_LONG(3008, "消息内容超过长度限制"),

    /**
     * 流式响应中断
     */
    CHAT_STREAM_INTERRUPTED(3009, "流式响应被中断"),

    // ==================== 4xxx: Agent相关错误 (AGENT) ====================
    /**
     * Agent不存在
     */
    AGENT_NOT_FOUND(4001, "Agent不存在"),

    /**
     * Agent创建失败
     */
    AGENT_CREATE_FAILED(4002, "Agent创建失败"),

    /**
     * Agent更新失败
     */
    AGENT_UPDATE_FAILED(4003, "Agent更新失败"),

    /**
     * Agent删除失败
     */
    AGENT_DELETE_FAILED(4004, "Agent删除失败"),

    /**
     * Agent执行失败
     */
    AGENT_EXECUTE_FAILED(4005, "Agent执行失败"),

    /**
     * Agent配置无效
     */
    AGENT_CONFIG_INVALID(4006, "Agent配置无效"),

    /**
     * 多Agent协作失败
     */
    AGENT_MULTI_COLLABORATION_FAILED(4007, "多Agent协作失败"),

    /**
     * Agent工具调用失败
     */
    AGENT_TOOL_CALL_FAILED(4008, "Agent工具调用失败"),

    // ==================== 5xxx: RAG知识库相关错误 (RAG) ====================
    /**
     * 知识库不存在
     */
    RAG_KNOWLEDGE_BASE_NOT_FOUND(5001, "知识库不存在"),

    /**
     * 文档不存在
     */
    RAG_DOCUMENT_NOT_FOUND(5002, "文档不存在"),

    /**
     * 文档上传失败
     */
    RAG_DOCUMENT_UPLOAD_FAILED(5003, "文档上传失败"),

    /**
     * 文档解析失败
     */
    RAG_DOCUMENT_PARSE_FAILED(5004, "文档解析失败"),

    /**
     * 文档向量化失败
     */
    RAG_EMBEDDING_FAILED(5005, "文档向量化失败"),

    /**
     * 知识库检索失败
     */
    RAG_RETRIEVAL_FAILED(5006, "知识库检索失败"),

    /**
     * 文档删除失败
     */
    RAG_DOCUMENT_DELETE_FAILED(5007, "文档删除失败"),

    /**
     * 文件格式不支持
     */
    RAG_FILE_FORMAT_UNSUPPORTED(5008, "不支持的文件格式"),

    /**
     * 文件大小超限
     */
    RAG_FILE_TOO_LARGE(5009, "文件大小超过限制"),

    /**
     * 文件为空
     */
    RAG_DOCUMENT_EMPTY(5010, "上传文件或文档内容不能为空"),

    /**
     * 文件读取失败
     */
    RAG_FILE_READ_FAILED(5011, "文件读取失败"),

    // ==================== 6xxx: MCP协议相关错误 (MCP) ====================
    /**
     * MCP服务不存在
     */
    MCP_SERVER_NOT_FOUND(6001, "MCP服务不存在"),

    /**
     * MCP服务连接失败
     */
    MCP_CONNECTION_FAILED(6002, "MCP服务连接失败"),

    /**
     * MCP工具不存在
     */
    MCP_TOOL_NOT_FOUND(6003, "MCP工具不存在"),

    /**
     * MCP工具调用失败
     */
    MCP_TOOL_CALL_FAILED(6004, "MCP工具调用失败"),

    /**
     * MCP资源不存在
     */
    MCP_RESOURCE_NOT_FOUND(6005, "MCP资源不存在"),

    /**
     * MCP资源读取失败
     */
    MCP_RESOURCE_READ_FAILED(6006, "MCP资源读取失败"),

    /**
     * MCP协议版本不兼容
     */
    MCP_PROTOCOL_INCOMPATIBLE(6007, "MCP协议版本不兼容"),

    /**
     * MCP服务注册失败
     */
    MCP_REGISTER_FAILED(6008, "MCP服务注册失败"),

    // ==================== 9xxx: 系统级错误 (SYSTEM) ====================
    /**
     * 系统内部错误
     */
    SYSTEM_INTERNAL_ERROR(9001, "系统内部错误"),

    /**
     * 服务暂不可用
     */
    SYSTEM_SERVICE_UNAVAILABLE(9002, "服务暂不可用，请稍后重试"),

    /**
     * 数据库操作失败
     */
    SYSTEM_DATABASE_ERROR(9003, "数据库操作失败"),

    /**
     * 参数校验失败
     */
    SYSTEM_PARAM_VALIDATION_ERROR(9004, "参数校验失败"),

    /**
     * 请求参数格式错误
     */
    SYSTEM_PARAM_FORMAT_ERROR(9005, "请求参数格式错误"),

    /**
     * 请求方法不支持
     */
    SYSTEM_METHOD_NOT_ALLOWED(9006, "请求方法不支持"),

    /**
     * 请求路径不存在
     */
    SYSTEM_NOT_FOUND(9007, "请求的资源不存在"),

    /**
     * 请求过于频繁
     */
    SYSTEM_RATE_LIMIT_EXCEEDED(9008, "请求过于频繁，请稍后重试"),

    /**
     * 文件操作失败
     */
    SYSTEM_FILE_OPERATION_FAILED(9009, "文件操作失败"),

    /**
     * 网络请求失败
     */
    SYSTEM_NETWORK_ERROR(9010, "网络请求失败"),

    /**
     * 序列化/反序列化失败
     */
    SYSTEM_SERIALIZATION_ERROR(9011, "数据序列化失败"),

    /**
     * 第三方服务调用失败
     */
    SYSTEM_THIRD_PARTY_ERROR(9012, "第三方服务调用失败");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;
}
