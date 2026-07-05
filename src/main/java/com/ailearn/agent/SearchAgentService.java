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

/**
 * 联网搜索智能体服务类
 * 实现"先搜索后总结"的联网问答模式：系统先调用搜索引擎获取实时信息，
 * 然后将搜索结果作为上下文提供给大模型，由大模型综合整理后给出准确回答。
 *
 * <p>核心特性：
 * <ul>
 *   <li>自动联网搜索：对用户问题先执行Tavily联网搜索，获取实时信息</li>
 *   <li>基于搜索结果回答：大模型基于真实搜索结果回答，避免幻觉</li>
 *   <li>来源标注：回答中标注信息来源链接，支持追溯验证</li>
 *   <li>降级处理：搜索失败时自动降级为基于模型知识回答，并告知用户</li>
 *   <li>SSE流式输出：支持实时推送搜索状态和回答token</li>
 * </ul>
 *
 * <p>执行流程：用户提问 → 联网搜索 → 构建增强Prompt（含搜索结果）→ 大模型总结回答
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
@RateLimiter(name = "searchAgentService")
public class SearchAgentService {

    /**
     * 搜索Agent专用聊天客户端
     * 配置了"赛博搜索官"系统提示词，指导模型基于搜索结果回答
     */
    private final ChatClient searchAgentClient;

    /**
     * 会话服务，用于会话管理和消息持久化
     */
    private final ConversationService conversationService;

    /**
     * 网页搜索工具，封装Tavily搜索引擎API调用
     */
    private final WebSearchTool webSearchTool;

    /**
     * 构造方法：初始化联网搜索Agent服务
     * 构建搜索专用ChatClient，配置"赛博搜索官"角色提示词
     *
     * @param chatModel           聊天模型
     * @param chatMemory          数据库聊天记忆
     * @param conversationService 会话服务
     * @param webSearchTool       网页搜索工具
     */
    public SearchAgentService(ChatModel chatModel,
                              DatabaseChatMemory chatMemory,
                              ConversationService conversationService,
                              WebSearchTool webSearchTool) {
        this.conversationService = conversationService;
        this.webSearchTool = webSearchTool;
        this.searchAgentClient = ChatClient.builder(chatModel)
                // 搜索Agent系统提示词：定义"赛博搜索官"角色，要求基于搜索结果回答并标注来源
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

    /**
     * 构建包含搜索结果的用户Prompt
     * 先执行联网搜索，然后将用户问题和搜索结果拼接成增强的Prompt。
     *
     * @param task 用户原始问题/任务
     * @return String 包含搜索结果的增强Prompt
     */
    private String buildUserPromptWithSearch(String task) {
        log.info("正在执行联网搜索: query={}", task.length() > 50 ? task.substring(0, 50) + "..." : task);
        // 调用Tavily搜索引擎执行搜索，获取basic类型搜索结果，最多5条
        String searchResults = webSearchTool.searchWeb(task, "basic", 5);
        // 拼接用户问题和搜索结果，供大模型参考
        return "用户问题：" + task + "\n\n" +
                "以下是系统通过Tavily搜索引擎获取的实时搜索结果，请基于这些信息回答用户问题：\n\n" +
                searchResults;
    }

    /**
     * 联网搜索Agent同步调用
     * 完整流程：参数校验 → 会话处理 → 联网搜索 → 大模型总结 → 保存消息
     *
     * @param req  Agent聊天请求
     * @param user 当前登录用户
     * @return String 基于搜索结果的回答
     * @throws BusinessException 参数为空或调用失败时抛出异常
     */
    public String callWithSearch(AgentChatRequest req, UserPrincipal user) {
        log.info("搜索Agent同步调用开始: userId={}, task={}",
                user != null ? user.getUserId() : "anonymous",
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        // 会话处理：创建新会话或获取已有会话
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

        // 保存用户消息
        conversationService.saveMessage(finalConversationId, "user", task);

        String userPrompt;
        try {
            // 执行联网搜索，构建包含搜索结果的Prompt
            userPrompt = buildUserPromptWithSearch(task);
        } catch (Exception e) {
            // 搜索失败降级处理：告知模型搜索不可用，请求基于已有知识回答
            log.error("联网搜索失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e);
            userPrompt = "用户问题：" + task + "\n\n注意：联网搜索失败，请基于你的知识回答并告知用户搜索暂时不可用。";
        }

        String response;
        try {
            // 调用大模型基于搜索结果生成回答
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

        // 保存AI回复
        if (StringUtils.hasText(response)) {
            conversationService.saveMessage(finalConversationId, "assistant", response);
        }

        return response;
    }

    /**
     * 联网搜索Agent流式调用（SSE模式）
     * 以Flux流方式实时推送搜索状态和回答token，前端可实时看到搜索进度和回答生成过程。
     *
     * <p>Flux流处理链说明：
     * <ol>
     *   <li>首先推送"🔍 正在联网搜索..."提示消息</li>
     *   <li>使用Mono.fromCallable在boundedElastic调度器上异步执行搜索（避免阻塞）</li>
     *   <li>搜索完成后，根据搜索是否成功决定是否推送降级提示</li>
     *   <li>concatWith连接LLM流式响应，逐token输出最终回答</li>
     *   <li>doOnComplete时移除状态提示消息，保存纯回答内容到数据库</li>
     * </ol>
     *
     * @param req  Agent聊天请求
     * @param user 当前登录用户
     * @return Flux&lt;String&gt; SSE事件流
     */
    public Flux<String> streamCallWithSearch(AgentChatRequest req, UserPrincipal user) {
        log.info("搜索Agent流式调用开始: userId={}, task={}",
                user != null ? user.getUserId() : "anonymous",
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        // 会话处理
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

        // 保存用户消息
        conversationService.saveMessage(finalConversationId, "user", task);

        // 用于拼接完整回复（包含状态提示）
        StringBuilder responseBuilder = new StringBuilder();

        // 搜索中提示消息
        String searchingMsg = "🔍 正在联网搜索...\n\n";

        // 异步执行联网搜索，使用boundedElastic调度器避免阻塞响应式线程
        Mono<String> searchMono = Mono.fromCallable(() -> {
            try {
                return buildUserPromptWithSearch(task);
            } catch (Exception e) {
                log.error("联网搜索失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e);
                // 搜索失败降级
                return "用户问题：" + task + "\n\n注意：联网搜索暂时不可用，请基于你的知识回答并告知用户。";
            }
        }).subscribeOn(Schedulers.boundedElastic());

        // 构建Flux流：搜索提示 → 搜索执行 → LLM流式回答
        return Flux.just(searchingMsg)
                .doOnNext(responseBuilder::append)
                // 搜索完成后，连接LLM响应流
                .concatWith(searchMono.flatMapMany(userPrompt -> {
                    // 判断搜索是否失败（通过降级提示词检测）
                    boolean searchFailed = userPrompt.contains("联网搜索暂时不可用");
                    if (searchFailed) {
                        // 搜索失败：推送降级提示，再继续回答
                        String failMsg = "⚠️ 联网搜索失败，将基于已有知识回答...\n\n";
                        responseBuilder.append(failMsg);
                        return Flux.just(failMsg)
                                .concatWith(streamLlmResponse(userPrompt, finalConversationId, responseBuilder));
                    }
                    // 搜索成功：直接流式输出LLM回答
                    return streamLlmResponse(userPrompt, finalConversationId, responseBuilder);
                }))
                // 流完成时：清理状态提示，保存纯回答内容到数据库
                .doOnComplete(() -> {
                    String fullResponse = responseBuilder.toString();
                    if (StringUtils.hasText(fullResponse)) {
                        // 移除搜索中提示和失败提示，只保存实际回答内容
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

    /**
     * 私有方法：流式调用LLM生成回答
     * 封装大模型流式调用逻辑，逐token输出并追加到responseBuilder
     *
     * @param userPrompt      包含搜索结果的用户Prompt
     * @param conversationId  会话ID
     * @param responseBuilder 响应构建器，用于拼接完整回复
     * @return Flux&lt;String&gt; LLM生成的token流
     */
    private Flux<String> streamLlmResponse(String userPrompt, Long conversationId, StringBuilder responseBuilder) {
        return searchAgentClient.prompt()
                .user(userPrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "search_" + conversationId))
                .stream()
                .content()
                .doOnNext(responseBuilder::append);
    }
}
