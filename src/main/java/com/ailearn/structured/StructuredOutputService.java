package com.ailearn.structured; // 声明包名

import lombok.extern.slf4j.Slf4j; // 日志注解
import org.springframework.ai.chat.client.ChatClient; // Spring AI聊天客户端
import org.springframework.ai.chat.model.ChatModel; // 聊天模型
import org.springframework.core.ParameterizedTypeReference; // 泛型类型引用
import org.springframework.stereotype.Service; // Service注解

import java.util.List; // 列表

/**
 * 结构化输出服务类
 * 利用Spring AI的结构化输出能力，将大模型的非结构化文本输出转换为强类型Java对象
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log
@Service // 业务层Bean
public class StructuredOutputService { // 结构化输出服务

    /** ChatClient实例 */
    private final ChatClient chatClient; // AI聊天客户端

    /**
     * 构造方法：初始化结构化输出服务
     *
     * @param chatModel AI大模型
     */
    public StructuredOutputService(ChatModel chatModel) { // 构造器
        this.chatClient = ChatClient.builder(chatModel).build(); // 构建ChatClient（无系统提示词）
        log.info("结构化输出服务初始化完成"); // 初始化日志
    }

    /**
     * 从自然语言描述中提取图书信息
     *
     * @param text 用户对图书的自然语言描述
     * @return BookInfo 结构化的图书信息对象
     */
    public BookInfo extractBookInfo(String text) { // 提取图书信息
        return chatClient.prompt() // 构建提示
                .user("请从以下描述中提取图书信息，按照要求的JSON格式返回：" + text) // 设置用户消息（含提取指令）
                .call() // 同步调用
                .entity(BookInfo.class); // 将JSON响应映射为BookInfo对象（Spring AI自动生成JSON Schema引导模型输出）
    }

    /**
     * 从自然语言描述中提取电影信息
     *
     * @param text 用户对电影的自然语言描述
     * @return MovieInfo 结构化的电影信息对象
     */
    public MovieInfo extractMovieInfo(String text) { // 提取电影信息
        return chatClient.prompt() // 构建提示
                .user("请从以下描述中提取电影信息，按照要求的JSON格式返回：" + text) // 用户消息
                .call() // 同步调用
                .entity(MovieInfo.class); // 映射为MovieInfo对象
    }

    /**
     * 从自然语言文本中提取多本图书信息列表
     *
     * @param text 包含多本图书信息的自然语言文本
     * @return List<BookInfo> 结构化的图书信息列表
     */
    public List<BookInfo> extractBookList(String text) { // 提取图书列表
        return chatClient.prompt() // 构建提示
                .user("请从以下描述中提取所有提到的图书信息列表，按照要求的JSON数组格式返回：" + text) // 用户消息
                .call() // 同步调用
                .entity(new ParameterizedTypeReference<>() {}); // 用ParameterizedTypeReference保留泛型信息，映射为List<BookInfo>
    }
} // StructuredOutputService类结束
