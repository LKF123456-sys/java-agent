package com.ailearn.chat; // 声明包名

import com.ailearn.common.BusinessException; // 业务异常类
import com.ailearn.common.ErrorCode; // 错误码枚举
import com.ailearn.dto.ChatRequest; // 聊天请求DTO
import com.ailearn.entity.Conversation; // 会话实体
import com.ailearn.security.UserPrincipal; // 用户主体类
import com.ailearn.service.ConversationService; // 会话服务
import org.junit.jupiter.api.AfterEach; // JUnit后置方法注解
import org.junit.jupiter.api.BeforeEach; // JUnit前置方法注解
import org.junit.jupiter.api.DisplayName; // JUnit显示名称注解
import org.junit.jupiter.api.Test; // JUnit测试方法注解
import org.junit.jupiter.api.extension.ExtendWith; // JUnit扩展注解
import org.mockito.Mock; // Mockito创建Mock注解
import org.mockito.MockedStatic; // Mockito静态Mock对象
import org.mockito.junit.jupiter.MockitoExtension; // Mockito JUnit 5扩展
import org.springframework.ai.chat.client.ChatClient; // Spring AI聊天客户端
import org.springframework.ai.chat.model.ChatModel; // Spring AI聊天模型接口
import reactor.core.publisher.Flux; // Reactor响应式流类
import reactor.test.StepVerifier; // Reactor测试验证器

import java.util.Map; // Map接口

import static org.junit.jupiter.api.Assertions.*; // JUnit断言静态导入
import static org.mockito.ArgumentMatchers.any; // Mockito参数匹配器
import static org.mockito.ArgumentMatchers.anyLong; // Mockito long参数匹配器
import static org.mockito.ArgumentMatchers.anyString; // Mockito String参数匹配器
import static org.mockito.ArgumentMatchers.eq; // Mockito相等参数匹配器
import static org.mockito.Mockito.*; // Mockito静态导入

@ExtendWith(MockitoExtension.class) // 启用Mockito扩展
@DisplayName("聊天服务单元测试") // 测试类显示名称
class ChatServiceTest { // 聊天服务测试类

    @Mock // 创建ChatModel Mock对象
    private ChatModel chatModel; // Mock聊天模型

    @Mock // 创建ChatClient.Builder Mock对象
    private ChatClient.Builder chatClientBuilder; // Mock客户端构建器

    @Mock // 创建ChatClient Mock对象
    private ChatClient chatClient; // Mock聊天客户端

    @Mock // 创建ChatClient.ChatClientRequestSpec Mock对象
    private ChatClient.ChatClientRequestSpec requestSpec; // Mock请求规范

    @Mock // 创建ChatClient.CallResponseSpec Mock对象
    private ChatClient.CallResponseSpec callResponseSpec; // Mock同步响应规范

    @Mock // 创建ChatClient.StreamResponseSpec Mock对象
    private ChatClient.StreamResponseSpec streamResponseSpec; // Mock流式响应规范

    @Mock // 创建ConversationService Mock对象
    private ConversationService conversationService; // Mock会话服务

    private MockedStatic<ChatClient> chatClientStatic; // 静态Mock对象，用于Mock ChatClient.builder()

    private ChatService chatService; // 被测聊天服务实例

    private static final Long TEST_USER_ID = 1L; // 测试用户ID
    private static final String TEST_USERNAME = "testuser"; // 测试用户名
    private static final String TEST_ROLE = "user"; // 测试角色
    private static final Long TEST_CONVERSATION_ID = 100L; // 测试会话ID
    private static final String TEST_USER_MESSAGE = "你好，请介绍一下自己"; // 测试用户消息
    private static final String TEST_AI_REPLY = "你好！我是赛博AI助手，很高兴为你服务。"; // 测试AI回复

    private UserPrincipal testUser; // 测试用户主体对象

    @BeforeEach // 每个测试前执行
    void setUp() { // 初始化方法
        testUser = UserPrincipal.create(TEST_USER_ID, TEST_USERNAME, TEST_ROLE); // 创建测试用户

        chatClientStatic = mockStatic(ChatClient.class); // Mock静态方法ChatClient.builder()
        chatClientStatic.when(() -> ChatClient.builder(chatModel)).thenReturn(chatClientBuilder); // Mock builder(chatModel)返回builder

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder); // Mock defaultSystem返回builder
        when(chatClientBuilder.build()).thenReturn(chatClient); // Mock build返回chatClient

        chatService = new ChatService(chatModel, conversationService); // 创建ChatService实例
    } // setUp方法结束

    @AfterEach // 每个测试后执行
    void tearDown() { // 清理方法
        if (chatClientStatic != null) { // 如果静态Mock存在
            chatClientStatic.close(); // 关闭静态Mock
        } // if结束
    } // tearDown方法结束

    private void setupMockForSyncChat(String aiReply) { // 设置同步聊天Mock调用链
        when(chatClient.prompt()).thenReturn(requestSpec); // Mock prompt返回requestSpec
        when(requestSpec.user(anyString())).thenReturn(requestSpec); // Mock user返回requestSpec
        when(requestSpec.call()).thenReturn(callResponseSpec); // Mock call返回callResponseSpec
        when(callResponseSpec.content()).thenReturn(aiReply); // Mock content返回AI回复
    } // setupMockForSyncChat方法结束

    private void setupMockForStreamChat(String... tokens) { // 设置流式聊天Mock调用链
        when(chatClient.prompt()).thenReturn(requestSpec); // Mock prompt返回requestSpec
        when(requestSpec.user(anyString())).thenReturn(requestSpec); // Mock user返回requestSpec
        when(requestSpec.stream()).thenReturn(streamResponseSpec); // Mock stream返回streamResponseSpec
        when(streamResponseSpec.content()).thenReturn(Flux.just(tokens)); // Mock content返回token流
    } // setupMockForStreamChat方法结束

    @Test
    @DisplayName("同步对话 - 新会话成功场景")
    void testChat_NewConversation_Success() { // 测试新会话同步对话
        ChatRequest req = new ChatRequest(); // 创建聊天请求
        req.setMessage(TEST_USER_MESSAGE); // 设置用户消息
        req.setConversationId(null); // 新会话无conversationId

        Conversation newConversation = new Conversation(); // 创建新会话
        newConversation.setId(TEST_CONVERSATION_ID); // 设置会话ID
        newConversation.setUserId(TEST_USER_ID); // 设置用户ID
        when(conversationService.createConversation(eq(TEST_USER_ID), anyString(), eq("chat"))).thenReturn(newConversation); // Mock创建会话

        setupMockForSyncChat(TEST_AI_REPLY); // 设置同步聊天Mock

        Map<String, Object> result = chatService.chat(req, testUser); // 执行聊天

        assertNotNull(result, "返回结果不应为空"); // 结果不为空
        assertEquals(TEST_CONVERSATION_ID, result.get("conversationId"), "返回的会话ID应正确"); // 会话ID正确
        assertEquals(TEST_AI_REPLY, result.get("reply"), "返回的AI回复应正确"); // 回复正确

        verify(conversationService, times(1)).createConversation(eq(TEST_USER_ID), anyString(), eq("chat")); // 验证创建会话调用
        verify(conversationService, times(1)).saveMessage(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID), eq("user"), eq(TEST_USER_MESSAGE)); // 验证保存用户消息
        verify(conversationService, times(1)).saveMessage(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID), eq("assistant"), eq(TEST_AI_REPLY)); // 验证保存助手消息
    } // testChat_NewConversation_Success方法结束

    @Test
    @DisplayName("同步对话 - 已有会话成功场景")
    void testChat_ExistingConversation_Success() { // 测试已有会话同步对话
        ChatRequest req = new ChatRequest(); // 创建聊天请求
        req.setMessage(TEST_USER_MESSAGE); // 设置用户消息
        req.setConversationId(TEST_CONVERSATION_ID); // 指定已有会话ID

        Conversation existingConversation = new Conversation(); // 创建已有会话
        existingConversation.setId(TEST_CONVERSATION_ID); // 设置会话ID
        existingConversation.setUserId(TEST_USER_ID); // 设置用户ID
        when(conversationService.getConversationById(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID))).thenReturn(existingConversation); // Mock查询会话

        setupMockForSyncChat(TEST_AI_REPLY); // 设置同步聊天Mock

        Map<String, Object> result = chatService.chat(req, testUser); // 执行聊天

        assertNotNull(result, "返回结果不应为空"); // 结果不为空
        assertEquals(TEST_CONVERSATION_ID, result.get("conversationId"), "返回的会话ID应正确"); // 会话ID正确
        assertEquals(TEST_AI_REPLY, result.get("reply"), "返回的AI回复应正确"); // 回复正确

        verify(conversationService, never()).createConversation(anyLong(), anyString(), anyString()); // 验证未创建新会话
        verify(conversationService, times(1)).getConversationById(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID)); // 验证查询会话
        verify(conversationService, times(1)).saveMessage(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID), eq("user"), eq(TEST_USER_MESSAGE)); // 验证保存用户消息
        verify(conversationService, times(1)).saveMessage(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID), eq("assistant"), eq(TEST_AI_REPLY)); // 验证保存助手消息
    } // testChat_ExistingConversation_Success方法结束

    @Test
    @DisplayName("同步对话 - AI回复为空应抛出异常")
    void testChat_EmptyReply() { // 测试AI回复为空
        ChatRequest req = new ChatRequest(); // 创建聊天请求
        req.setMessage(TEST_USER_MESSAGE); // 设置用户消息
        req.setConversationId(TEST_CONVERSATION_ID); // 设置会话ID

        Conversation conv = new Conversation(); // 创建会话
        conv.setId(TEST_CONVERSATION_ID); // 设置ID
        conv.setUserId(TEST_USER_ID); // 设置用户ID
        when(conversationService.getConversationById(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID))).thenReturn(conv); // Mock查询会话

        setupMockForSyncChat(""); // Mock AI返回空字符串

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出BusinessException
                () -> chatService.chat(req, testUser), "AI回复为空应抛出BusinessException");
        assertEquals(ErrorCode.CHAT_AI_CALL_FAILED.getCode(), exception.getCode(), "错误码应为CHAT_AI_CALL_FAILED"); // 错误码正确
    } // testChat_EmptyReply方法结束

    @Test
    @DisplayName("同步对话 - AI调用异常应抛出业务异常")
    void testChat_AICallException() { // 测试AI调用异常
        ChatRequest req = new ChatRequest(); // 创建聊天请求
        req.setMessage(TEST_USER_MESSAGE); // 设置用户消息
        req.setConversationId(TEST_CONVERSATION_ID); // 设置会话ID

        Conversation conv = new Conversation(); // 创建会话
        conv.setId(TEST_CONVERSATION_ID); // 设置ID
        conv.setUserId(TEST_USER_ID); // 设置用户ID
        when(conversationService.getConversationById(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID))).thenReturn(conv); // Mock查询会话

        when(chatClient.prompt()).thenReturn(requestSpec); // Mock prompt
        when(requestSpec.user(anyString())).thenReturn(requestSpec); // Mock user
        when(requestSpec.call()).thenThrow(new RuntimeException("AI服务连接超时")); // Mock call抛出异常

        BusinessException exception = assertThrows(BusinessException.class, // 断言抛出异常
                () -> chatService.chat(req, testUser), "AI调用异常应抛出BusinessException");
        assertEquals(ErrorCode.CHAT_AI_CALL_FAILED.getCode(), exception.getCode(), "错误码应为CHAT_AI_CALL_FAILED"); // 错误码正确
    } // testChat_AICallException方法结束

    @Test
    @DisplayName("简单对话 - 直接返回AI回复")
    void testSimpleChat() { // 测试简单对话
        setupMockForSyncChat(TEST_AI_REPLY); // 设置Mock

        String result = chatService.chat(TEST_USER_MESSAGE); // 执行简单对话

        assertEquals(TEST_AI_REPLY, result, "简单对话应直接返回AI回复"); // 回复正确
        verifyNoInteractions(conversationService); // 验证未调用会话服务
    } // testSimpleChat方法结束

    @Test
    @DisplayName("简单流式对话 - 返回token流")
    void testSimpleStreamChat() { // 测试简单流式对话
        String token1 = "Hello"; // token1
        String token2 = " World"; // token2
        setupMockForStreamChat(token1, token2); // 设置流式Mock

        Flux<String> result = chatService.streamChat("Hi"); // 执行流式对话

        StepVerifier.create(result) // 使用StepVerifier验证流
                .expectNext(token1) // 期望第一个token
                .expectNext(token2) // 期望第二个token
                .verifyComplete(); // 验证流完成

        verifyNoInteractions(conversationService); // 验证未调用会话服务
    } // testSimpleStreamChat方法结束

    @Test
    @DisplayName("带System Prompt的对话")
    void testChatWithSystem() { // 测试带System Prompt
        String systemPrompt = "你是一个专业的Java程序员"; // 系统提示词
        String userMessage = "什么是Spring Boot？"; // 用户消息
        String expectedReply = "Spring Boot是一个快速开发框架..."; // 期望回复

        when(chatClient.prompt()).thenReturn(requestSpec); // Mock prompt
        when(requestSpec.system(systemPrompt)).thenReturn(requestSpec); // Mock system
        when(requestSpec.user(userMessage)).thenReturn(requestSpec); // Mock user
        when(requestSpec.call()).thenReturn(callResponseSpec); // Mock call
        when(callResponseSpec.content()).thenReturn(expectedReply); // Mock content返回期望回复

        String result = chatService.chatWithSystem(userMessage, systemPrompt); // 执行带System Prompt对话

        assertEquals(expectedReply, result, "带System Prompt的对话应返回预期回复"); // 回复正确
        verify(requestSpec, times(1)).system(systemPrompt); // 验证system被调用
        verify(requestSpec, times(1)).user(userMessage); // 验证user被调用
    } // testChatWithSystem方法结束

    @Test
    @DisplayName("新会话标题 - 长消息自动截断为20字符加省略号")
    void testNewConversationTitle_LongMessageTruncated() { // 测试长消息标题截断
        String longMessage = "这是一个非常非常非常非常非常非常非常非常长的用户消息内容"; // 长消息
        ChatRequest req = new ChatRequest(); // 创建请求
        req.setMessage(longMessage); // 设置长消息
        req.setConversationId(null); // 新会话

        Conversation newConversation = new Conversation(); // 创建新会话
        newConversation.setId(TEST_CONVERSATION_ID); // 设置ID
        newConversation.setUserId(TEST_USER_ID); // 设置用户ID
        when(conversationService.createConversation(eq(TEST_USER_ID), anyString(), eq("chat"))).thenReturn(newConversation); // Mock创建会话
        setupMockForSyncChat(TEST_AI_REPLY); // 设置Mock

        chatService.chat(req, testUser); // 执行聊天

        org.mockito.ArgumentCaptor<String> titleCaptor = org.mockito.ArgumentCaptor.forClass(String.class); // 创建标题捕获器
        verify(conversationService, times(1)).createConversation(eq(TEST_USER_ID), titleCaptor.capture(), eq("chat")); // 捕获标题参数
        String capturedTitle = titleCaptor.getValue(); // 获取捕获的标题

        assertEquals(23, capturedTitle.length(), "长消息标题应为20字符+省略号共23字符"); // 长度23（20字符+3点省略号）
        assertTrue(capturedTitle.endsWith("..."), "长消息标题应以省略号结尾"); // 以...结尾
    } // testNewConversationTitle_LongMessageTruncated方法结束

    @Test
    @DisplayName("新会话标题 - 短消息保持原样不截断")
    void testNewConversationTitle_ShortMessage() { // 测试短消息标题
        String shortMessage = "你好"; // 短消息
        ChatRequest req = new ChatRequest(); // 创建请求
        req.setMessage(shortMessage); // 设置短消息
        req.setConversationId(null); // 新会话

        Conversation newConversation = new Conversation(); // 创建新会话
        newConversation.setId(TEST_CONVERSATION_ID); // 设置ID
        newConversation.setUserId(TEST_USER_ID); // 设置用户ID
        when(conversationService.createConversation(eq(TEST_USER_ID), anyString(), eq("chat"))).thenReturn(newConversation); // Mock创建会话
        setupMockForSyncChat(TEST_AI_REPLY); // 设置Mock

        chatService.chat(req, testUser); // 执行聊天

        org.mockito.ArgumentCaptor<String> titleCaptor = org.mockito.ArgumentCaptor.forClass(String.class); // 创建标题捕获器
        verify(conversationService, times(1)).createConversation(eq(TEST_USER_ID), titleCaptor.capture(), eq("chat")); // 捕获标题

        assertEquals(shortMessage, titleCaptor.getValue(), "短消息标题应保持原样"); // 标题与原消息一致
        assertFalse(titleCaptor.getValue().endsWith("..."), "短消息标题不应以省略号结尾"); // 不以...结尾
    } // testNewConversationTitle_ShortMessage方法结束
} // ChatServiceTest类结束
