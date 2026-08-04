package com.ailearn.tools; // 声明包名

// Jackson 3 迁移：包名由 com.fasterxml.jackson 更换为 tools.jackson（类名与API保持不变）
import tools.jackson.databind.JsonNode; // JSON树节点
import tools.jackson.databind.ObjectMapper; // JSON映射器
import lombok.extern.slf4j.Slf4j; // Lombok日志
import org.springframework.ai.tool.annotation.Tool; // Spring AI工具注解
import org.springframework.ai.tool.annotation.ToolParam; // Spring AI工具参数注解
import org.springframework.beans.factory.annotation.Value; // Spring属性注入
import org.springframework.stereotype.Component; // Spring组件注解

import java.net.URI; // URI
import java.net.http.HttpClient; // HTTP客户端
import java.net.http.HttpRequest; // HTTP请求
import java.net.http.HttpResponse; // HTTP响应
import java.time.Duration; // 时间段
import java.util.ArrayList; // 动态数组
import java.util.List; // 列表
import java.util.Map; // Map接口

/**
 * 联网搜索工具类
 * 提供给AI Agent调用的互联网搜索功能，基于Tavily Search API实现。
 * 当大模型需要获取实时信息、最新新闻、技术文档、产品价格等训练数据截止后的信息时，
 * 通过此工具联网搜索获取最新数据，避免模型产生幻觉。
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log
@Component // 注册为Spring Bean
public class WebSearchTool { // 联网搜索工具类定义

    /** Tavily API密钥 */
    private final String apiKey; // 从yml注入

    /** Tavily API基础URL */
    private final String baseUrl; // 默认https://api.tavily.com

    /** 默认返回结果数量 */
    private final int maxResults; // 默认5

    /** 默认搜索深度 */
    private final String searchDepth; // basic或advanced

    /** Jackson ObjectMapper实例 */
    private final ObjectMapper objectMapper; // JSON解析器

    /** HTTP客户端实例 */
    private final HttpClient httpClient; // JDK内置HTTP客户端

    /**
     * 构造方法：初始化联网搜索工具
     * 通过Spring依赖注入获取配置参数和ObjectMapper，创建HTTP客户端
     */
    public WebSearchTool( // 构造器
            @Value("${tavily.api-key}") String apiKey, // 注入API Key
            @Value("${tavily.base-url:https://api.tavily.com}") String baseUrl, // 注入基础URL
            @Value("${tavily.max-results:5}") int maxResults, // 注入结果数
            @Value("${tavily.search-depth:basic}") String searchDepth, // 注入搜索深度
            ObjectMapper objectMapper) { // 注入ObjectMapper
        this.apiKey = apiKey; // 赋值
        this.baseUrl = baseUrl; // 赋值
        this.maxResults = maxResults; // 赋值
        this.searchDepth = searchDepth; // 赋值
        this.objectMapper = objectMapper; // 赋值
        this.httpClient = HttpClient.newBuilder() // 构建HTTP客户端
                .connectTimeout(Duration.ofSeconds(10)) // 10秒连接超时
                .build(); // 构建
        log.info("WebSearchTool初始化完成: baseUrl={}, maxResults={}, depth={}", baseUrl, maxResults, searchDepth); // 初始化日志
    }

    /**
     * 联网搜索互联网获取实时信息
     *
     * @param query 搜索关键词
     * @param depth 搜索深度：basic或advanced（可选）
     * @param limit 返回结果数量，默认5，最多10（可选）
     * @return String 格式化的搜索结果
     */
    @Tool(description = "联网搜索互联网获取实时信息。当用户询问实时新闻、最新事件、近期数据、技术文档、产品价格、天气之外的实时信息，或者需要查证事实时，必须使用此工具搜索互联网获取最新信息。不要凭空猜测你不知道的最新信息。") // 工具说明
    public String searchWeb( // 联网搜索方法
            @ToolParam(description = "搜索关键词，要具体明确，如'2025年Spring AI最新版本'、'北京今日新闻'、'Python 3.12新特性'等") String query, // 查询参数
            @ToolParam(description = "搜索深度：basic（快速搜索，默认）或 advanced（深度搜索，结果更全面但稍慢）", required = false) String depth, // 深度参数（可选）
            @ToolParam(description = "返回结果数量，默认5条，最多10条", required = false) Integer limit) { // 数量参数（可选）
        log.info("联网搜索工具被调用: query={}, depth={}, limit={}", query, depth, limit); // 业务日志

        // 处理结果数量：优先使用传入参数，限制最大10条
        int resultLimit = limit != null ? Math.min(limit, 10) : maxResults; // 传参优先，封顶10
        // 处理搜索深度：优先使用传入参数，否则使用配置默认值
        String searchDepthStr = depth != null ? depth : searchDepth; // 传参优先

        try { // 尝试搜索
            // 构建Tavily搜索API请求体
            Map<String, Object> requestBody = Map.of( // 不可变Map
                    "api_key", apiKey, // API密钥
                    "query", query, // 查询词
                    "search_depth", searchDepthStr, // 搜索深度
                    "max_results", resultLimit, // 结果数量
                    "include_answer", true, // 启用AI摘要
                    "include_raw_content", false // 不返回原始内容
            );

            // 将请求体序列化为JSON字符串
            String requestJson = objectMapper.writeValueAsString(requestBody); // Map转JSON

            // 构建HTTP POST请求
            HttpRequest httpRequest = HttpRequest.newBuilder() // 构建请求
                    .uri(URI.create(baseUrl + "/search")) // 请求URL
                    .header("Content-Type", "application/json") // JSON内容类型
                    .timeout(Duration.ofSeconds(30)) // 30秒超时（搜索较慢）
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson)) // POST JSON体
                    .build(); // 构建

            // 发送请求并获取响应
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString()); // 发送

            // 检查HTTP响应状态码
            if (response.statusCode() != 200) { // 非200
                log.error("Tavily搜索API返回错误: status={}, body={}", response.statusCode(), response.body()); // 错误日志
                return "搜索失败: API返回状态码 " + response.statusCode(); // 返回错误
            }

            // 解析JSON响应并格式化输出
            JsonNode root = objectMapper.readTree(response.body()); // 解析JSON
            return formatSearchResults(root, query); // 委托格式化

        } catch (Exception e) { // 异常
            log.error("联网搜索异常: query={}, error={}", query, e.getMessage(), e); // 错误日志
            return "搜索失败: " + e.getMessage(); // 返回错误信息
        }
    }

    /**
     * 格式化搜索结果为易读的Markdown格式
     *
     * @param root  Tavily API返回的JSON根节点
     * @param query 原始搜索查询词
     * @return String 格式化后的Markdown文本
     */
    private String formatSearchResults(JsonNode root, String query) { // 私有格式化方法
        StringBuilder sb = new StringBuilder(); // 文本构建器
        sb.append("## 搜索结果：").append(query).append("\n\n"); // 标题

        // 提取AI生成的答案摘要（如果有）
        JsonNode answerNode = root.get("answer"); // 取answer字段
        if (answerNode != null && !answerNode.asText().isEmpty()) { // 有摘要
            sb.append("### AI摘要\n").append(answerNode.asText()).append("\n\n"); // 输出摘要
        }

        // 提取搜索结果列表
        JsonNode results = root.get("results"); // 取results数组
        if (results != null && results.isArray()) { // 是数组
            sb.append("### 搜索结果详情\n"); // 小标题
            List<String> sources = new ArrayList<>(); // 来源列表
            for (int i = 0; i < results.size(); i++) { // 遍历结果
                JsonNode item = results.get(i); // 取第i条
                String title = item.path("title").asText("无标题"); // 标题（默认"无标题"）
                String url = item.path("url").asText(""); // URL
                String content = item.path("content").asText(""); // 内容摘要

                sb.append(i + 1).append(". **").append(title).append("**\n"); // 序号+加粗标题
                if (!content.isEmpty()) { // 有内容
                    // 内容摘要截断到300字符，过长用...表示
                    String snippet = content.length() > 300 ? content.substring(0, 300) + "..." : content; // 截断
                    sb.append("   ").append(snippet.replace("\n", " ")).append("\n"); // 换行转空格
                }
                sb.append("   🔗 ").append(url).append("\n\n"); // 来源链接
                sources.add(url); // 记录来源
            }
        }

        // 添加分隔线和来源提示
        sb.append("---\n"); // 分隔线
        sb.append("以上信息来自互联网搜索，请根据这些搜索结果综合回答用户问题，并在回答中标注信息来源。"); // 提示语
        return sb.toString(); // 返回格式化文本
    }
} // WebSearchTool类结束
