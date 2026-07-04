package com.ailearn.service;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.entity.ChatMessage;
import com.ailearn.entity.Conversation;
import com.ailearn.mapper.ChatMessageMapper;
import com.ailearn.mapper.ConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

/**
 * ConversationService单元测试类
 * 使用Mockito框架mock依赖（ConversationMapper、ChatMessageMapper）
 * 独立运行，不依赖Spring容器和外部数据库
 *
 * @author AiLearn Platform
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("会话服务测试")
class ConversationServiceTest {

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @InjectMocks
    private ConversationService conversationService;

    private static final Long TEST_CONVERSATION_ID = 1L;
    private static final String TEST_TITLE = "测试会话";
    private static final String TEST_TYPE = "chat";
    private static final String TEST_USER_MESSAGE = "你好";
    private static final String TEST_ASSISTANT_MESSAGE = "你好！有什么可以帮助你的吗？";

    /**
     * 测试创建会话成功场景
     */
    @Test
    @DisplayName("创建会话 - 成功场景")
    void testCreateConversation_Success() {
        // Mock：insert操作后回填ID
        doAnswer(invocation -> {
            Conversation conv = invocation.getArgument(0);
            conv.setId(TEST_CONVERSATION_ID);
            return 1;
        }).when(conversationMapper).insert(any(Conversation.class));

        // 执行
        Conversation result = conversationService.createConversation(TEST_TITLE, TEST_TYPE);

        // 验证
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.getId());
        assertEquals(TEST_TITLE, result.getTitle());
        assertEquals(TEST_TYPE, result.getType());

        // 验证insert被调用且参数正确
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).insert(captor.capture());
        assertEquals(TEST_TITLE, captor.getValue().getTitle());
        assertEquals(TEST_TYPE, captor.getValue().getType());
    }

    /**
     * 测试查询会话列表
     */
    @Test
    @DisplayName("查询会话列表 - 按类型查询")
    void testGetConversations() {
        // 准备：模拟返回会话列表
        Conversation conv1 = new Conversation();
        conv1.setId(1L);
        conv1.setTitle("会话1");
        Conversation conv2 = new Conversation();
        conv2.setId(2L);
        conv2.setTitle("会话2");
        when(conversationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(conv1, conv2));

        // 执行
        List<Conversation> result = conversationService.getConversations(TEST_TYPE);

        // 验证
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(conversationMapper).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 测试根据ID查询会话成功
     */
    @Test
    @DisplayName("根据ID查询会话 - 成功场景")
    void testGetConversationById_Success() {
        // Mock
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        conv.setTitle(TEST_TITLE);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv);

        // 执行
        Conversation result = conversationService.getConversationById(TEST_CONVERSATION_ID);

        // 验证
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.getId());
        assertEquals(TEST_TITLE, result.getTitle());
    }

    /**
     * 测试根据ID查询会话不存在场景
     */
    @Test
    @DisplayName("根据ID查询会话 - 会话不存在应抛出异常")
    void testGetConversationById_NotFound() {
        // Mock：会话不存在
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(null);

        // 执行&验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.getConversationById(TEST_CONVERSATION_ID));
        assertEquals(ErrorCode.CHAT_CONVERSATION_NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 测试删除会话成功场景
     */
    @Test
    @DisplayName("删除会话 - 成功场景（级联删除消息）")
    void testDeleteConversation_Success() {
        // Mock：会话存在
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv);
        // Mock：删除消息返回删除数量
        when(chatMessageMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(5);

        // 执行
        conversationService.deleteConversation(TEST_CONVERSATION_ID);

        // 验证：会话被删除
        verify(conversationMapper).deleteById(TEST_CONVERSATION_ID);
        // 验证：相关消息被删除
        verify(chatMessageMapper).delete(any(LambdaQueryWrapper.class));
    }

    /**
     * 测试删除不存在的会话
     */
    @Test
    @DisplayName("删除会话 - 会话不存在应抛出异常")
    void testDeleteConversation_NotFound() {
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.deleteConversation(TEST_CONVERSATION_ID));
        assertEquals(ErrorCode.CHAT_CONVERSATION_NOT_FOUND.getCode(), exception.getCode());

        // 验证：delete未被调用
        verify(conversationMapper, never()).deleteById(isA(Serializable.class));
        verify(chatMessageMapper, never()).delete(isA(LambdaQueryWrapper.class));
    }

    /**
     * 测试保存消息成功场景
     */
    @Test
    @DisplayName("保存消息 - 成功场景")
    void testSaveMessage_Success() {
        // Mock：会话存在
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv);
        // Mock：消息insert后回填ID
        doAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            return 1;
        }).when(chatMessageMapper).insert(any(ChatMessage.class));

        // 执行
        ChatMessage result = conversationService.saveMessage(TEST_CONVERSATION_ID, "user", TEST_USER_MESSAGE);

        // 验证
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.getConversationId());
        assertEquals("user", result.getRole());
        assertEquals(TEST_USER_MESSAGE, result.getContent());

        // 验证：消息被保存
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(captor.capture());
        assertEquals(TEST_USER_MESSAGE, captor.getValue().getContent());
        assertEquals("user", captor.getValue().getRole());
    }

    /**
     * 测试向不存在的会话保存消息
     */
    @Test
    @DisplayName("保存消息 - 会话不存在应抛出异常")
    void testSaveMessage_ConversationNotFound() {
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.saveMessage(TEST_CONVERSATION_ID, "user", TEST_USER_MESSAGE));
        assertEquals(ErrorCode.CHAT_CONVERSATION_NOT_FOUND.getCode(), exception.getCode());

        verify(chatMessageMapper, never()).insert(isA(ChatMessage.class));
    }

    /**
     * 测试查询会话消息
     */
    @Test
    @DisplayName("查询会话消息 - 按创建时间正序排列")
    void testGetMessages() {
        // 准备：模拟返回消息列表
        ChatMessage msg1 = new ChatMessage();
        msg1.setId(1L);
        msg1.setRole("user");
        msg1.setContent(TEST_USER_MESSAGE);
        ChatMessage msg2 = new ChatMessage();
        msg2.setId(2L);
        msg2.setRole("assistant");
        msg2.setContent(TEST_ASSISTANT_MESSAGE);
        when(chatMessageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(msg1, msg2));

        // 执行
        List<ChatMessage> result = conversationService.getMessages(TEST_CONVERSATION_ID);

        // 验证
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("user", result.get(0).getRole());
        assertEquals("assistant", result.get(1).getRole());
        verify(chatMessageMapper).selectList(any(LambdaQueryWrapper.class));
    }
}
