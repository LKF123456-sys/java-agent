package com.ailearn.chat;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.dto.ChatRequest;
import com.ailearn.entity.Conversation;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ChatService单元测试类
 * 使用Mockito框架mock依赖（ChatClient.Builder、ConversationService）
 * 重点测试业务逻辑：会话管理、消息保存、SSE格式构建等
 * AI模型调用部分通过mock ChatClient来隔离测试
 *
 * @author AiLearn Platform
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("聊天服务测试")
class ChatServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    @Mock
    private ConversationService conversationService;

    private ChatService chatService;

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_ROLE = "user";
    private static final Long TEST_CONVERSATION_ID = 1L;
    private static final String TEST_USER_MESSAGE = "你好，请介绍一下自己";
    private static final String TEST_AI_REPLY = "你好！我是赛博AI助手，很高兴为你服务。";

    private UserPrincipal testUser;

    /**
     * 初始化ChatService，mock ChatClient构建流程
     */
    @BeforeEach
    void setUp() {
        testUser = UserPrincipal.create(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);

        // Mock ChatClient构建链
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        chatService = new ChatService(chatClientBuilder, conversationService);
    }

    /**
     * 准备同步聊天的mock链
     */
    private void setupMockForSyncChat(String aiReply) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(aiReply);
    }

    /**
     * 准备流式聊天的mock链
     */
    private void setupMockForStreamChat(String... tokens) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just(tokens));
    }

    /**
     * 测试同步对话成功场景（新会话）
     */
    @Test
    @DisplayName("同步对话 - 新会话成功场景")
    void testChat_NewConversation_Success() {
        // 准备：无conversationId，需要创建新会话
        ChatRequest req = new ChatRequest();
        req.setMessage(TEST_USER_MESSAGE);
        req.setConversationId(null);

        // Mock：创建新会话
        Conversation newConversation = new Conversation();
        newConversation.setId(TEST_CONVERSATION_ID);
        when(conversationService.createConversation(anyString(), eq("chat"))).thenReturn(newConversation);
        // Mock：AI回复
        setupMockForSyncChat(TEST_AI_REPLY);

        // 执行
        Map<String, Object> result = chatService.chat(req, testUser);

        // 验证
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.get("conversationId"));
        assertEquals(TEST_AI_REPLY, result.get("reply"));

        // 验证：会话创建、消息保存
        verify(conversationService).createConversation(anyString(), eq("chat"));
        verify(conversationService).saveMessage(TEST_CONVERSATION_ID, "user", TEST_USER_MESSAGE);
        verify(conversationService).saveMessage(TEST_CONVERSATION_ID, "assistant", TEST_AI_REPLY);
    }

    /**
     * 测试同步对话成功场景（已有会话）
     */
    @Test
    @DisplayName("同步对话 - 已有会话成功场景")
    void testChat_ExistingConversation_Success() {
        // 准备：指定conversationId
        ChatRequest req = new ChatRequest();
        req.setMessage(TEST_USER_MESSAGE);
        req.setConversationId(TEST_CONVERSATION_ID);

        // Mock：验证会话存在
        Conversation existingConversation = new Conversation();
        existingConversation.setId(TEST_CONVERSATION_ID);
        when(conversationService.getConversationById(TEST_CONVERSATION_ID)).thenReturn(existingConversation);
        // Mock：AI回复
        setupMockForSyncChat(TEST_AI_REPLY);

        // 执行
        Map<String, Object> result = chatService.chat(req, testUser);

        // 验证
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.get("conversationId"));
        assertEquals(TEST_AI_REPLY, result.get("reply"));

        // 验证：不创建新会话，直接验证和保存消息
        verify(conversationService, never()).createConversation(anyString(), anyString());
        verify(conversationService).getConversationById(TEST_CONVERSATION_ID);
        verify(conversationService).saveMessage(TEST_CONVERSATION_ID, "user", TEST_USER_MESSAGE);
        verify(conversationService).saveMessage(TEST_CONVERSATION_ID, "assistant", TEST_AI_REPLY);
    }

    /**
     * 测试AI回复为空的场景
     */
    @Test
    @DisplayName("同步对话 - AI回复为空应抛出异常")
    void testChat_EmptyReply() {
        ChatRequest req = new ChatRequest();
        req.setMessage(TEST_USER_MESSAGE);
        req.setConversationId(TEST_CONVERSATION_ID);

        when(conversationService.getConversationById(TEST_CONVERSATION_ID)).thenReturn(new Conversation());
        setupMockForSyncChat("");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(req, testUser));
        assertEquals(ErrorCode.CHAT_AI_CALL_FAILED.getCode(), exception.getCode());
    }

    /**
     * 测试AI调用异常场景
     */
    @Test
    @DisplayName("同步对话 - AI调用异常应抛出业务异常")
    void testChat_AICallException() {
        ChatRequest req = new ChatRequest();
        req.setMessage(TEST_USER_MESSAGE);
        req.setConversationId(TEST_CONVERSATION_ID);

        when(conversationService.getConversationById(TEST_CONVERSATION_ID)).thenReturn(new Conversation());
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("AI服务连接超时"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatService.chat(req, testUser));
        assertEquals(ErrorCode.CHAT_AI_CALL_FAILED.getCode(), exception.getCode());
    }

    /**
     * 测试流式对话成功场景
     */
    @Test
    @DisplayName("流式对话 - 成功场景（验证SSE格式）")
    void testStreamChat_Success() {
        // 准备
        ChatRequest req = new ChatRequest();
        req.setMessage(TEST_USER_MESSAGE);
        req.setConversationId(null);

        Conversation newConversation = new Conversation();
        newConversation.setId(TEST_CONVERSATION_ID);
        when(conversationService.createConversation(anyString(), eq("chat"))).thenReturn(newConversation);

        // Mock：流式返回多个token
        String token1 = "你好";
        String token2 = "！";
        String token3 = "我是AI助手";
        setupMockForStreamChat(token1, token2, token3);

        // 执行
        Flux<String> result = chatService.streamChat(req, testUser);

        // 验证：使用StepVerifier验证响应流
        StepVerifier.create(result)
                // 第一个事件应是conversationId
                .assertNext(event -> {
                    assertTrue(event.contains("\"type\":\"conversationId\""));
                    assertTrue(event.contains(String.valueOf(TEST_CONVERSATION_ID)));
                    assertTrue(event.startsWith("data: "));
                    assertTrue(event.endsWith("\n\n"));
                })
                // 接下来是token事件
                .assertNext(event -> {
                    assertTrue(event.contains("\"type\":\"token\""));
                    assertTrue(event.contains(token1));
                })
                .assertNext(event -> {
                    assertTrue(event.contains("\"type\":\"token\""));
                    assertTrue(event.contains(token2));
                })
                .assertNext(event -> {
                    assertTrue(event.contains("\"type\":\"token\""));
                    assertTrue(event.contains(token3));
                })
                // 最后是done事件
                .assertNext(event -> {
                    assertTrue(event.contains("\"type\":\"done\""));
                    assertTrue(event.contains("[DONE]"));
                })
                .verifyComplete();

        // 验证：消息保存被调用
        verify(conversationService).saveMessage(eq(TEST_CONVERSATION_ID), eq("user"), eq(TEST_USER_MESSAGE));
    }

    /**
     * 测试简单同步对话（不带会话管理）
     */
    @Test
    @DisplayName("简单对话 - 直接返回AI回复")
    void testSimpleChat() {
        setupMockForSyncChat(TEST_AI_REPLY);

        String result = chatService.chat(TEST_USER_MESSAGE);

        assertEquals(TEST_AI_REPLY, result);
        // 简单对话不涉及会话服务
        verifyNoInteractions(conversationService);
    }

    /**
     * 测试简单流式对话
     */
    @Test
    @DisplayName("简单流式对话 - 返回token流")
    void testSimpleStreamChat() {
        setupMockForStreamChat("Hello", " World");

        Flux<String> result = chatService.streamChat("Hi");

        StepVerifier.create(result)
                .expectNext("Hello")
                .expectNext(" World")
                .verifyComplete();

        verifyNoInteractions(conversationService);
    }

    /**
     * 测试带System Prompt的对话
     */
    @Test
    @DisplayName("带System Prompt的对话")
    void testChatWithSystem() {
        String systemPrompt = "你是一个专业的Java程序员";
        String userMessage = "什么是Spring Boot？";
        String expectedReply = "Spring Boot是一个快速开发框架...";

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(systemPrompt)).thenReturn(requestSpec);
        when(requestSpec.user(userMessage)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(expectedReply);

        String result = chatService.chatWithSystem(userMessage, systemPrompt);

        assertEquals(expectedReply, result);
        verify(requestSpec).system(systemPrompt);
        verify(requestSpec).user(userMessage);
    }

    /**
     * 测试会话标题生成逻辑（长消息截断）
     */
    @Test
    @DisplayName("新会话标题 - 长消息自动截断")
    void testNewConversationTitle_LongMessageTruncated() {
        // 准备一个超过20字符的消息
        String longMessage = "这是一个非常非常非常非常非常非常非常非常长的用户消息内容";
        ChatRequest req = new ChatRequest();
        req.setMessage(longMessage);
        req.setConversationId(null);

        Conversation newConversation = new Conversation();
        newConversation.setId(TEST_CONVERSATION_ID);

        // 使用ArgumentCaptor捕获创建会话时的标题参数
        when(conversationService.createConversation(anyString(), eq("chat"))).thenReturn(newConversation);
        setupMockForSyncChat(TEST_AI_REPLY);

        chatService.chat(req, testUser);

        // 验证：标题被截断为20字符+省略号
        org.mockito.ArgumentCaptor<String> titleCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(conversationService).createConversation(titleCaptor.capture(), eq("chat"));
        String capturedTitle = titleCaptor.getValue();
        assertEquals(23, capturedTitle.length()); // 20字符 + "..."
        assertTrue(capturedTitle.endsWith("..."));
    }

    /**
     * 测试会话标题生成逻辑（短消息不截断）
     */
    @Test
    @DisplayName("新会话标题 - 短消息不截断")
    void testNewConversationTitle_ShortMessage() {
        String shortMessage = "你好";
        ChatRequest req = new ChatRequest();
        req.setMessage(shortMessage);
        req.setConversationId(null);

        Conversation newConversation = new Conversation();
        newConversation.setId(TEST_CONVERSATION_ID);

        when(conversationService.createConversation(anyString(), eq("chat"))).thenReturn(newConversation);
        setupMockForSyncChat(TEST_AI_REPLY);

        chatService.chat(req, testUser);

        org.mockito.ArgumentCaptor<String> titleCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(conversationService).createConversation(titleCaptor.capture(), eq("chat"));
        assertEquals(shortMessage, titleCaptor.getValue());
        assertFalse(titleCaptor.getValue().endsWith("..."));
    }
}
