package com.ailearn.agent;

// 导入业务异常类
import com.ailearn.common.BusinessException;
// 导入错误码枚举
import com.ailearn.common.ErrorCode;
// 导入Agent聊天请求DTO
import com.ailearn.dto.AgentChatRequest;
// 导入会话实体类
import com.ailearn.entity.Conversation;
// 导入数据库聊天记忆实现
import com.ailearn.memory.DatabaseChatMemory;
// 导入用户安全主体
import com.ailearn.security.UserPrincipal;
// 导入会话管理服务
import com.ailearn.service.ConversationService;
// 导入联网搜索工具，用于先搜索后回答
import com.ailearn.tools.WebSearchTool;
// 导入Resilience4j限流器注解
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
// 导入Lombok日志注解
import lombok.extern.slf4j.Slf4j;
// 导入Spring AI ChatClient
import org.springframework.ai.chat.client.ChatClient;
// 导入消息记忆顾问
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
// 导入ChatMemory接口
import org.springframework.ai.chat.memory.ChatMemory;
// 导入ChatModel接口
import org.springframework.ai.chat.model.ChatModel;
// 导入Spring Service注解
import org.springframework.stereotype.Service;
// 导入Spring字符串工具类
import org.springframework.util.StringUtils;
// 导入Reactor Flux响应式流
import reactor.core.publisher.Flux;
// 导入Reactor Mono单值响应式容器
import reactor.core.publisher.Mono;
// 导入弹性线程池调度器
import reactor.core.scheduler.Schedulers;

/**
 * 联网搜索Agent服务
 * 实现"先搜索后总结"的智能问答模式：先自动联网搜索互联网获取实时信息，
 * 再基于搜索结果生成准确、有来源标注的回答。
 *
 * <p>工作流程：
 * 1. 接收用户问题
 * 2. 调用Tavily搜索引擎获取实时搜索结果
 * 3. 将搜索结果注入用户提示词
 * 4. LLM基于搜索结果生成带来源标注的回答
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
// 使用Resilience4j限流器，限制searchAgentService的调用频率
@RateLimiter(name = "searchAgentService")
public class SearchAgentService {

    /**
     * 搜索Agent客户端，预配置了系统提示词和记忆顾问（不注册工具，搜索由WebSearchTool直接完成）
     */
    private final ChatClient searchAgentClient;

    /**
     * 会话管理服务
     */
    private final ConversationService conversationService;

    /**
     * 联网搜索工具，封装了Tavily搜索引擎API调用
     */
    private final WebSearchTool webSearchTool;

    /**
     * 构造方法：初始化搜索Agent客户端
     *
     * @param chatModel            AI大模型客户端
     * @param chatMemory           数据库聊天记忆
     * @param conversationService  会话管理服务
     * @param webSearchTool        联网搜索工具
     */
    public SearchAgentService(ChatModel chatModel,
                              DatabaseChatMemory chatMemory,
                              ConversationService conversationService,
                              WebSearchTool webSearchTool) {
        // 保存会话服务引用
        this.conversationService = conversationService;
        // 保存搜索工具引用
        this.webSearchTool = webSearchTool;
        // 构建搜索Agent客户端
        this.searchAgentClient = ChatClient.builder(chatModel)
                // 设置搜索助手的系统提示词，定义其"先搜索后总结"的工作原则
                .defaultSystem("""
                        你是一个专业的联网搜索助手，名叫"赛博搜索官"。系统会先帮你联网搜索互联网，并将搜索结果附在用户问题之后供你参考。
                        
                        你的工作原则：
                        1. **基于搜索结果回答**：优先使用搜索结果中的信息回答，不要凭空编造你不确定的事实。
                        2. **信息综合**：综合多条搜索结果给出准确、有条理的回答，不要简单堆砌搜索结果。
                        3. **标注来源**：回答中引用具体信息时，标注来源链接，格式为 [来源](URL)，让用户可以追验证。
                        4. **诚实透明**：如果搜索结果不足以回答问题，明确告知用户，不要编造信息。
                        5. **时效性说明**：如果问题涉及实时信息，说明数据的时间。
                        6. **常识问题**：对于常识性问题（如"水的沸点"），可以直接回答，但如果搜索结果中有更准确的数据，以搜索结果为准。
                        
                        回答格式：
                        - 先给出直接回答/结论
                        - 然后展开详细说明
                        - 最后列出"📎 参考来源"，列出引用的链接
                        """)
                // 注册消息记忆顾问
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        log.info("SearchAgentService初始化完成（先搜索后总结模式）");
    }

    /**
     * 构建带搜索结果的用户提示词
     * 先调用Tavily搜索引擎搜索互联网，然后将搜索结果拼接到用户问题后面
     *
     * @param task 用户原始问题
     * @return 包含搜索结果的结构化用户提示词
     */
    private String buildUserPromptWithSearch(String task) {
        // 问题为空时返回提示信息
        if (!StringUtils.hasText(task)) {
            return "用户问题：(空)\n\n注意：用户未提供有效问题。";
        }
        // 记录搜索日志，截取问题前50字符
        log.info("正在执行联网搜索: query={}", task.length() > 50 ? task.substring(0, 50) + "..." : task);
        // 调用WebSearchTool执行搜索，使用basic模式，返回5条结果
        String searchResults = webSearchTool.searchWeb(task, "basic", 5);
        // 将用户问题和搜索结果组合为结构化提示词
        return "用户问题：" + task + "\n\n" +
                "以下是系统通过Tavily搜索引擎获取的实时搜索结果，请基于这些信息回答用户问题：\n\n" +
                searchResults;
    }

    /**
     * 搜索Agent同步调用
     * 先联网搜索，再基于搜索结果生成回答，同步返回完整结果
     *
     * @param req  Agent聊天请求
     * @param user 当前登录用户
     * @return 带来源标注的完整回答文本
     * @throws BusinessException 任务为空、会话不存在或调用失败时抛出
     */
    public String callWithSearch(AgentChatRequest req, UserPrincipal user) {
        Long userId = user.getUserId();
        log.info("搜索Agent同步调用开始: userId={}, task={}",
                userId,
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        Long conversationId = req.getConversationId();
        Long finalConversationId;

        // 未提供会话ID时自动创建新会话
        if (conversationId == null) {
            Conversation conversation = conversationService.createConversation(
                    userId,
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "search-agent"
            );
            finalConversationId = conversation.getId();
            req.setConversationId(finalConversationId);
        } else {
            // 验证会话归属权
            Conversation existing = conversationService.getConversationById(userId, conversationId);
            if (existing == null) {
                throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
            }
            finalConversationId = conversationId;
        }

        // 保存用户消息到数据库
        conversationService.saveMessage(userId, finalConversationId, "user", task);

        // 执行联网搜索，构建带搜索结果的用户提示词
        String userPrompt;
        try {
            userPrompt = buildUserPromptWithSearch(task);
        } catch (Exception e) {
            // 搜索失败时降级为基于LLM知识回答
            log.error("联网搜索失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e);
            userPrompt = "用户问题：" + task + "\n\n注意：联网搜索失败，请基于你的知识回答并告知用户搜索暂时不可用。";
        }

        String response;
        try {
            // 调用搜索Agent，使用search_前缀隔离记忆上下文
            response = searchAgentClient.prompt()
                    .user(userPrompt)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "search_" + finalConversationId))
                    .call()
                    .content();
            int len = response != null ? response.length() : 0;
            log.info("搜索Agent同步调用完成: conversationId={}, responseLength={}", finalConversationId, len);
        } catch (Exception e) {
            log.error("搜索Agent调用失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, e);
        }

        // 保存AI回复到数据库
        if (StringUtils.hasText(response)) {
            conversationService.saveMessage(userId, finalConversationId, "assistant", response);
        }

        return response != null ? response : "";
    }

    /**
     * 搜索Agent SSE流式调用
     * 先显示搜索状态提示，完成搜索后流式输出基于搜索结果的回答
     *
     * @param req  Agent聊天请求
     * @param user 当前登录用户
     * @return Flux&lt;String&gt; SSE数据流，包含搜索状态提示和回答token
     * @throws BusinessException 任务为空或会话不存在时抛出
     */
    public Flux<String> streamCallWithSearch(AgentChatRequest req, UserPrincipal user) {
        Long userId = user.getUserId();
        log.info("搜索Agent流式调用开始: userId={}, task={}",
                userId,
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        Long conversationId = req.getConversationId();
        Long finalConversationId;

        // 未提供会话ID时自动创建
        if (conversationId == null) {
            Conversation conversation = conversationService.createConversation(
                    userId,
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "search-agent"
            );
            finalConversationId = conversation.getId();
            req.setConversationId(finalConversationId);
        } else {
            Conversation existing = conversationService.getConversationById(userId, conversationId);
            if (existing == null) {
                throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
            }
            finalConversationId = conversationId;
        }

        // 保存用户消息
        conversationService.saveMessage(userId, finalConversationId, "user", task);
        // 保存为final变量供lambda使用
        final Long convId = finalConversationId;
        final Long uid = userId;

        // 创建响应累积缓冲区
        StringBuilder responseBuilder = new StringBuilder();
        // 搜索中的提示消息
        String searchingMsg = "🔍 正在联网搜索...\n\n";

        // 在弹性线程池上异步执行联网搜索（搜索是阻塞IO操作）
        Mono<String> searchMono = Mono.fromCallable(() -> {
            try {
                return buildUserPromptWithSearch(task);
            } catch (Exception e) {
                // 搜索失败时降级
                log.error("联网搜索失败: conversationId={}, error={}", convId, e.getMessage(), e);
                return "用户问题：" + task + "\n\n注意：联网搜索暂时不可用，请基于你的知识回答并告知用户。";
            }
        }).subscribeOn(Schedulers.boundedElastic());

        // 先发送搜索中提示，然后等待搜索完成后流式输出LLM回答
        return Flux.just(searchingMsg)
                // 将搜索提示累积到缓冲区
                .doOnNext(responseBuilder::append)
                // 搜索完成后拼接LLM流式输出
                .concatWith(searchMono.flatMapMany(userPrompt -> {
                    // 检查搜索是否失败（降级标记）
                    boolean searchFailed = userPrompt.contains("联网搜索暂时不可用");
                    if (searchFailed) {
                        // 搜索失败时发送失败提示
                        String failMsg = "⚠️ 联网搜索失败，将基于已有知识回答...\n\n";
                        responseBuilder.append(failMsg);
                        return Flux.just(failMsg)
                                .concatWith(streamLlmResponse(userPrompt, finalConversationId, responseBuilder));
                    }
                    // 搜索成功，直接流式输出LLM回答
                    return streamLlmResponse(userPrompt, finalConversationId, responseBuilder);
                }))
                // 流完成时保存完整AI回复到数据库
                .doOnComplete(() -> {
                    String fullResponse = responseBuilder.toString();
                    if (StringUtils.hasText(fullResponse)) {
                        // 去除搜索状态提示消息，只保存有效回答内容
                        String assistantContent = fullResponse
                                .replace("🔍 正在联网搜索...\n\n", "")
                                .replace("⚠️ 联网搜索失败，将基于已有知识回答...\n\n", "");
                        conversationService.saveMessage(uid, finalConversationId, "assistant", assistantContent);
                        log.info("搜索Agent流式调用完成: conversationId={}, responseLength={}",
                                finalConversationId, assistantContent.length());
                    }
                })
                // 流异常时记录错误日志
                .doOnError(e -> log.error("搜索Agent流式调用失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e))
                // 异常处理：返回错误消息防止前端无限等待
                .onErrorResume(e -> {
                    String errMsg = e.getMessage() != null ? e.getMessage() : "搜索Agent调用失败";
                    log.warn("搜索Agent流异常，发送错误消息: {}", errMsg);
                    return Flux.just("[ERROR] " + errMsg);
                });
    }

    /**
     * 流式输出LLM回答的辅助方法
     * 将用户提示词发送给搜索Agent，流式返回token
     *
     * @param userPrompt     包含搜索结果的完整用户提示词
     * @param conversationId 会话ID
     * @param responseBuilder 响应累积缓冲区
     * @return Flux&lt;String&gt; 流式token序列
     */
    private Flux<String> streamLlmResponse(String userPrompt, Long conversationId, StringBuilder responseBuilder) {
        // 调用搜索Agent流式输出，使用search_前缀隔离记忆
        return searchAgentClient.prompt()
                .user(userPrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "search_" + conversationId))
                .stream()
                .content()
                // 每个token同时累积到缓冲区
                .doOnNext(responseBuilder::append);
    }
}
