package com.ailearn.service; // 声明包名，service包存放业务逻辑层

import com.ailearn.common.BusinessException; // 业务异常类，抛出可预期的业务错误
import com.ailearn.common.ErrorCode; // 错误码枚举，标准化错误码定义
import com.ailearn.entity.ChatMessage; // 聊天消息实体，对应数据库chat_message表
import com.ailearn.entity.Conversation; // 会话实体，对应数据库conversation表
import com.ailearn.mapper.ChatMessageMapper; // 消息表MyBatis Mapper接口
import com.ailearn.mapper.ConversationMapper; // 会话表MyBatis Mapper接口
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus条件构造器，用Lambda拼SQL条件
import jakarta.validation.constraints.NotBlank; // 参数校验注解，字符串不能为空白
import jakarta.validation.constraints.NotNull; // 参数校验注解，对象不能为null
import lombok.RequiredArgsConstructor; // Lombok注解，为final字段生成构造器（实现依赖注入）
import lombok.extern.slf4j.Slf4j; // Lombok注解，自动生成log日志对象
import org.springframework.stereotype.Service; // Spring注解，标记为业务层组件
import org.springframework.transaction.annotation.Transactional; // Spring事务注解，标记方法为事务
import org.springframework.validation.annotation.Validated; // Spring注解，开启方法级参数校验

import java.util.List; // Java列表接口

/**
 * 会话管理服务
 *
 * <p>负责"会话"和"消息"两个实体的全部业务逻辑：创建/查询/删除会话、保存/查询消息。
 * 是聊天、记忆对话、Agent、RAG等多个模块共享的基础设施。
 *
 * <p>核心安全设计：所有涉及"按会话ID取数据"的方法都会校验"会话属于当前用户"，
 * 防止用户A越权读取用户B的对话——这是多租户数据隔离的基本要求。
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log对象
@Service // 标记为Spring业务层Bean
@Validated // 开启方法级校验，使方法参数上的@NotNull/@NotBlank生效
@RequiredArgsConstructor // 为所有final字段生成构造器，Spring据此完成依赖注入
public class ConversationService { // 会话服务类定义

    private final ConversationMapper conversationMapper; // 会话表Mapper，由Spring通过构造器注入

    private final ChatMessageMapper chatMessageMapper; // 消息表Mapper，由Spring通过构造器注入

    /**
     * 创建新会话
     *
     * <p>用@Transactional保证事务：插入失败时整个操作回滚。
     *
     * @param userId 当前用户ID（不能为null）
     * @param title  会话标题（不能为空白，用于列表展示）
     * @param type   会话类型（chat/memory/agent/rag等，用于分类筛选）
     * @return 插入成功后的会话对象（含数据库生成的id）
     */
    @Transactional(rollbackFor = Exception.class) // 开启事务，任意Exception都回滚
    public Conversation createConversation(@NotNull(message = "用户ID不能为空") Long userId, // 校验userId非空
                                            @NotBlank(message = "会话标题不能为空") String title, // 校验title非空白
                                            @NotBlank(message = "会话类型不能为空") String type) { // 校验type非空白
        log.info("创建新会话: userId={}, title={}, type={}", userId, title, type); // 记录业务日志

        Conversation conversation = new Conversation(); // 创建会话实体对象
        conversation.setUserId(userId); // 设置用户ID（外键关联）
        conversation.setTitle(title); // 设置会话标题
        conversation.setType(type); // 设置会话类型

        conversationMapper.insert(conversation); // 调用Mapper插入数据库，insert后conversation.getId()自动填充

        log.info("会话创建成功: conversationId={}, userId={}", conversation.getId(), userId); // 记录成功日志
        return conversation; // 返回含主键id的会话对象
    }

    /**
     * 按用户和类型查询会话列表
     *
     * @param userId 当前用户ID
     * @param type   会话类型
     * @return 该用户该类型的会话列表（按最后更新时间倒序）
     */
    public List<Conversation> getConversations(@NotNull(message = "用户ID不能为空") Long userId, // 校验userId
                                                @NotBlank(message = "会话类型不能为空") String type) { // 校验type
        log.debug("查询会话列表: userId={}, type={}", userId, type); // 调试日志

        // 用Lambda构造查询条件，避免硬编码字段名字符串（编译期检查字段名，重构友好）
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>(); // 创建条件构造器
        wrapper.eq(Conversation::getUserId, userId) // 条件1：user_id = 当前用户
                .eq(Conversation::getType, type) // 条件2：type = 指定类型
                .orderByDesc(Conversation::getUpdatedAt); // 按更新时间倒序（最新在最前）

        List<Conversation> conversations = conversationMapper.selectList(wrapper); // 执行查询
        log.debug("查询到{}个会话: userId={}, type={}", conversations.size(), userId, type); // 调试日志

        return conversations; // 返回会话列表
    }

    /**
     * 按ID查询单个会话（带所有权校验）
     *
     * @param userId 当前用户ID
     * @param id     会话ID
     * @return 会话对象
     * @throws BusinessException 会话不存在或不属于当前用户时抛出
     */
    public Conversation getConversationById(@NotNull(message = "用户ID不能为空") Long userId, // 校验userId
                                             @NotNull(message = "会话ID不能为空") Long id) { // 校验id
        log.debug("查询会话详情: userId={}, conversationId={}", userId, id); // 调试日志

        Conversation conversation = conversationMapper.selectById(id); // 按主键查询
        if (conversation == null) { // 会话不存在
            log.warn("查询会话失败，会话不存在: conversationId={}", id); // 警告日志
            throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND); // 抛业务异常
        }

        validateConversationOwnership(userId, conversation); // 校验会话是否属于当前用户

        return conversation; // 返回会话对象
    }

    /**
     * 删除会话及其所有消息（事务）
     *
     * <p>先删会话，再删该会话下的全部消息；整体在一个事务内，保证一致性。
     *
     * @param userId 当前用户ID
     * @param id     会话ID
     * @throws BusinessException 会话不存在或不属于当前用户时抛出
     */
    @Transactional(rollbackFor = Exception.class) // 事务：删会话+删消息要么全成功要么全回滚
    public void deleteConversation(@NotNull(message = "用户ID不能为空") Long userId, // 校验userId
                                    @NotNull(message = "会话ID不能为空") Long id) { // 校验id
        log.info("删除会话: userId={}, conversationId={}", userId, id); // 业务日志

        Conversation conversation = conversationMapper.selectById(id); // 先查出会话
        if (conversation == null) { // 会话不存在
            log.warn("删除会话失败，会话不存在: conversationId={}", id); // 警告日志
            throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND); // 抛异常
        }

        validateConversationOwnership(userId, conversation); // 校验所有权，防止越权删除

        conversationMapper.deleteById(id); // 删除会话记录

        // 删除该会话下的全部消息：用条件构造器指定conversation_id
        LambdaQueryWrapper<ChatMessage> messageWrapper = new LambdaQueryWrapper<>(); // 条件构造器
        messageWrapper.eq(ChatMessage::getConversationId, id); // 条件：conversation_id = 要删的会话
        int deletedMessages = chatMessageMapper.delete(messageWrapper); // 批量删除，返回删除条数

        log.info("会话删除成功: userId={}, conversationId={}, 共删除{}条消息", userId, id, deletedMessages); // 成功日志
    }

    /**
     * 保存一条聊天消息（事务）
     *
     * <p>保存消息后顺便"触碰"会话记录，触发其update_time更新，
     * 让会话列表按最近活跃时间排序。
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID
     * @param role           消息角色（user/assistant/system）
     * @param content        消息文本内容
     * @return 保存后的消息对象（含主键id）
     * @throws BusinessException 会话不存在或不属于当前用户时抛出
     */
    @Transactional(rollbackFor = Exception.class) // 事务
    public ChatMessage saveMessage(@NotNull(message = "用户ID不能为空") Long userId, // 校验userId
                                    @NotNull(message = "会话ID不能为空") Long conversationId, // 校验会话ID
                                    @NotBlank(message = "消息角色不能为空") String role, // 校验role
                                    @NotBlank(message = "消息内容不能为空") String content) { // 校验content
        log.debug("保存聊天消息: userId={}, conversationId={}, role={}, contentLength={}", // 调试日志
                userId, conversationId, role, content.length());

        Conversation conversation = conversationMapper.selectById(conversationId); // 查会话
        if (conversation == null) { // 会话不存在
            log.warn("保存消息失败，会话不存在: conversationId={}", conversationId); // 警告日志
            throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND); // 抛异常
        }

        validateConversationOwnership(userId, conversation); // 校验所有权

        ChatMessage message = new ChatMessage(); // 创建消息实体
        message.setUserId(userId); // 设置用户ID
        message.setConversationId(conversationId); // 设置会话ID（外键）
        message.setRole(role); // 设置角色（user/assistant/system）
        message.setContent(content); // 设置消息内容

        chatMessageMapper.insert(message); // 插入消息记录

        conversationMapper.updateById(conversation); // 更新会话记录（触发updated_at自动更新）

        log.debug("消息保存成功: messageId={}, userId={}, conversationId={}", message.getId(), userId, conversationId); // 调试日志
        return message; // 返回含主键的消息对象
    }

    /**
     * 查询某会话的全部历史消息（按时间正序）
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID
     * @return 消息列表（最早的消息在前）
     * @throws BusinessException 会话不存在或不属于当前用户时抛出
     */
    public List<ChatMessage> getMessages(@NotNull(message = "用户ID不能为空") Long userId, // 校验userId
                                          @NotNull(message = "会话ID不能为空") Long conversationId) { // 校验会话ID
        log.debug("查询会话消息: userId={}, conversationId={}", userId, conversationId); // 调试日志

        Conversation conversation = conversationMapper.selectById(conversationId); // 查会话
        if (conversation == null) { // 会话不存在
            log.warn("查询消息失败，会话不存在: conversationId={}", conversationId); // 警告日志
            throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND); // 抛异常
        }

        validateConversationOwnership(userId, conversation); // 校验所有权

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>(); // 条件构造器
        wrapper.eq(ChatMessage::getConversationId, conversationId) // 条件：属于该会话
                .orderByAsc(ChatMessage::getCreatedAt); // 按创建时间正序（对话顺序）

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper); // 执行查询
        log.debug("查询到{}条消息: userId={}, conversationId={}", messages.size(), userId, conversationId); // 调试日志

        return messages; // 返回消息列表
    }

    /**
     * 获取会话（不做严格校验的版本，供内部调用）
     *
     * <p>与getConversationById的区别：会话不存在时返回null而非抛异常，
     * 适用于"可能不存在"的场景。
     *
     * @param userId 当前用户ID
     * @param id     会话ID
     * @return 会话对象；不存在返回null；存在但不属于该用户也返回null（安全降级）
     */
    public Conversation getConversation(@NotNull(message = "用户ID不能为空") Long userId, // 校验userId
                                         @NotNull(message = "会话ID不能为空") Long id) { // 校验id
        Conversation conversation = conversationMapper.selectById(id); // 按主键查询
        if (conversation != null) { // 如果存在
            validateConversationOwnership(userId, conversation); // 校验所有权
        }
        return conversation; // 返回会话对象或null
    }

    /**
     * 列出某用户某类型的会话（listConversations是getConversations的别名）
     *
     * @param userId 当前用户ID
     * @param type   会话类型
     * @return 会话列表
     */
    public List<Conversation> listConversations(@NotNull(message = "用户ID不能为空") Long userId, // 校验userId
                                                 @NotBlank(message = "会话类型不能为空") String type) { // 校验type
        return getConversations(userId, type); // 委托给getConversations
    }

    /**
     * 校验会话所有权（私有工具方法）
     *
     * <p>多租户数据隔离的核心：会话的userId必须与当前请求的userId一致，
     * 否则视为越权访问，抛AUTH_ACCESS_DENIED。
     *
     * @param userId       当前请求的用户ID
     * @param conversation 待校验的会话对象
     * @throws BusinessException 会话不属于该用户时抛出
     */
    private void validateConversationOwnership(Long userId, Conversation conversation) { // 私有方法
        if (conversation.getUserId() == null || !conversation.getUserId().equals(userId)) { // 用户ID为空或不匹配
            log.warn("会话所有权验证失败: userId={}, conversationUserId={}, conversationId={}", // 警告日志
                    userId, conversation.getUserId(), conversation.getId());
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED); // 抛权限不足异常
        }
    }
} // ConversationService类结束
