package com.ailearn.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 联网搜索工具类
 * 提供给AI Agent调用的互联网搜索功能，基于Tavily Search API实现。
 * 当大模型需要获取实时信息、最新新闻、技术文档、产品价格等训练数据截止后的信息时，
 * 通过此工具联网搜索获取最新数据，避免模型产生幻觉。
 *
 * <p>功能特性：
 * <ul>
 *   <li>支持basic（快速）和advanced（深度）两种搜索模式</li>
 *   <li>可配置返回结果数量（最多10条）</li>
 *   <li>自动获取AI生成的答案摘要</li>
 *   <li>格式化输出搜索结果，包含标题、摘要、链接</li>
 * </ul>
 *
 * <p>API调用流程：
 * <ol>
 *   <li>构建搜索请求体（包含API Key、查询词、搜索深度、结果数量等参数）</li>
 *   <li>发送POST请求到Tavily /search端点</li>
 *   <li>解析JSON响应，提取AI摘要和搜索结果列表</li>
 *   <li>格式化结果为Markdown格式返回，包含标题、内容摘要、来源链接</li>
 * </ol>
 *
 * @author AiLearn Platform
 */
@Slf4j
@Component
public class WebSearchTool {

    /**
     * Tavily API密钥
     * 从配置文件tavily.api-key注入，用于API身份认证
     */
    private final String apiKey;

    /**
     * Tavily API基础URL
     * 从配置文件tavily.base-url注入，默认为https://api.tavily.com
     */
    private final String baseUrl;

    /**
     * 默认返回结果数量
     * 从配置文件tavily.max-results注入，默认为5条
     */
    private final int maxResults;

    /**
     * 默认搜索深度
     * 从配置文件tavily.search-depth注入，可选值：basic/advanced，默认为basic
     */
    private final String searchDepth;

    /**
     * Jackson ObjectMapper实例，用于JSON序列化和反序列化
     */
    private final ObjectMapper objectMapper;

    /**
     * HTTP客户端实例，用于调用Tavily搜索API
     * 配置10秒连接超时
     */
    private final HttpClient httpClient;

    /**
     * 构造方法：初始化联网搜索工具
     * 通过Spring依赖注入获取配置参数和ObjectMapper，创建HTTP客户端
     *
     * @param apiKey       Tavily API密钥
     * @param baseUrl      Tavily API基础URL，默认https://api.tavily.com
     * @param maxResults   默认返回结果数量，默认5条
     * @param searchDepth  默认搜索深度，默认basic
     * @param objectMapper Jackson ObjectMapper实例
     */
    public WebSearchTool(
            @Value("${tavily.api-key}") String apiKey,
            @Value("${tavily.base-url:https://api.tavily.com}") String baseUrl,
            @Value("${tavily.max-results:5}") int maxResults,
            @Value("${tavily.search-depth:basic}") String searchDepth,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.maxResults = maxResults;
        this.searchDepth = searchDepth;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("WebSearchTool初始化完成: baseUrl={}, maxResults={}, depth={}", baseUrl, maxResults, searchDepth);
    }

    /**
     * 联网搜索互联网获取实时信息
     * 当用户询问实时新闻、最新事件、近期数据、技术文档、产品价格等实时信息，
     * 或者需要查证事实时，使用此工具搜索互联网获取最新信息。
     *
     * <p>API调用逻辑：
     * <ol>
     *   <li>参数处理：确定结果数量限制和搜索深度，优先使用调用参数，否则使用默认配置</li>
     *   <li>构建请求体：包含api_key、query、search_depth、max_results等参数，启用AI答案</li>
     *   <li>序列化请求体为JSON，设置Content-Type为application/json</li>
     *   <li>发送POST请求到{baseUrl}/search端点，设置30秒超时（搜索可能耗时较长）</li>
     *   <li>检查HTTP状态码，非200则返回错误信息</li>
     *   <li>解析JSON响应，调用formatSearchResults格式化结果</li>
     * </ol>
     *
     * @param query 搜索关键词，要具体明确，如'2025年Spring AI最新版本'、'北京今日新闻'、'Python 3.12新特性'等
     * @param depth 搜索深度：basic（快速搜索，默认）或 advanced（深度搜索，结果更全面但稍慢）
     * @param limit 返回结果数量，默认5条，最多10条
     * @return String 格式化的搜索结果，包含AI摘要、搜索结果详情（标题、摘要、链接）
     */
    @Tool(description = "联网搜索互联网获取实时信息。当用户询问实时新闻、最新事件、近期数据、技术文档、产品价格、天气之外的实时信息，或者需要查证事实时，必须使用此工具搜索互联网获取最新信息。不要凭空猜测你不知道的最新信息。")
    public String searchWeb(
            @ToolParam(description = "搜索关键词，要具体明确，如'2025年Spring AI最新版本'、'北京今日新闻'、'Python 3.12新特性'等") String query,
            @ToolParam(description = "搜索深度：basic（快速搜索，默认）或 advanced（深度搜索，结果更全面但稍慢）", required = false) String depth,
            @ToolParam(description = "返回结果数量，默认5条，最多10条", required = false) Integer limit) {
        log.info("联网搜索工具被调用: query={}, depth={}, limit={}", query, depth, limit);

        // 处理结果数量：优先使用传入参数，限制最大10条
        int resultLimit = limit != null ? Math.min(limit, 10) : maxResults;
        // 处理搜索深度：优先使用传入参数，否则使用配置默认值
        String searchDepthStr = depth != null ? depth : searchDepth;

        try {
            // 构建Tavily搜索API请求体
            // include_answer=true: 让API返回AI生成的答案摘要
            // include_raw_content=false: 不返回原始网页内容，只返回摘要（减少响应大小）
            Map<String, Object> requestBody = Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "search_depth", searchDepthStr,
                    "max_results", resultLimit,
                    "include_answer", true,
                    "include_raw_content", false
            );

            // 将请求体序列化为JSON字符串
            String requestJson = objectMapper.writeValueAsString(requestBody);

            // 构建HTTP POST请求
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/search"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))  // 搜索可能需要较长时间，设置30秒超时
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            // 发送请求并获取响应
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // 检查HTTP响应状态码
            if (response.statusCode() != 200) {
                log.error("Tavily搜索API返回错误: status={}, body={}", response.statusCode(), response.body());
                return "搜索失败: API返回状态码 " + response.statusCode();
            }

            // 解析JSON响应并格式化输出
            JsonNode root = objectMapper.readTree(response.body());
            return formatSearchResults(root, query);

        } catch (Exception e) {
            log.error("联网搜索异常: query={}, error={}", query, e.getMessage(), e);
            return "搜索失败: " + e.getMessage();
        }
    }

    /**
     * 格式化搜索结果为易读的Markdown格式
     * 从Tavily API响应中提取AI摘要和搜索结果列表，组织成结构化文本
     *
     * @param root  Tavily API返回的JSON根节点
     * @param query 原始搜索查询词
     * @return String 格式化后的Markdown文本，包含AI摘要、搜索结果详情和来源提示
     */
    private String formatSearchResults(JsonNode root, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 搜索结果：").append(query).append("\n\n");

        // 提取AI生成的答案摘要（如果有）
        JsonNode answerNode = root.get("answer");
        if (answerNode != null && !answerNode.asText().isEmpty()) {
            sb.append("### AI摘要\n").append(answerNode.asText()).append("\n\n");
        }

        // 提取搜索结果列表
        JsonNode results = root.get("results");
        if (results != null && results.isArray()) {
            sb.append("### 搜索结果详情\n");
            List<String> sources = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JsonNode item = results.get(i);
                // 提取标题，无标题时显示"无标题"
                String title = item.path("title").asText("无标题");
                // 提取URL链接
                String url = item.path("url").asText("");
                // 提取内容摘要
                String content = item.path("content").asText("");

                // 添加序号和加粗标题
                sb.append(i + 1).append(". **").append(title).append("**\n");
                if (!content.isEmpty()) {
                    // 内容摘要截断到300字符，过长部分用...表示，并将换行替换为空格
                    String snippet = content.length() > 300 ? content.substring(0, 300) + "..." : content;
                    sb.append("   ").append(snippet.replace("\n", " ")).append("\n");
                }
                // 添加来源链接
                sb.append("   🔗 ").append(url).append("\n\n");
                sources.add(url);
            }
        }

        // 添加分隔线和来源提示
        sb.append("---\n");
        sb.append("以上信息来自互联网搜索，请根据这些搜索结果综合回答用户问题，并在回答中标注信息来源。");
        return sb.toString();
    }
}
