package com.ailearn.agent;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.dto.AgentChatRequest;
import com.ailearn.entity.Conversation;
import com.ailearn.memory.DatabaseChatMemory;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.ConversationService;
import com.ailearn.tools.WebSearchTool;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RateLimiter(name = "searchAgentService")
public class SearchAgentService {

    private final ChatClient searchAgentClient;
    private final ConversationService conversationService;
    private final WebSearchTool webSearchTool;

    public SearchAgentService(ChatModel chatModel,
                              DatabaseChatMemory chatMemory,
                              ConversationService conversationService,
                              WebSearchTool webSearchTool) {
        this.conversationService = conversationService;
        this.webSearchTool = webSearchTool;
        this.searchAgentClient = ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是一个专业的联网搜索助手，名叫"赛博搜索官"。系统会先帮你联网搜索互联网，并将搜索结果附在用户问题之后供你参考。
                        
                        你的工作原则：
                        1. **基于搜索结果回答**：优先使用搜索结果中的信息回答，不要凭空编造你不确定的事实。
                        2. **信息综合**：综合多条搜索结果给出准确、有条理的回答，不要简单堆砌搜索结果。
                        3. **标注来源**：回答中引用具体信息时，标注来源链接，格式为 [来源](URL)，让用户可以追溯验证。
                        4. **诚实透明**：如果搜索结果不足以回答问题，明确告知用户，不要编造信息。
                        5. **时效性说明**：如果问题涉及实时信息，说明数据的时间。
                        6. **常识问题**：对于常识性问题（如"水的沸点"），可以直接回答，但如果搜索结果中有更准确的数据，以搜索结果为准。
                        
                        回答格式：
                        - 先给出直接回答/结论
                        - 然后展开详细说明
                        - 最后列出"📎 参考来源"，列出引用的链接
                        """)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        log.info("SearchAgentService初始化完成（先搜索后总结模式）");
    }

    private String buildUserPromptWithSearch(String task) {
        log.info("正在执行联网搜索: query={}", task.length() > 50 ? task.substring(0, 50) + "..." : task);
        String searchResults = webSearchTool.searchWeb(task, "basic", 5);
        return "用户问题：" + task + "\n\n" +
                "以下是系统通过Tavily搜索引擎获取的实时搜索结果，请基于这些信息回答用户问题：\n\n" +
                searchResults;
    }

    public String callWithSearch(AgentChatRequest req, UserPrincipal user) {
        log.info("搜索Agent同步调用开始: userId={}, task={}",
                user != null ? user.getUserId() : "anonymous",
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        Long conversationId = req.getConversationId();
        Long finalConversationId;

        if (conversationId == null) {
            Conversation conversation = conversationService.createConversation(
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "search-agent"
            );
            finalConversationId = conversation.getId();
        } else {
            conversationService.getConversationById(conversationId);
            finalConversationId = conversationId;
        }

        conversationService.saveMessage(finalConversationId, "user", task);

        String userPrompt;
        try {
            userPrompt = buildUserPromptWithSearch(task);
        } catch (Exception e) {
            log.error("联网搜索失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e);
            userPrompt = "用户问题：" + task + "\n\n注意：联网搜索失败，请基于你的知识回答并告知用户搜索暂时不可用。";
        }

        String response;
        try {
            response = searchAgentClient.prompt()
                    .user(userPrompt)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "search_" + finalConversationId))
                    .call()
                    .content();
            log.info("搜索Agent同步调用完成: conversationId={}, responseLength={}", finalConversationId, response.length());
        } catch (Exception e) {
            log.error("搜索Agent调用失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, e);
        }

        if (StringUtils.hasText(response)) {
            conversationService.saveMessage(finalConversationId, "assistant", response);
        }

        return response;
    }

    public Flux<String> streamCallWithSearch(AgentChatRequest req, UserPrincipal user) {
        log.info("搜索Agent流式调用开始: userId={}, task={}",
                user != null ? user.getUserId() : "anonymous",
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        Long conversationId = req.getConversationId();
        Long finalConversationId;

        if (conversationId == null) {
            Conversation conversation = conversationService.createConversation(
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "search-agent"
            );
            finalConversationId = conversation.getId();
        } else {
            conversationService.getConversationById(conversationId);
            finalConversationId = conversationId;
        }

        conversationService.saveMessage(finalConversationId, "user", task);

        StringBuilder responseBuilder = new StringBuilder();

        String searchingMsg = "🔍 正在联网搜索...\n\n";

        Mono<String> searchMono = Mono.fromCallable(() -> {
            try {
                return buildUserPromptWithSearch(task);
            } catch (Exception e) {
                log.error("联网搜索失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e);
                return "用户问题：" + task + "\n\n注意：联网搜索暂时不可用，请基于你的知识回答并告知用户。";
            }
        }).subscribeOn(Schedulers.boundedElastic());

        return Flux.just(searchingMsg)
                .doOnNext(responseBuilder::append)
                .concatWith(searchMono.flatMapMany(userPrompt -> {
                    boolean searchFailed = userPrompt.contains("联网搜索暂时不可用");
                    if (searchFailed) {
                        String failMsg = "⚠️ 联网搜索失败，将基于已有知识回答...\n\n";
                        responseBuilder.append(failMsg);
                        return Flux.just(failMsg)
                                .concatWith(streamLlmResponse(userPrompt, finalConversationId, responseBuilder));
                    }
                    return streamLlmResponse(userPrompt, finalConversationId, responseBuilder);
                }))
                .doOnComplete(() -> {
                    String fullResponse = responseBuilder.toString();
                    if (StringUtils.hasText(fullResponse)) {
                        String assistantContent = fullResponse
                                .replace("🔍 正在联网搜索...\n\n", "")
                                .replace("⚠️ 联网搜索失败，将基于已有知识回答...\n\n", "");
                        conversationService.saveMessage(finalConversationId, "assistant", assistantContent);
                        log.info("搜索Agent流式调用完成: conversationId={}, responseLength={}",
                                finalConversationId, assistantContent.length());
                    }
                })
                .doOnError(e -> log.error("搜索Agent流式调用失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e))
                .onErrorResume(e -> {
                    String errMsg = e.getMessage() != null ? e.getMessage() : "搜索Agent调用失败";
                    log.warn("搜索Agent流异常，发送错误消息: {}", errMsg);
                    return Flux.just("[ERROR] " + errMsg);
                });
    }

    private Flux<String> streamLlmResponse(String userPrompt, Long conversationId, StringBuilder responseBuilder) {
        return searchAgentClient.prompt()
                .user(userPrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "search_" + conversationId))
                .stream()
                .content()
                .doOnNext(responseBuilder::append);
    }
}
