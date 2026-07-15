package com.ailearn.chat;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.dto.ChatRequest;
import com.ailearn.entity.Conversation;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.ConversationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ChatService单元测试类
 * 使用Mockito框架mock依赖（ChatModel、ChatClient、ConversationService）
 * 重点测试业务逻辑：会话管理、消息保存、异常处理等
 * AI模型调用部分通过mock ChatClient来隔离测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("聊天服务单元测试")
class ChatServiceTest {

    /**
     * Mock ChatModel - Spring AI聊天模型
     */
    @Mock
    private ChatModel chatModel;

    /**
     * Mock ChatClient.Builder - ChatClient构建器
     */
    @Mock
    private ChatClient.Builder chatClientBuilder;

    /**
     * Mock ChatClient - Spring AI聊天客户端
     */
    @Mock
    private ChatClient chatClient;

    /**
     * Mock ChatClient.ChatClientRequestSpec - 请求构建器
     */
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    /**
     * Mock ChatClient.CallResponseSpec - 同步响应规范
     */
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    /**
     * Mock ChatClient.StreamResponseSpec - 流式响应规范
     */
    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    /**
     * Mock ConversationService - 会话服务
     */
    @Mock
    private ConversationService conversationService;

    /**
     * 静态Mock对象，用于Mock ChatClient.builder()静态方法
     */
    private MockedStatic<ChatClient> chatClientStatic;

    /**
     * 被测试的ChatService实例
     */
    private ChatService chatService;

    /**
     * 测试用用户ID常量
     */
    private static final Long TEST_USER_ID = 1L;

    /**
     * 测试用用户名常量
     */
    private static final String TEST_USERNAME = "testuser";

    /**
     * 测试用角色常量
     */
    private static final String TEST_ROLE = "user";

    /**
     * 测试用会话ID常量
     */
    private static final Long TEST_CONVERSATION_ID = 100L;

    /**
     * 测试用用户消息常量
     */
    private static final String TEST_USER_MESSAGE = "你好，请介绍一下自己";

    /**
     * 测试用AI回复常量
     */
    private static final String TEST_AI_REPLY = "你好！我是赛博AI助手，很高兴为你服务。";

    /**
     * 测试用用户主体对象
     */
    private UserPrincipal testUser;

    /**
     * 每个测试方法执行前的初始化
     * Mock ChatClient构建流程，创建ChatService实例
     */
    @BeforeEach
    void setUp() {
        // 创建测试用户主体
        testUser = UserPrincipal.create(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);

        // Mock ChatClient静态builder方法
        chatClientStatic = mockStatic(ChatClient.class);
        chatClientStatic.when(() -> ChatClient.builder(chatModel)).thenReturn(chatClientBuilder);

        // Mock ChatClient构建链
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        // 创建ChatService实例
        chatService = new ChatService(chatModel, conversationService);
    }

    /**
     * 每个测试方法执行后的清理
     * 关闭静态Mock对象
     */
    @AfterEach
    void tearDown() {
        if (chatClientStatic != null) {
            chatClientStatic.close();
        }
    }

    /**
     * 设置同步聊天的Mock调用链
     * @param aiReply AI回复内容
     */
    private void setupMockForSyncChat(String aiReply) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(aiReply);
    }

    /**
     * 设置流式聊天的Mock调用链
     * @param tokens 要返回的token数组
     */
    private void setupMockForStreamChat(String... tokens) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just(tokens));
    }

    /**
     * 测试：同步对话成功场景（新会话）
     * 当conversationId为null时，应该自动创建新会话
     */
    @Test
    @DisplayName("同步对话 - 新会话成功场景")
    void testChat_NewConversation_Success() {
        // 准备请求对象 - 无conversationId
        ChatRequest req = new ChatRequest();
        req.setMessage(TEST_USER_MESSAGE);
        req.setConversationId(null);

        // Mock：创建新会话返回预期会话
        Conversation newConversation = new Conversation();
        newConversation.setId(TEST_CONVERSATION_ID);
        newConversation.setUserId(TEST_USER_ID);
        when(conversationService.createConversation(eq(TEST_USER_ID), anyString(), eq("chat")))
                .thenReturn(newConversation);

        // Mock：AI同步回复
        setupMockForSyncChat(TEST_AI_REPLY);

        // 执行测试
        Map<String, Object> result = chatService.chat(req, testUser);

        // 验证返回结果
        assertNotNull(result, "返回结果不应为空");
        assertEquals(TEST_CONVERSATION_ID, result.get("conversationId"), "返回的会话ID应正确");
        assertEquals(TEST_AI_REPLY, result.get("reply"), "返回的AI回复应正确");

        // 验证服务调用
        verify(conversationService, times(1))
                .createConversation(eq(TEST_USER_ID), anyString(), eq("chat"));
        verify(conversationService, times(1))
                .saveMessage(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID), eq("user"), eq(TEST_USER_MESSAGE));
        verify(conversationService, times(1))
                .saveMessage(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID), eq("assistant"), eq(TEST_AI_REPLY));
    }

    /**
     * 测试：同步对话成功场景（已有会话）
     * 当conversationId存在时，应验证会话所有权并继续使用
     */
    @Test
    @DisplayName("同步对话 - 已有会话成功场景")
    void testChat_ExistingConversation_Success() {
        // 准备请求对象 - 指定conversationId
        ChatRequest req = new ChatRequest();
        req.setMessage(TEST_USER_MESSAGE);
        req.setConversationId(TEST_CONVERSATION_ID);

        // Mock：验证会话存在并属于当前用户
        Conversation existingConversation = new Conversation();
        existingConversation.setId(TEST_CONVERSATION_ID);
        existingConversation.setUserId(TEST_USER_ID);
        when(conversationService.getConversationById(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID)))
                .thenReturn(existingConversation);

        // Mock：AI同步回复
        setupMockForSyncChat(TEST_AI_REPLY);

        // 执行测试
        Map<String, Object> result = chatService.chat(req, testUser);

        // 验证返回结果
        assertNotNull(result, "返回结果不应为空");
        assertEquals(TEST_CONVERSATION_ID, result.get("conversationId"), "返回的会话ID应正确");
        assertEquals(TEST_AI_REPLY, result.get("reply"), "返回的AI回复应正确");

        // 验证服务调用 - 不应创建新会话
        verify(conversationService, never())
                .createConversation(anyLong(), anyString(), anyString());
        verify(conversationService, times(1))
                .getConversationById(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID));
        verify(conversationService, times(1))
                .saveMessage(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID), eq("user"), eq(TEST_USER_MESSAGE));
        verify(conversationService, times(1))
                .saveMessage(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID), eq("assistant"), eq(TEST_AI_REPLY));
    }

    /**
     * 测试：AI回复为空时应抛出异常
     */
    @Test
    @DisplayName("同步对话 - AI回复为空应抛出异常")
    void testChat_EmptyReply() {
        // 准备请求对象
        ChatRequest req = new ChatRequest();
        req.setMessage(TEST_USER_MESSAGE);
        req.setConversationId(TEST_CONVERSATION_ID);

        // Mock：会话验证
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(TEST_USER_ID);
        when(conversationService.getConversationById(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID)))
                .thenReturn(conv);

        // Mock：AI返回空字符串
        setupMockForSyncChat("");

        // 执行并验证抛出异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(req, testUser), "AI回复为空应抛出BusinessException");
        assertEquals(ErrorCode.CHAT_AI_CALL_FAILED.getCode(), exception.getCode(),
                "错误码应为CHAT_AI_CALL_FAILED");
    }

    /**
     * 测试：AI调用异常时应抛出业务异常
     */
    @Test
    @DisplayName("同步对话 - AI调用异常应抛出业务异常")
    void testChat_AICallException() {
        // 准备请求对象
        ChatRequest req = new ChatRequest();
        req.setMessage(TEST_USER_MESSAGE);
        req.setConversationId(TEST_CONVERSATION_ID);

        // Mock：会话验证
        Conversation conv = new Conversation();
        conv.setId(TEST_CONVERSATION_ID);
        conv.setUserId(TEST_USER_ID);
        when(conversationService.getConversationById(eq(TEST_USER_ID), eq(TEST_CONVERSATION_ID)))
                .thenReturn(conv);

        // Mock：AI调用抛出异常
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("AI服务连接超时"));

        // 执行并验证抛出异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(req, testUser), "AI调用异常应抛出BusinessException");
        assertEquals(ErrorCode.CHAT_AI_CALL_FAILED.getCode(), exception.getCode(),
                "错误码应为CHAT_AI_CALL_FAILED");
    }

    /**
     * 测试：简单同步对话（不带会话管理）
     */
    @Test
    @DisplayName("简单对话 - 直接返回AI回复")
    void testSimpleChat() {
        // Mock AI回复
        setupMockForSyncChat(TEST_AI_REPLY);

        // 执行测试
        String result = chatService.chat(TEST_USER_MESSAGE);

        // 验证结果
        assertEquals(TEST_AI_REPLY, result, "简单对话应直接返回AI回复");
        // 验证：简单对话不应调用会话服务
        verifyNoInteractions(conversationService);
    }

    /**
     * 测试：简单流式对话
     */
    @Test
    @DisplayName("简单流式对话 - 返回token流")
    void testSimpleStreamChat() {
        // 设置流式Mock
        String token1 = "Hello";
        String token2 = " World";
        setupMockForStreamChat(token1, token2);

        // 执行测试
        Flux<String> result = chatService.streamChat("Hi");

        // 使用StepVerifier验证流内容
        StepVerifier.create(result)
                .expectNext(token1)
                .expectNext(token2)
                .verifyComplete();

        // 验证：简单流式对话不应调用会话服务
        verifyNoInteractions(conversationService);
    }

    /**
     * 测试：带System Prompt的对话
     */
    @Test
    @DisplayName("带System Prompt的对话")
    void testChatWithSystem() {
        // 准备测试数据
        String systemPrompt = "你是一个专业的Java程序员";
        String userMessage = "什么是Spring Boot？";
        String expectedReply = "Spring Boot是一个快速开发框架...";

        // Mock带系统提示词的调用链
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(systemPrompt)).thenReturn(requestSpec);
        when(requestSpec.user(userMessage)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(expectedReply);

        // 执行测试
        String result = chatService.chatWithSystem(userMessage, systemPrompt);

        // 验证结果
        assertEquals(expectedReply, result, "带System Prompt的对话应返回预期回复");
        verify(requestSpec, times(1)).system(systemPrompt);
        verify(requestSpec, times(1)).user(userMessage);
    }

    /**
     * 测试：新会话标题生成 - 长消息自动截断
     */
    @Test
    @DisplayName("新会话标题 - 长消息自动截断为20字符加省略号")
    void testNewConversationTitle_LongMessageTruncated() {
        // 准备超过20字符的长消息
        String longMessage = "这是一个非常非常非常非常非常非常非常非常长的用户消息内容";
        ChatRequest req = new ChatRequest();
        req.setMessage(longMessage);
        req.setConversationId(null);

        // Mock创建新会话
        Conversation newConversation = new Conversation();
        newConversation.setId(TEST_CONVERSATION_ID);
        newConversation.setUserId(TEST_USER_ID);
        when(conversationService.createConversation(eq(TEST_USER_ID), anyString(), eq("chat")))
                .thenReturn(newConversation);
        setupMockForSyncChat(TEST_AI_REPLY);

        // 执行测试
        chatService.chat(req, testUser);

        // 使用ArgumentCaptor捕获创建会话时的标题参数
        org.mockito.ArgumentCaptor<String> titleCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(conversationService, times(1))
                .createConversation(eq(TEST_USER_ID), titleCaptor.capture(), eq("chat"));
        String capturedTitle = titleCaptor.getValue();

        // 验证标题被正确截断
        assertEquals(23, capturedTitle.length(), "长消息标题应为20字符+省略号共23字符");
        assertTrue(capturedTitle.endsWith("..."), "长消息标题应以省略号结尾");
    }

    /**
     * 测试：新会话标题生成 - 短消息不截断
     */
    @Test
    @DisplayName("新会话标题 - 短消息保持原样不截断")
    void testNewConversationTitle_ShortMessage() {
        // 准备短消息
        String shortMessage = "你好";
        ChatRequest req = new ChatRequest();
        req.setMessage(shortMessage);
        req.setConversationId(null);

        // Mock创建新会话
        Conversation newConversation = new Conversation();
        newConversation.setId(TEST_CONVERSATION_ID);
        newConversation.setUserId(TEST_USER_ID);
        when(conversationService.createConversation(eq(TEST_USER_ID), anyString(), eq("chat")))
                .thenReturn(newConversation);
        setupMockForSyncChat(TEST_AI_REPLY);

        // 执行测试
        chatService.chat(req, testUser);

        // 使用ArgumentCaptor捕获标题参数
        org.mockito.ArgumentCaptor<String> titleCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(conversationService, times(1))
                .createConversation(eq(TEST_USER_ID), titleCaptor.capture(), eq("chat"));

        // 验证短消息标题不截断
        assertEquals(shortMessage, titleCaptor.getValue(), "短消息标题应保持原样");
        assertFalse(titleCaptor.getValue().endsWith("..."), "短消息标题不应以省略号结尾");
    }
}
