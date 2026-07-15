package com.ailearn.config; // 声明包名

import org.mockito.ArgumentMatchers; // Mockito参数匹配器
import org.springframework.ai.chat.client.ChatClient; // Spring AI聊天客户端
import org.springframework.ai.document.Document; // Spring AI文档类
import org.springframework.ai.embedding.Embedding; // Spring AI嵌入类
import org.springframework.ai.embedding.EmbeddingModel; // Spring AI嵌入模型接口
import org.springframework.ai.embedding.EmbeddingRequest; // Spring AI嵌入请求
import org.springframework.ai.embedding.EmbeddingResponse; // Spring AI嵌入响应
import org.springframework.boot.test.context.TestConfiguration; // Spring Boot测试配置注解
import org.springframework.context.annotation.Bean; // Spring Bean注解
import org.springframework.context.annotation.Primary; // Spring Primary注解（优先注入）
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // BCrypt密码编码器
import org.springframework.security.crypto.password.PasswordEncoder; // 密码编码器接口
import reactor.core.publisher.Flux; // Reactor响应式流类

import java.util.Collections; // 集合工具类

import static org.mockito.Mockito.mock; // Mockito mock方法
import static org.mockito.Mockito.when; // Mockito when方法

@TestConfiguration // 测试配置类，提供测试环境Bean
public class TestConfig { // 测试配置类定义

    @Bean // 声明为Spring Bean
    @Primary // 优先注入此Bean
    public ChatClient.Builder chatClientBuilder() { // Mock ChatClient.Builder
        ChatClient.Builder builder = mock(ChatClient.Builder.class); // Mock Builder
        ChatClient chatClient = mock(ChatClient.class); // Mock ChatClient
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class); // Mock请求规范
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class); // Mock同步响应
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class); // Mock流式响应

        when(builder.defaultSystem(ArgumentMatchers.anyString())).thenReturn(builder); // Mock defaultSystem返回builder
        when(builder.build()).thenReturn(chatClient); // Mock build返回chatClient
        when(chatClient.prompt()).thenReturn(requestSpec); // Mock prompt返回requestSpec
        when(requestSpec.user(ArgumentMatchers.anyString())).thenReturn(requestSpec); // Mock user返回requestSpec
        when(requestSpec.system(ArgumentMatchers.anyString())).thenReturn(requestSpec); // Mock system返回requestSpec
        when(requestSpec.call()).thenReturn(callResponseSpec); // Mock call返回callResponseSpec
        when(callResponseSpec.content()).thenReturn("测试回复"); // Mock content返回测试回复
        when(requestSpec.stream()).thenReturn(streamResponseSpec); // Mock stream返回streamResponseSpec
        when(streamResponseSpec.content()).thenReturn(Flux.just("测试", "回复")); // Mock流式内容返回"测试""回复"

        return builder; // 返回Mock的Builder
    } // chatClientBuilder方法结束

    @Bean // 声明为Spring Bean
    @Primary // 优先注入
    public EmbeddingModel embeddingModel() { // Mock EmbeddingModel
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class); // Mock嵌入模型
        float[] mockVector = new float[768]; // 创建768维mock向量（与nomic-embed-text维度一致）
        for (int i = 0; i < 768; i++) { // 循环初始化向量
            mockVector[i] = 0.0f; // 每个维度设为0
        } // for循环结束
        Embedding embedding = new Embedding(mockVector, 0); // 创建Embedding对象
        EmbeddingResponse embeddingResponse = new EmbeddingResponse( // 创建EmbeddingResponse
                Collections.singletonList(embedding), // embedding列表
                null // 元数据为null
        );
        when(embeddingModel.embed(ArgumentMatchers.any(String.class))).thenReturn(mockVector); // Mock embed(String)返回向量
        when(embeddingModel.embed(ArgumentMatchers.any(Document.class))).thenReturn(mockVector); // Mock embed(Document)返回向量
        when(embeddingModel.call(ArgumentMatchers.any(EmbeddingRequest.class))).thenReturn(embeddingResponse); // Mock call返回响应
        return embeddingModel; // 返回Mock的EmbeddingModel
    } // embeddingModel方法结束

    @Bean // 声明为Spring Bean
    public PasswordEncoder passwordEncoder() { // 提供密码编码器
        return new BCryptPasswordEncoder(); // 返回BCrypt密码编码器实例
    } // passwordEncoder方法结束
} // TestConfig类结束
