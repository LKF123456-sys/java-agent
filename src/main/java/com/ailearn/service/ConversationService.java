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

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;

    private final ChatMessageMapper chatMessageMapper;

    @Transactional(rollbackFor = Exception.class)
    public Conversation createConversation(@NotNull(message = "用户ID不能为空") Long userId,
                                            @NotBlank(message = "会话标题不能为空") String title,
                                            @NotBlank(message = "会话类型不能为空") String type) {
        log.info("创建新会话: userId={}, title={}, type={}", userId, title, type);

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setType(type);

        conversationMapper.insert(conversation);

        log.info("会话创建成功: conversationId={}, userId={}", conversation.getId(), userId);
        return conversation;
    }

    public List<Conversation> getConversations(@NotNull(message = "用户ID不能为空") Long userId,
                                                @NotBlank(message = "会话类型不能为空") String type) {
        log.debug("查询会话列表: userId={}, type={}", userId, type);

        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
                .eq(Conversation::getType, type)
                .orderByDesc(Conversation::getUpdatedAt);

        List<Conversation> conversations = conversationMapper.selectList(wrapper);
        log.debug("查询到{}个会话: userId={}, type={}", conversations.size(), userId, type);

        return conversations;
    }

    public Conversation getConversationById(@NotNull(message = "用户ID不能为空") Long userId,
                                             @NotNull(message = "会话ID不能为空") Long id) {
        log.debug("查询会话详情: userId={}, conversationId={}", userId, id);

        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            log.warn("查询会话失败，会话不存在: conversationId={}", id);
            throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
        }

        validateConversationOwnership(userId, conversation);

        return conversation;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(@NotNull(message = "用户ID不能为空") Long userId,
                                    @NotNull(message = "会话ID不能为空") Long id) {
        log.info("删除会话: userId={}, conversationId={}", userId, id);

        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            log.warn("删除会话失败，会话不存在: conversationId={}", id);
            throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
        }

        validateConversationOwnership(userId, conversation);

        conversationMapper.deleteById(id);

        LambdaQueryWrapper<ChatMessage> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(ChatMessage::getConversationId, id);
        int deletedMessages = chatMessageMapper.delete(messageWrapper);

        log.info("会话删除成功: userId={}, conversationId={}, 共删除{}条消息", userId, id, deletedMessages);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChatMessage saveMessage(@NotNull(message = "用户ID不能为空") Long userId,
                                    @NotNull(message = "会话ID不能为空") Long conversationId,
                                    @NotBlank(message = "消息角色不能为空") String role,
                                    @NotBlank(message = "消息内容不能为空") String content) {
        log.debug("保存聊天消息: userId={}, conversationId={}, role={}, contentLength={}",
                userId, conversationId, role, content.length());

        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            log.warn("保存消息失败，会话不存在: conversationId={}", conversationId);
            throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
        }

        validateConversationOwnership(userId, conversation);

        ChatMessage message = new ChatMessage();
        message.setUserId(userId);
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);

        chatMessageMapper.insert(message);

        conversationMapper.updateById(conversation);

        log.debug("消息保存成功: messageId={}, userId={}, conversationId={}", message.getId(), userId, conversationId);
        return message;
    }

    public List<ChatMessage> getMessages(@NotNull(message = "用户ID不能为空") Long userId,
                                          @NotNull(message = "会话ID不能为空") Long conversationId) {
        log.debug("查询会话消息: userId={}, conversationId={}", userId, conversationId);

        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            log.warn("查询消息失败，会话不存在: conversationId={}", conversationId);
            throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
        }

        validateConversationOwnership(userId, conversation);

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreatedAt);

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
        log.debug("查询到{}条消息: userId={}, conversationId={}", messages.size(), userId, conversationId);

        return messages;
    }

    public Conversation getConversation(@NotNull(message = "用户ID不能为空") Long userId,
                                         @NotNull(message = "会话ID不能为空") Long id) {
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation != null) {
            validateConversationOwnership(userId, conversation);
        }
        return conversation;
    }

    public List<Conversation> listConversations(@NotNull(message = "用户ID不能为空") Long userId,
                                                 @NotBlank(message = "会话类型不能为空") String type) {
        return getConversations(userId, type);
    }

    private void validateConversationOwnership(Long userId, Conversation conversation) {
        if (conversation.getUserId() == null || !conversation.getUserId().equals(userId)) {
            log.warn("会话所有权验证失败: userId={}, conversationUserId={}, conversationId={}",
                    userId, conversation.getUserId(), conversation.getId());
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
    }
}
