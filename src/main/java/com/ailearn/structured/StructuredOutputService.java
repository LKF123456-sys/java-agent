package com.ailearn.structured;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 结构化输出服务类
 * 利用Spring AI的结构化输出能力，将大模型的非结构化文本输出转换为强类型Java对象：
 * <ul>
 *   <li><b>图书信息提取</b>：从自然语言描述中提取图书名称、作者、分类、简介等结构化信息</li>
 *   <li><b>电影信息提取</b>：从自然语言描述中提取电影名称、导演、演员、类型、评分等结构化信息</li>
 *   <li><b>图书列表提取</b>：从自然语言文本中提取多本图书的信息列表</li>
 * </ul>
 *
 * <p>底层通过Spring AI的entity映射功能，自动根据BookInfo/MovieInfo的@JsonPropertyDescription注解
 * 生成JSON Schema并引导大模型输出符合格式的JSON，然后反序列化为Java Record对象，确保类型安全。
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
public class StructuredOutputService {

    /**
     * ChatClient实例
     * 由Spring AI自动注入，用于调用大模型并进行结构化输出映射
     */
    private final ChatClient chatClient;

    /**
     * 构造方法：初始化结构化输出服务
     *
     * @param chatClientBuilder ChatClient构建器
     */
    public StructuredOutputService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        log.info("结构化输出服务初始化完成");
    }

    /**
     * 从自然语言描述中提取图书信息
     * 将用户对图书的自然语言描述转换为结构化的BookInfo对象
     *
     * @param text 用户对图书的自然语言描述
     * @return BookInfo 结构化的图书信息对象，包含书名、作者、ISBN、出版社、简介等字段
     */
    public BookInfo extractBookInfo(String text) {
        return chatClient.prompt()
                .user("请从以下描述中提取图书信息，按照要求的JSON格式返回：" + text)
                .call()
                .entity(BookInfo.class);
    }

    /**
     * 从自然语言描述中提取电影信息
     * 将用户对电影的自然语言描述转换为结构化的MovieInfo对象
     *
     * @param text 用户对电影的自然语言描述
     * @return MovieInfo 结构化的电影信息对象，包含片名、导演、演员、类型、评分、票房等字段
     */
    public MovieInfo extractMovieInfo(String text) {
        return chatClient.prompt()
                .user("请从以下描述中提取电影信息，按照要求的JSON格式返回：" + text)
                .call()
                .entity(MovieInfo.class);
    }

    /**
     * 从自然语言文本中提取多本图书信息列表
     * 支持从一段文本中提取多本图书的结构化信息
     *
     * @param text 包含多本图书信息的自然语言文本
     * @return List<BookInfo> 结构化的图书信息列表
     */
    public List<BookInfo> extractBookList(String text) {
        return chatClient.prompt()
                .user("请从以下描述中提取所有提到的图书信息列表，按照要求的JSON数组格式返回：" + text)
                .call()
                .entity(new ParameterizedTypeReference<>() {});
    }
}
