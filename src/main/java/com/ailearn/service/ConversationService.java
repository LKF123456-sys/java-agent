package com.ailearn.service;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.entity.ChatMessage;
import com.ailearn.entity.Conversation;
import com.ailearn.mapper.ChatMessageMapper;
import com.ailearn.mapper.ConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 会话服务类
 * 提供对话会话的创建、查询、删除以及聊天消息的保存和查询功能
 * 一个会话（Conversation）包含多条聊天消息（ChatMessage），支持多轮对话管理
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class ConversationService {

    /**
     * 会话数据访问接口
     * 用于conversation表的CRUD操作
     */
    private final ConversationMapper conversationMapper;

    /**
     * 聊天消息数据访问接口
     * 用于chat_message表的CRUD操作
     */
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 创建新会话
     * 根据传入的标题和类型创建一个空的对话会话，用于后续的聊天交互
     *
     * @param title 会话标题，用于展示给用户识别会话内容，不能为空
     * @param type  会话类型，用于区分不同场景的对话：
     *              - chat: 普通聊天
     *              - agent: Agent智能体对话
     *              - rag: RAG知识库问答
     *              - memory: 带记忆的多轮对话
     *              不能为空
     * @return Conversation 创建后的会话实体（包含自增ID和创建时间）
     */
    @Transactional(rollbackFor = Exception.class)
    public Conversation createConversation(@NotBlank(message = "会话标题不能为空") String title,
                                            @NotBlank(message = "会话类型不能为空") String type) {
        log.info("创建新会话: title={}, type={}", title, type);

        // 构建会话实体对象
        Conversation conversation = new Conversation();
        conversation.setTitle(title);
        conversation.setType(type);

        // 插入数据库，MyBatis-Plus自动回填自增ID和自动填充字段（createdAt/updatedAt）
        conversationMapper.insert(conversation);

        log.info("会话创建成功: conversationId={}", conversation.getId());
        return conversation;
    }

    /**
     * 查询会话列表
     * 根据会话类型查询所有会话，按创建时间（更新时间）倒序排列，最新的会话排在前面
     *
     * @param type 会话类型，用于过滤不同场景的会话，不能为空
     * @return List&lt;Conversation&gt; 符合条件的会话列表，按更新时间倒序排列
     */
    public List<Conversation> getConversations(@NotBlank(message = "会话类型不能为空") String type) {
        log.debug("查询会话列表: type={}", type);

        // 使用Lambda查询构造器构建查询条件
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getType, type)
                .orderByDesc(Conversation::getUpdatedAt);

        List<Conversation> conversations = conversationMapper.selectList(wrapper);
        log.debug("查询到{}个会话: type={}", conversations.size(), type);

        return conversations;
    }

    /**
     * 根据ID查询会话详情
     * 查询单个会话的详细信息，如果会话不存在则抛出业务异常
     *
     * @param id 会话ID，不能为空
     * @return Conversation 会话实体对象
     * @throws BusinessException 当会话不存在时抛出CHAT_CONVERSATION_NOT_FOUND异常
     */
    public Conversation getConversationById(@NotNull(message = "会话ID不能为空") Long id) {
        log.debug("查询会话详情: conversationId={}", id);

        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            log.warn("查询会话失败，会话不存在: conversationId={}", id);
            throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
        }

        return conversation;
    }

    /**
     * 删除会话及其关联的所有消息
     * 级联删除操作，在事务中同时删除会话记录和该会话下的所有聊天消息，保证数据一致性
     * 删除前会先验证会话是否存在
     *
     * @param id 要删除的会话ID，不能为空
     * @throws BusinessException 当会话不存在时抛出CHAT_CONVERSATION_NOT_FOUND异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(@NotNull(message = "会话ID不能为空") Long id) {
        log.info("删除会话: conversationId={}", id);

        // 先验证会话是否存在
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            log.warn("删除会话失败，会话不存在: conversationId={}", id);
            throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
        }

        // 删除会话记录
        conversationMapper.deleteById(id);

        // 删除该会话下的所有聊天消息
        LambdaQueryWrapper<ChatMessage> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(ChatMessage::getConversationId, id);
        int deletedMessages = chatMessageMapper.delete(messageWrapper);

        log.info("会话删除成功: conversationId={}, 共删除{}条消息", id, deletedMessages);
    }

    /**
     * 保存聊天消息
     * 向指定会话中保存一条聊天消息（用户提问或AI回复），
     * 同时触发会话的updatedAt字段自动更新（由MyBatis-Plus自动填充或更新操作触发）
     *
     * @param conversationId 会话ID，消息所属的会话，不能为空
     * @param role           消息角色，标识消息发送者身份：
     *                       - user: 用户发送的消息
     *                       - assistant: AI助手回复的消息
     *                       - system: 系统消息
     *                       不能为空
     * @param content        消息文本内容，不能为空
     * @return ChatMessage 保存后的消息实体（包含自增ID和创建时间）
     * @throws BusinessException 当会话不存在时抛出CHAT_CONVERSATION_NOT_FOUND异常
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage saveMessage(@NotNull(message = "会话ID不能为空") Long conversationId,
                                    @NotBlank(message = "消息角色不能为空") String role,
                                    @NotBlank(message = "消息内容不能为空") String content) {
        log.debug("保存聊天消息: conversationId={}, role={}, contentLength={}",
                conversationId, role, content.length());

        // 验证会话是否存在，防止向不存在的会话保存消息
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            log.warn("保存消息失败，会话不存在: conversationId={}", conversationId);
            throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
        }

        // 构建消息实体对象
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);

        // 插入消息到数据库
        chatMessageMapper.insert(message);

        // 更新会话的更新时间（通过updateById触发自动填充，确保会话列表按最新消息排序）
        // 这里执行一次空更新以触发updatedAt字段自动更新
        conversationMapper.updateById(conversation);

        log.debug("消息保存成功: messageId={}, conversationId={}", message.getId(), conversationId);
        return message;
    }

    /**
     * 查询会话的所有消息
     * 获取指定会话中的所有聊天消息，按创建时间正序排列（从早到晚，即对话的时间顺序）
     *
     * @param conversationId 会话ID，不能为空
     * @return List&lt;ChatMessage&gt; 该会话下的所有消息列表，按创建时间正序排列
     */
    public List<ChatMessage> getMessages(@NotNull(message = "会话ID不能为空") Long conversationId) {
        log.debug("查询会话消息: conversationId={}", conversationId);

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreatedAt);

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
        log.debug("查询到{}条消息: conversationId={}", messages.size(), conversationId);

        return messages;
    }

    /**
     * 根据ID查询会话（内部方法，保留原有方法名兼容旧代码）
     *
     * @param id 会话ID
     * @return Conversation 会话实体对象，不存在则返回null
     */
    public Conversation getConversation(@NotNull(message = "会话ID不能为空") Long id) {
        return conversationMapper.selectById(id);
    }

    /**
     * 查询会话列表（内部方法，保留原有方法名兼容旧代码）
     *
     * @param type 会话类型
     * @return List&lt;Conversation&gt; 会话列表
     */
    public List<Conversation> listConversations(@NotBlank(message = "会话类型不能为空") String type) {
        return getConversations(type);
    }
}
