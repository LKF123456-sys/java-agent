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

@Slf4j
@Component
public class WebSearchTool {

    private final String apiKey;
    private final String baseUrl;
    private final int maxResults;
    private final String searchDepth;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

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

    @Tool(description = "联网搜索互联网获取实时信息。当用户询问实时新闻、最新事件、近期数据、技术文档、产品价格、天气之外的实时信息，或者需要查证事实时，必须使用此工具搜索互联网获取最新信息。不要凭空猜测你不知道的最新信息。")
    public String searchWeb(
            @ToolParam(description = "搜索关键词，要具体明确，如'2025年Spring AI最新版本'、'北京今日新闻'、'Python 3.12新特性'等") String query,
            @ToolParam(description = "搜索深度：basic（快速搜索，默认）或 advanced（深度搜索，结果更全面但稍慢）", required = false) String depth,
            @ToolParam(description = "返回结果数量，默认5条，最多10条", required = false) Integer limit) {
        log.info("联网搜索工具被调用: query={}, depth={}, limit={}", query, depth, limit);

        int resultLimit = limit != null ? Math.min(limit, 10) : maxResults;
        String searchDepthStr = depth != null ? depth : searchDepth;

        try {
            Map<String, Object> requestBody = Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "search_depth", searchDepthStr,
                    "max_results", resultLimit,
                    "include_answer", true,
                    "include_raw_content", false
            );

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/search"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Tavily搜索API返回错误: status={}, body={}", response.statusCode(), response.body());
                return "搜索失败: API返回状态码 " + response.statusCode();
            }

            JsonNode root = objectMapper.readTree(response.body());
            return formatSearchResults(root, query);

        } catch (Exception e) {
            log.error("联网搜索异常: query={}, error={}", query, e.getMessage(), e);
            return "搜索失败: " + e.getMessage();
        }
    }

    private String formatSearchResults(JsonNode root, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 搜索结果：").append(query).append("\n\n");

        JsonNode answerNode = root.get("answer");
        if (answerNode != null && !answerNode.asText().isEmpty()) {
            sb.append("### AI摘要\n").append(answerNode.asText()).append("\n\n");
        }

        JsonNode results = root.get("results");
        if (results != null && results.isArray()) {
            sb.append("### 搜索结果详情\n");
            List<String> sources = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JsonNode item = results.get(i);
                String title = item.path("title").asText("无标题");
                String url = item.path("url").asText("");
                String content = item.path("content").asText("");

                sb.append(i + 1).append(". **").append(title).append("**\n");
                if (!content.isEmpty()) {
                    String snippet = content.length() > 300 ? content.substring(0, 300) + "..." : content;
                    sb.append("   ").append(snippet.replace("\n", " ")).append("\n");
                }
                sb.append("   🔗 ").append(url).append("\n\n");
                sources.add(url);
            }
        }

        sb.append("---\n");
        sb.append("以上信息来自互联网搜索，请根据这些搜索结果综合回答用户问题，并在回答中标注信息来源。");
        return sb.toString();
    }
}
