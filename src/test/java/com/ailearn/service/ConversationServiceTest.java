package com.ailearn.service;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.entity.ChatMessage;
import com.ailearn.entity.Conversation;
import com.ailearn.mapper.ChatMessageMapper;
import com.ailearn.mapper.ConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 会话服务单元测试类
 * 使用Mockito框架mock依赖（ConversationMapper、ChatMessageMapper）
 * 测试会话创建、查询、删除、消息保存、所有权验证等功能
 *
 * @author AiLearn Platform
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("会话服务测试")
class ConversationServiceTest {

    /**
     * Mock会话数据访问接口
     */
    @Mock
    private ConversationMapper conversationMapper;

    /**
     * Mock聊天消息数据访问接口
     */
    @Mock
    private ChatMessageMapper chatMessageMapper;

    /**
     * 被测试的ConversationService实例，@InjectMocks自动注入mock依赖
     */
    @InjectMocks
    private ConversationService conversationService;

    /**
     * 测试用户ID
     */
    private static final Long TEST_USER_ID = 1L;

    /**
     * 测试其他用户ID（用于测试权限验证）
     */
    private static final Long OTHER_USER_ID = 2L;

    /**
     * 测试会话ID
     */
    private static final Long TEST_CONVERSATION_ID = 1L;

    /**
     * 测试会话标题
     */
    private static final String TEST_TITLE = "测试会话";

    /**
     * 测试会话类型
     */
    private static final String TEST_TYPE = "chat";

    /**
     * 测试用户消息内容
     */
    private static final String TEST_USER_MESSAGE = "你好";

    /**
     * 测试助手消息内容
     */
    private static final String TEST_ASSISTANT_MESSAGE = "你好！有什么可以帮助你的吗？";

    /**
     * 每个测试方法执行前的初始化
     */
    @BeforeEach
    void setUp() {
        // 初始化工作（如有需要）
    }

    /**
     * 测试创建会话成功场景
     * 验证：会话被正确创建、用户ID正确设置、ID回填正常
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

        // 执行：创建会话
        Conversation result = conversationService.createConversation(TEST_USER_ID, TEST_TITLE, TEST_TYPE);

        // 验证：返回结果不为空
        assertNotNull(result, "创建的会话不应为空");
        // 验证：ID正确回填
        assertEquals(TEST_CONVERSATION_ID, result.getId(), "会话ID应正确回填");
        // 验证：标题正确
        assertEquals(TEST_TITLE, result.getTitle(), "会话标题应正确");
        // 验证：类型正确
        assertEquals(TEST_TYPE, result.getType(), "会话类型应正确");
        // 验证：用户ID正确
        assertEquals(TEST_USER_ID, result.getUserId(), "会话所属用户ID应正确");

        // 验证：insert被调用且参数正确
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).insert(captor.capture());
        assertEquals(TEST_TITLE, captor.getValue().getTitle(), "插入的会话标题应正确");
        assertEquals(TEST_TYPE, captor.getValue().getType(), "插入的会话类型应正确");
        assertEquals(TEST_USER_ID, captor.getValue().getUserId(), "插入的会话用户ID应正确");
    }

    /**
     * 测试查询用户会话列表
     * 验证：只返回指定用户、指定类型的会话
     */
    @Test
    @DisplayName("查询会话列表 - 按用户和类型查询")
    void testGetConversations() {
        // 准备：模拟返回会话列表
        Conversation conv1 = new Conversation();
        conv1.setId(1L);
        conv1.setUserId(TEST_USER_ID);
        conv1.setTitle("会话1");
        Conversation conv2 = new Conversation();
        conv2.setId(2L);
        conv2.setUserId(TEST_USER_ID);
        conv2.setTitle("会话2");
        when(conversationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(conv1, conv2));

        // 执行：查询会话列表
        List<Conversation> result = conversationService.getConversations(TEST_USER_ID, TEST_TYPE);

        // 验证：结果不为空
        assertNotNull(result, "查询结果不应为空");
        // 验证：结果数量正确
        assertEquals(2, result.size(), "应返回2个会话");
        // 验证：selectList被调用
        verify(conversationMapper).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 测试根据ID查询会话成功场景
     * 验证：能正确查询属于自己的会话
     */
    @Test
    @DisplayName("根据ID查询会话 - 成功场景")
    void testGetConversationById_Success() {
        // Mock：会话存在且属于当前用户
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(TEST_USER_ID);
        conv.setTitle(TEST_TITLE);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv);

        // 执行：查询会话
        Conversation result = conversationService.getConversationById(TEST_USER_ID, TEST_CONVERSATION_ID);

        // 验证：结果不为空
        assertNotNull(result, "查询结果不应为空");
        // 验证：ID正确
        assertEquals(TEST_CONVERSATION_ID, result.getId(), "会话ID应正确");
        // 验证：标题正确
        assertEquals(TEST_TITLE, result.getTitle(), "会话标题应正确");
    }

    /**
     * 测试根据ID查询不存在的会话
     * 验证：抛出CHAT_CONVERSATION_NOT_FOUND异常
     */
    @Test
    @DisplayName("根据ID查询会话 - 会话不存在应抛出异常")
    void testGetConversationById_NotFound() {
        // Mock：会话不存在
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(null);

        // 执行&验证：应抛出BusinessException
        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.getConversationById(TEST_USER_ID, TEST_CONVERSATION_ID),
                "会话不存在时应抛出BusinessException");
        // 验证：错误码正确
        assertEquals(ErrorCode.CHAT_CONVERSATION_NOT_FOUND.getCode(), exception.getCode(),
                "错误码应为CHAT_CONVERSATION_NOT_FOUND");
    }

    /**
     * 测试查询他人会话时权限不足
     * 验证：抛出AUTH_ACCESS_DENIED异常
     */
    @Test
    @DisplayName("根据ID查询会话 - 访问他人会话应抛出权限不足异常")
    void testGetConversationById_AccessDenied() {
        // Mock：会话存在但属于其他用户
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(OTHER_USER_ID);
        conv.setTitle(TEST_TITLE);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv);

        // 执行&验证：应抛出权限不足异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.getConversationById(TEST_USER_ID, TEST_CONVERSATION_ID),
                "访问他人会话应抛出BusinessException");
        // 验证：错误码为AUTH_ACCESS_DENIED
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED.getCode(), exception.getCode(),
                "错误码应为AUTH_ACCESS_DENIED");
    }

    /**
     * 测试删除会话成功场景
     * 验证：会话和关联消息被级联删除
     */
    @Test
    @DisplayName("删除会话 - 成功场景（级联删除消息）")
    void testDeleteConversation_Success() {
        // Mock：会话存在且属于当前用户
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(TEST_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv);
        // Mock：删除消息返回删除数量
        when(chatMessageMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(5);

        // 执行：删除会话
        conversationService.deleteConversation(TEST_USER_ID, TEST_CONVERSATION_ID);

        // 验证：会话被删除
        verify(conversationMapper).deleteById(TEST_CONVERSATION_ID);
        // 验证：相关消息被删除
        verify(chatMessageMapper).delete(any(LambdaQueryWrapper.class));
    }

    /**
     * 测试删除不存在的会话
     * 验证：抛出CHAT_CONVERSATION_NOT_FOUND异常
     */
    @Test
    @DisplayName("删除会话 - 会话不存在应抛出异常")
    void testDeleteConversation_NotFound() {
        // Mock：会话不存在
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(null);

        // 执行&验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.deleteConversation(TEST_USER_ID, TEST_CONVERSATION_ID),
                "删除不存在的会话应抛出异常");
        assertEquals(ErrorCode.CHAT_CONVERSATION_NOT_FOUND.getCode(), exception.getCode());

        // 验证：delete未被调用
        verify(conversationMapper, never()).deleteById(TEST_CONVERSATION_ID);
        verify(chatMessageMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    /**
     * 测试删除他人会话时权限不足
     * 验证：抛出AUTH_ACCESS_DENIED异常
     */
    @Test
    @DisplayName("删除会话 - 删除他人会话应抛出权限不足异常")
    void testDeleteConversation_AccessDenied() {
        // Mock：会话存在但属于其他用户
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(OTHER_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv);

        // 执行&验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.deleteConversation(TEST_USER_ID, TEST_CONVERSATION_ID),
                "删除他人会话应抛出权限不足异常");
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED.getCode(), exception.getCode());

        // 验证：delete未被调用
        verify(conversationMapper, never()).deleteById(TEST_CONVERSATION_ID);
    }

    /**
     * 测试保存消息成功场景
     * 验证：消息被正确保存，关联会话和用户
     */
    @Test
    @DisplayName("保存消息 - 成功场景")
    void testSaveMessage_Success() {
        // Mock：会话存在且属于当前用户
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(TEST_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv);
        // Mock：消息insert后回填ID
        doAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            return 1;
        }).when(chatMessageMapper).insert(any(ChatMessage.class));

        // 执行：保存消息
        ChatMessage result = conversationService.saveMessage(TEST_USER_ID, TEST_CONVERSATION_ID, "user", TEST_USER_MESSAGE);

        // 验证：返回结果不为空
        assertNotNull(result, "保存的消息不应为空");
        // 验证：会话ID正确
        assertEquals(TEST_CONVERSATION_ID, result.getConversationId(), "消息所属会话ID应正确");
        // 验证：用户ID正确
        assertEquals(TEST_USER_ID, result.getUserId(), "消息所属用户ID应正确");
        // 验证：角色正确
        assertEquals("user", result.getRole(), "消息角色应正确");
        // 验证：内容正确
        assertEquals(TEST_USER_MESSAGE, result.getContent(), "消息内容应正确");

        // 验证：消息被保存
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(captor.capture());
        assertEquals(TEST_USER_MESSAGE, captor.getValue().getContent(), "插入的消息内容应正确");
        assertEquals("user", captor.getValue().getRole(), "插入的消息角色应正确");
        assertEquals(TEST_USER_ID, captor.getValue().getUserId(), "插入的消息用户ID应正确");
    }

    /**
     * 测试向不存在的会话保存消息
     * 验证：抛出CHAT_CONVERSATION_NOT_FOUND异常
     */
    @Test
    @DisplayName("保存消息 - 会话不存在应抛出异常")
    void testSaveMessage_ConversationNotFound() {
        // Mock：会话不存在
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(null);

        // 执行&验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.saveMessage(TEST_USER_ID, TEST_CONVERSATION_ID, "user", TEST_USER_MESSAGE),
                "向不存在的会话保存消息应抛出异常");
        assertEquals(ErrorCode.CHAT_CONVERSATION_NOT_FOUND.getCode(), exception.getCode());

        // 验证：insert未被调用
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    /**
     * 测试向他人会话保存消息
     * 验证：抛出AUTH_ACCESS_DENIED异常
     */
    @Test
    @DisplayName("保存消息 - 向他人会话保存消息应抛出权限不足异常")
    void testSaveMessage_AccessDenied() {
        // Mock：会话存在但属于其他用户
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(OTHER_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv);

        // 执行&验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.saveMessage(TEST_USER_ID, TEST_CONVERSATION_ID, "user", TEST_USER_MESSAGE),
                "向他人会话保存消息应抛出权限不足异常");
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED.getCode(), exception.getCode());

        // 验证：insert未被调用
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    /**
     * 测试查询会话消息
     * 验证：按创建时间正序返回消息列表
     */
    @Test
    @DisplayName("查询会话消息 - 按创建时间正序排列")
    void testGetMessages() {
        // Mock：会话存在且属于当前用户
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(TEST_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv);
        // 准备：模拟返回消息列表
        ChatMessage msg1 = new ChatMessage();
        msg1.setId(1L);
        msg1.setConversationId(TEST_CONVERSATION_ID);
        msg1.setRole("user");
        msg1.setContent(TEST_USER_MESSAGE);
        ChatMessage msg2 = new ChatMessage();
        msg2.setId(2L);
        msg2.setConversationId(TEST_CONVERSATION_ID);
        msg2.setRole("assistant");
        msg2.setContent(TEST_ASSISTANT_MESSAGE);
        when(chatMessageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(msg1, msg2));

        // 执行：查询消息
        List<ChatMessage> result = conversationService.getMessages(TEST_USER_ID, TEST_CONVERSATION_ID);

        // 验证：结果不为空
        assertNotNull(result, "查询结果不应为空");
        // 验证：消息数量正确
        assertEquals(2, result.size(), "应返回2条消息");
        // 验证：消息顺序正确（用户消息在前）
        assertEquals("user", result.get(0).getRole(), "第一条消息应为用户消息");
        assertEquals("assistant", result.get(1).getRole(), "第二条消息应为助手消息");
        // 验证：selectList被调用
        verify(chatMessageMapper).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 测试查询他人会话消息
     * 验证：抛出AUTH_ACCESS_DENIED异常
     */
    @Test
    @DisplayName("查询会话消息 - 查询他人会话消息应抛出权限不足异常")
    void testGetMessages_AccessDenied() {
        // Mock：会话存在但属于其他用户
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(OTHER_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv);

        // 执行&验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.getMessages(TEST_USER_ID, TEST_CONVERSATION_ID),
                "查询他人会话消息应抛出权限不足异常");
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED.getCode(), exception.getCode());

        // 验证：selectList未被调用
        verify(chatMessageMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }
}
