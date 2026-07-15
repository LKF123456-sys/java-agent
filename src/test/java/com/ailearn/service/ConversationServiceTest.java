package com.ailearn.service; // 声明包名

import com.ailearn.common.BusinessException; // 业务异常类
import com.ailearn.common.ErrorCode; // 错误码枚举
import com.ailearn.entity.ChatMessage; // 聊天消息实体
import com.ailearn.entity.Conversation; // 会话实体
import com.ailearn.mapper.ChatMessageMapper; // 聊天消息Mapper
import com.ailearn.mapper.ConversationMapper; // 会话Mapper
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus Lambda查询构造器
import org.junit.jupiter.api.BeforeEach; // JUnit前置方法注解
import org.junit.jupiter.api.DisplayName; // JUnit显示名称注解
import org.junit.jupiter.api.Test; // JUnit测试方法注解
import org.junit.jupiter.api.extension.ExtendWith; // JUnit扩展注解
import org.mockito.ArgumentCaptor; // Mockito参数捕获器
import org.mockito.InjectMocks; // Mockito自动注入注解
import org.mockito.Mock; // Mockito创建Mock注解
import org.mockito.junit.jupiter.MockitoExtension; // Mockito JUnit 5扩展

import java.util.Arrays; // 数组工具类
import java.util.List; // List接口

import static org.junit.jupiter.api.Assertions.*; // JUnit断言静态导入
import static org.mockito.ArgumentMatchers.any; // Mockito参数匹配器
import static org.mockito.Mockito.*; // Mockito静态导入

@ExtendWith(MockitoExtension.class) // 启用Mockito扩展
@DisplayName("会话服务测试") // 测试类显示名称
class ConversationServiceTest { // 会话服务测试类

    @Mock // 创建ConversationMapper Mock对象
    private ConversationMapper conversationMapper; // Mock会话数据访问接口

    @Mock // 创建ChatMessageMapper Mock对象
    private ChatMessageMapper chatMessageMapper; // Mock聊天消息数据访问接口

    @InjectMocks // 自动注入Mock依赖到被测服务
    private ConversationService conversationService; // 被测会话服务实例

    private static final Long TEST_USER_ID = 1L; // 测试用户ID
    private static final Long OTHER_USER_ID = 2L; // 其他用户ID（用于权限测试）
    private static final Long TEST_CONVERSATION_ID = 1L; // 测试会话ID
    private static final String TEST_TITLE = "测试会话"; // 测试会话标题
    private static final String TEST_TYPE = "chat"; // 测试会话类型
    private static final String TEST_USER_MESSAGE = "你好"; // 测试用户消息
    private static final String TEST_ASSISTANT_MESSAGE = "你好！有什么可以帮助你的吗？"; // 测试助手消息

    @BeforeEach // 每个测试前执行
    void setUp() { // 初始化方法
    } // setUp方法结束

    @Test // 测试方法
    @DisplayName("创建会话 - 成功场景")
    void testCreateConversation_Success() { // 测试创建会话成功
        doAnswer(invocation -> { // Mock insert操作回填ID
            Conversation conv = invocation.getArgument(0); // 获取传入的会话参数
            conv.setId(TEST_CONVERSATION_ID); // 设置会话ID
            return 1; // 返回影响行数
        }).when(conversationMapper).insert(any(Conversation.class)); // 当调用insert时执行

        Conversation result = conversationService.createConversation(TEST_USER_ID, TEST_TITLE, TEST_TYPE); // 执行创建会话

        assertNotNull(result, "创建的会话不应为空"); // 结果不为空
        assertEquals(TEST_CONVERSATION_ID, result.getId(), "会话ID应正确回填"); // ID正确
        assertEquals(TEST_TITLE, result.getTitle(), "会话标题应正确"); // 标题正确
        assertEquals(TEST_TYPE, result.getType(), "会话类型应正确"); // 类型正确
        assertEquals(TEST_USER_ID, result.getUserId(), "会话所属用户ID应正确"); // 用户ID正确

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class); // 捕获参数
        verify(conversationMapper).insert(captor.capture()); // 验证insert调用并捕获
        assertEquals(TEST_TITLE, captor.getValue().getTitle(), "插入的会话标题应正确"); // 标题正确
        assertEquals(TEST_TYPE, captor.getValue().getType(), "插入的会话类型应正确"); // 类型正确
        assertEquals(TEST_USER_ID, captor.getValue().getUserId(), "插入的会话用户ID应正确"); // 用户ID正确
    } // testCreateConversation_Success方法结束

    @Test
    @DisplayName("查询会话列表 - 按用户和类型查询")
    void testGetConversations() { // 测试查询会话列表
        Conversation conv1 = new Conversation(); // 构造会话1
        conv1.setId(1L);
        conv1.setUserId(TEST_USER_ID);
        conv1.setTitle("会话1");
        Conversation conv2 = new Conversation(); // 构造会话2
        conv2.setId(2L);
        conv2.setUserId(TEST_USER_ID);
        conv2.setTitle("会话2");
        when(conversationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(conv1, conv2)); // Mock查询返回两个会话

        List<Conversation> result = conversationService.getConversations(TEST_USER_ID, TEST_TYPE); // 执行查询

        assertNotNull(result, "查询结果不应为空"); // 结果不为空
        assertEquals(2, result.size(), "应返回2个会话"); // 数量正确
        verify(conversationMapper).selectList(any(LambdaQueryWrapper.class)); // 验证selectList被调用
    } // testGetConversations方法结束

    @Test
    @DisplayName("根据ID查询会话 - 成功场景")
    void testGetConversationById_Success() { // 测试按ID查询会话成功
        Conversation conv = new Conversation(); // 构造会话
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(TEST_USER_ID);
        conv.setTitle(TEST_TITLE);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv); // Mock查询到会话

        Conversation result = conversationService.getConversationById(TEST_USER_ID, TEST_CONVERSATION_ID); // 执行查询

        assertNotNull(result, "查询结果不应为空"); // 结果不为空
        assertEquals(TEST_CONVERSATION_ID, result.getId(), "会话ID应正确"); // ID正确
        assertEquals(TEST_TITLE, result.getTitle(), "会话标题应正确"); // 标题正确
    } // testGetConversationById_Success方法结束

    @Test
    @DisplayName("根据ID查询会话 - 会话不存在应抛出异常")
    void testGetConversationById_NotFound() { // 测试会话不存在场景
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(null); // Mock查询不到会话

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出异常
                () -> conversationService.getConversationById(TEST_USER_ID, TEST_CONVERSATION_ID),
                "会话不存在时应抛出BusinessException");
        assertEquals(ErrorCode.CHAT_CONVERSATION_NOT_FOUND.getCode(), exception.getCode(), "错误码应为CHAT_CONVERSATION_NOT_FOUND"); // 错误码正确
    } // testGetConversationById_NotFound方法结束

    @Test
    @DisplayName("根据ID查询会话 - 访问他人会话应抛出权限不足异常")
    void testGetConversationById_AccessDenied() { // 测试访问他人会话
        Conversation conv = new Conversation(); // 构造属于其他用户的会话
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(OTHER_USER_ID);
        conv.setTitle(TEST_TITLE);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv); // Mock查询到会话

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出权限异常
                () -> conversationService.getConversationById(TEST_USER_ID, TEST_CONVERSATION_ID),
                "访问他人会话应抛出BusinessException");
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED.getCode(), exception.getCode(), "错误码应为AUTH_ACCESS_DENIED"); // 错误码正确
    } // testGetConversationById_AccessDenied方法结束

    @Test
    @DisplayName("删除会话 - 成功场景（级联删除消息）")
    void testDeleteConversation_Success() { // 测试删除会话成功
        Conversation conv = new Conversation(); // 构造会话
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(TEST_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv); // Mock查询到会话
        when(chatMessageMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(5); // Mock删除5条消息

        conversationService.deleteConversation(TEST_USER_ID, TEST_CONVERSATION_ID); // 执行删除

        verify(conversationMapper).deleteById(TEST_CONVERSATION_ID); // 验证会话被删除
        verify(chatMessageMapper).delete(any(LambdaQueryWrapper.class)); // 验证消息被删除
    } // testDeleteConversation_Success方法结束

    @Test
    @DisplayName("删除会话 - 会话不存在应抛出异常")
    void testDeleteConversation_NotFound() { // 测试删除不存在的会话
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(null); // Mock查询不到

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出异常
                () -> conversationService.deleteConversation(TEST_USER_ID, TEST_CONVERSATION_ID),
                "删除不存在的会话应抛出异常");
        assertEquals(ErrorCode.CHAT_CONVERSATION_NOT_FOUND.getCode(), exception.getCode()); // 错误码正确

        verify(conversationMapper, never()).deleteById(TEST_CONVERSATION_ID); // deleteById未调用
        verify(chatMessageMapper, never()).delete(any(LambdaQueryWrapper.class)); // delete未调用
    } // testDeleteConversation_NotFound方法结束

    @Test
    @DisplayName("删除会话 - 删除他人会话应抛出权限不足异常")
    void testDeleteConversation_AccessDenied() { // 测试删除他人会话
        Conversation conv = new Conversation(); // 构造属于他人的会话
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(OTHER_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv); // Mock查询到

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出权限异常
                () -> conversationService.deleteConversation(TEST_USER_ID, TEST_CONVERSATION_ID),
                "删除他人会话应抛出权限不足异常");
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED.getCode(), exception.getCode()); // 错误码正确

        verify(conversationMapper, never()).deleteById(TEST_CONVERSATION_ID); // deleteById未调用
    } // testDeleteConversation_AccessDenied方法结束

    @Test
    @DisplayName("保存消息 - 成功场景")
    void testSaveMessage_Success() { // 测试保存消息成功
        Conversation conv = new Conversation(); // 构造会话
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(TEST_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv); // Mock查询到会话
        doAnswer(invocation -> { // Mock insert回填ID
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            return 1;
        }).when(chatMessageMapper).insert(any(ChatMessage.class));

        ChatMessage result = conversationService.saveMessage(TEST_USER_ID, TEST_CONVERSATION_ID, "user", TEST_USER_MESSAGE); // 执行保存

        assertNotNull(result, "保存的消息不应为空"); // 结果不为空
        assertEquals(TEST_CONVERSATION_ID, result.getConversationId(), "消息所属会话ID应正确"); // 会话ID正确
        assertEquals(TEST_USER_ID, result.getUserId(), "消息所属用户ID应正确"); // 用户ID正确
        assertEquals("user", result.getRole(), "消息角色应正确"); // 角色正确
        assertEquals(TEST_USER_MESSAGE, result.getContent(), "消息内容应正确"); // 内容正确

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class); // 捕获参数
        verify(chatMessageMapper).insert(captor.capture()); // 验证insert调用
        assertEquals(TEST_USER_MESSAGE, captor.getValue().getContent(), "插入的消息内容应正确"); // 内容正确
        assertEquals("user", captor.getValue().getRole(), "插入的消息角色应正确"); // 角色正确
        assertEquals(TEST_USER_ID, captor.getValue().getUserId(), "插入的消息用户ID应正确"); // 用户ID正确
    } // testSaveMessage_Success方法结束

    @Test
    @DisplayName("保存消息 - 会话不存在应抛出异常")
    void testSaveMessage_ConversationNotFound() { // 测试向不存在的会话保存消息
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(null); // Mock查询不到

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出异常
                () -> conversationService.saveMessage(TEST_USER_ID, TEST_CONVERSATION_ID, "user", TEST_USER_MESSAGE),
                "向不存在的会话保存消息应抛出异常");
        assertEquals(ErrorCode.CHAT_CONVERSATION_NOT_FOUND.getCode(), exception.getCode()); // 错误码正确

        verify(chatMessageMapper, never()).insert(any(ChatMessage.class)); // insert未调用
    } // testSaveMessage_ConversationNotFound方法结束

    @Test
    @DisplayName("保存消息 - 向他人会话保存消息应抛出权限不足异常")
    void testSaveMessage_AccessDenied() { // 测试向他人会话保存消息
        Conversation conv = new Conversation(); // 构造他人会话
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(OTHER_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv); // Mock查询到

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出权限异常
                () -> conversationService.saveMessage(TEST_USER_ID, TEST_CONVERSATION_ID, "user", TEST_USER_MESSAGE),
                "向他人会话保存消息应抛出权限不足异常");
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED.getCode(), exception.getCode()); // 错误码正确

        verify(chatMessageMapper, never()).insert(any(ChatMessage.class)); // insert未调用
    } // testSaveMessage_AccessDenied方法结束

    @Test
    @DisplayName("查询会话消息 - 按创建时间正序排列")
    void testGetMessages() { // 测试查询会话消息
        Conversation conv = new Conversation(); // 构造会话
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(TEST_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv); // Mock查询到会话
        ChatMessage msg1 = new ChatMessage(); // 构造消息1
        msg1.setId(1L);
        msg1.setConversationId(TEST_CONVERSATION_ID);
        msg1.setRole("user");
        msg1.setContent(TEST_USER_MESSAGE);
        ChatMessage msg2 = new ChatMessage(); // 构造消息2
        msg2.setId(2L);
        msg2.setConversationId(TEST_CONVERSATION_ID);
        msg2.setRole("assistant");
        msg2.setContent(TEST_ASSISTANT_MESSAGE);
        when(chatMessageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(msg1, msg2)); // Mock返回两条消息

        List<ChatMessage> result = conversationService.getMessages(TEST_USER_ID, TEST_CONVERSATION_ID); // 执行查询

        assertNotNull(result, "查询结果不应为空"); // 结果不为空
        assertEquals(2, result.size(), "应返回2条消息"); // 数量正确
        assertEquals("user", result.get(0).getRole(), "第一条消息应为用户消息"); // 第一条是用户消息
        assertEquals("assistant", result.get(1).getRole(), "第二条消息应为助手消息"); // 第二条是助手消息
        verify(chatMessageMapper).selectList(any(LambdaQueryWrapper.class)); // selectList被调用
    } // testGetMessages方法结束

    @Test
    @DisplayName("查询会话消息 - 查询他人会话消息应抛出权限不足异常")
    void testGetMessages_AccessDenied() { // 测试查询他人会话消息
        Conversation conv = new Conversation(); // 构造他人会话
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(OTHER_USER_ID);
        when(conversationMapper.selectById(TEST_CONVERSATION_ID)).thenReturn(conv); // Mock查询到

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出权限异常
                () -> conversationService.getMessages(TEST_USER_ID, TEST_CONVERSATION_ID),
                "查询他人会话消息应抛出权限不足异常");
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED.getCode(), exception.getCode()); // 错误码正确

        verify(chatMessageMapper, never()).selectList(any(LambdaQueryWrapper.class)); // selectList未调用
    } // testGetMessages_AccessDenied方法结束
} // ConversationServiceTest类结束
