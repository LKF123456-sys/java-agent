package com.ailearn.config;

import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 单元测试配置类
 * 提供测试环境下需要的Mock Bean和配置
 * 不加载真实的AI模型，使用Mockito Mock所有外部依赖
 *
 * @author AiLearn Platform
 */
@TestConfiguration
public class TestConfig {

    /**
     * Mock ChatClient.Builder
     * 防止Spring AI自动配置尝试连接Ollama服务
     */
    @Bean
    @Primary
    public ChatClient.Builder chatClientBuilder() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        // Mock构建链
        when(builder.defaultSystem(ArgumentMatchers.anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(ArgumentMatchers.anyString())).thenReturn(requestSpec);
        when(requestSpec.system(ArgumentMatchers.anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("测试回复");
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("测试", "回复"));

        return builder;
    }

    /**
     * Mock EmbeddingModel
     * 防止Spring AI Ollama自动配置尝试连接Ollama服务
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        // 返回一个768维的mock向量（与nomic-embed-text维度一致）
        float[] mockVector = new float[768];
        for (int i = 0; i < 768; i++) {
            mockVector[i] = 0.0f;
        }
        Embedding embedding = new Embedding(mockVector, 0);
        EmbeddingResponse embeddingResponse = new EmbeddingResponse(
                Collections.singletonList(embedding),
                null
        );
        when(embeddingModel.embed(ArgumentMatchers.any(String.class))).thenReturn(mockVector);
        when(embeddingModel.embed(ArgumentMatchers.any(Document.class))).thenReturn(mockVector);
        when(embeddingModel.call(ArgumentMatchers.any(EmbeddingRequest.class))).thenReturn(embeddingResponse);
        return embeddingModel;
    }

    /**
     * 提供密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
