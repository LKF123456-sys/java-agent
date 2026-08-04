package com.ailearn.agent; // 声明当前类所在的包：agent（智能体模块）

// 导入业务异常类，用于抛出业务错误
import com.ailearn.common.BusinessException;
// 导入错误码枚举，定义了各类业务错误码
import com.ailearn.common.ErrorCode;
// 导入Agent聊天请求DTO，包含task（问题）和可选conversationId
import com.ailearn.dto.AgentChatRequest;
// 导入会话实体类，对应数据库的会话表
import com.ailearn.entity.Conversation;
// 导入数据库聊天记忆实现，把对话历史持久化到数据库
import com.ailearn.memory.DatabaseChatMemory;
// 导入用户安全主体，封装当前登录用户信息
import com.ailearn.security.UserPrincipal;
// 导入会话管理服务，负责会话的创建、查询、消息保存
import com.ailearn.service.ConversationService;
// 导入联网搜索工具，封装了Tavily搜索引擎API调用，用于先搜索后回答
import com.ailearn.tools.WebSearchTool;
// 导入Resilience4j限流器注解，限制本服务的调用频率
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
// 导入Lombok日志注解，自动生成log对象
import lombok.extern.slf4j.Slf4j;
// 导入Spring AI的ChatClient，流式API调用大模型的入口
import org.springframework.ai.chat.client.ChatClient;
// 导入消息记忆顾问，把历史对话自动注入上下文
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
// 导入ChatMemory接口，定义了记忆存取的规范
import org.springframework.ai.chat.memory.ChatMemory;
// 导入ChatModel接口，代表底层大模型（本项目是Ollama）
import org.springframework.ai.chat.model.ChatModel;
// 导入Spring的@Service注解，标记这是业务服务层Bean
import org.springframework.stereotype.Service;
// 导入Spring字符串工具类，提供hasText等空值判断方法
import org.springframework.util.StringUtils;
// 导入Reactor的Flux，0..N个元素的异步流（SSE流式响应用）
import reactor.core.publisher.Flux;
// 导入Reactor的Mono，0..1个元素的异步容器（包装搜索这种单结果操作）
import reactor.core.publisher.Mono;
// 导入Reactor的Schedulers，提供线程池调度（boundedElastic适合阻塞IO）
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
@Slf4j // Lombok注解：自动生成log日志对象
@Service // Spring注解：标记为业务服务Bean，交给Spring容器管理
// 使用Resilience4j限流器，限制searchAgentService的调用频率（防止搜索接口被刷爆）
@RateLimiter(name = "searchAgentService") // name对应application.yml里resilience4j.ratelimiter的配置
public class SearchAgentService { // 定义联网搜索Agent服务类

    /**
     * 搜索Agent客户端，预配置了系统提示词和记忆顾问（不注册工具，搜索由WebSearchTool直接完成）
     */
    private final ChatClient searchAgentClient; // 预配置的ChatClient，构建时就注入了系统提示词和记忆顾问

    /**
     * 会话管理服务
     */
    private final ConversationService conversationService; // 注入会话服务，用于创建会话、保存消息

    /**
     * 联网搜索工具，封装了Tavily搜索引擎API调用
     */
    private final WebSearchTool webSearchTool; // 注入联网搜索工具，直接调用（不走LLM工具决策）

    /**
     * 构造方法：初始化搜索Agent客户端
     *
     * @param chatModel            AI大模型客户端
     * @param chatMemory           数据库聊天记忆
     * @param conversationService  会话管理服务
     * @param webSearchTool        联网搜索工具
     */
    public SearchAgentService(ChatModel chatModel, // 构造器注入4个依赖（Spring自动传入对应Bean）
                              DatabaseChatMemory chatMemory,
                              ConversationService conversationService,
                              WebSearchTool webSearchTool) {
        // 保存会话服务引用到成员变量
        this.conversationService = conversationService;
        // 保存搜索工具引用到成员变量
        this.webSearchTool = webSearchTool;
        // 构建搜索Agent客户端：基于ChatModel构建一个预配置的ChatClient
        this.searchAgentClient = ChatClient.builder(chatModel) // 用ChatClient.builder()基于底层模型开始构建
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
                        """) // defaultSystem设置每次调用都自动携带的系统提示词
                // 注册消息记忆顾问：每次调用前自动把历史对话注入上下文
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()) // 用数据库记忆构建顾问
                .build(); // 构建完成，返回ChatClient实例
        log.info("SearchAgentService初始化完成（先搜索后总结模式）"); // 打印初始化日志
    }

    /**
     * 构建带搜索结果的用户提示词
     * 先调用Tavily搜索引擎搜索互联网，然后将搜索结果拼接到用户问题后面
     *
     * @param task 用户原始问题
     * @return 包含搜索结果的结构化用户提示词
     */
    private String buildUserPromptWithSearch(String task) { // 私有方法：搜索并拼装提示词
        // 问题为空时返回提示信息（防御性编程，避免空调用搜索引擎）
        if (!StringUtils.hasText(task)) { // StringUtils.hasText判断字符串非null且非空白
            return "用户问题：(空)\n\n注意：用户未提供有效问题。"; // 返回空问题提示
        }
        // 记录搜索日志，截取问题前50字符（避免日志过长）
        log.info("正在执行联网搜索: query={}", task.length() > 50 ? task.substring(0, 50) + "..." : task); // 三元运算符：超50字符截断加省略号
        // 调用WebSearchTool执行搜索，使用basic模式，返回5条结果
        String searchResults = webSearchTool.searchWeb(task, "basic", 5); // 实际发起HTTP请求调用Tavily API
        // 将用户问题和搜索结果组合为结构化提示词（LLM能看懂"这是用户问题+这是搜索结果"的结构）
        return "用户问题：" + task + "\n\n" + // 第一部分：用户原始问题
                "以下是系统通过Tavily搜索引擎获取的实时搜索结果，请基于这些信息回答用户问题：\n\n" + // 第二部分：引导语
                searchResults; // 第三部分：搜索结果原文
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
    public String callWithSearch(AgentChatRequest req, UserPrincipal user) { // 同步版本的搜索问答
        Long userId = user.getUserId(); // 从用户主体中取出用户ID
        log.info("搜索Agent同步调用开始: userId={}, task={}", // 打印调用开始日志
                userId, // 日志参数1：用户ID
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null"); // 日志参数2：截取任务前50字符

        String task = req.getTask(); // 取出用户问题
        if (!StringUtils.hasText(task)) { // 问题为空时抛业务异常
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY); // 抛出"消息为空"错误
        }

        Long conversationId = req.getConversationId(); // 取出请求中的会话ID（可能为null）
        Long finalConversationId; // 声明最终使用的会话ID（Java要求lambda中使用的变量必须是final或有效final）

        // 未提供会话ID时自动创建新会话
        if (conversationId == null) { // 情况1：用户没传会话ID（新对话）
            Conversation conversation = conversationService.createConversation( // 调用会话服务创建新会话
                    userId, // 参数1：用户ID（会话归属）
                    task.length() > 50 ? task.substring(0, 50) + "..." : task, // 参数2：用问题前50字符作为会话标题
                    "search-agent" // 参数3：会话类型标记为search-agent
            );
            finalConversationId = conversation.getId(); // 拿到新创建会话的ID
            req.setConversationId(finalConversationId); // 回写到请求对象，方便Controller返回给前端
        } else { // 情况2：用户传了会话ID（延续旧对话）
            // 验证会话归属权（防止越权访问别人的会话）
            Conversation existing = conversationService.getConversationById(userId, conversationId); // 按用户ID+会话ID查询
            if (existing == null) { // 查不到说明会话不存在或不属于当前用户
                throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND); // 抛出"会话不存在"错误
            }
            finalConversationId = conversationId; // 验证通过，使用传入的会话ID
        }

        // 保存用户消息到数据库（持久化对话历史）
        conversationService.saveMessage(userId, finalConversationId, "user", task); // role="user"表示这是用户发的消息

        // 执行联网搜索，构建带搜索结果的用户提示词
        String userPrompt; // 声明最终的提示词变量
        try {
            userPrompt = buildUserPromptWithSearch(task); // 尝试搜索并拼装提示词
        } catch (Exception e) { // 捕获搜索过程中的一切异常（网络超时、API限流等）
            // 搜索失败时降级为基于LLM知识回答（优雅降级，不让用户看到错误）
            log.error("联网搜索失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e); // 记录错误日志（含堆栈）
            userPrompt = "用户问题：" + task + "\n\n注意：联网搜索失败，请基于你的知识回答并告知用户搜索暂时不可用。"; // 降级提示词
        }

        String response; // 声明AI回答变量
        try {
            // 调用搜索Agent，使用search_前缀隔离记忆上下文（避免和普通聊天的记忆混淆）
            response = searchAgentClient.prompt() // 开始构建一次AI调用
                    .user(userPrompt) // 设置用户提示词（含搜索结果）
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "search_" + finalConversationId)) // 通过顾问参数指定记忆会话ID（加search_前缀）
                    .call() // 同步调用（阻塞等待完整结果）
                    .content(); // 取出回答文本内容
            int len = response != null ? response.length() : 0; // 计算回答长度（防空指针）
            log.info("搜索Agent同步调用完成: conversationId={}, responseLength={}", finalConversationId, len); // 打印完成日志
        } catch (Exception e) { // 捕获AI调用异常
            log.error("搜索Agent调用失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e); // 记录错误日志
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, e); // 包装为业务异常抛出
        }

        // 保存AI回复到数据库
        if (StringUtils.hasText(response)) { // 只有非空回答才保存
            conversationService.saveMessage(userId, finalConversationId, "assistant", response); // role="assistant"表示AI回复
        }

        return response != null ? response : ""; // 返回回答（防null，null时返回空字符串）
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
    public Flux<String> streamCallWithSearch(AgentChatRequest req, UserPrincipal user) { // 流式版本的搜索问答
        Long userId = user.getUserId(); // 取出用户ID
        log.info("搜索Agent流式调用开始: userId={}, task={}", // 打印开始日志
                userId,
                req.getTask() != null ? req.getTask().substring(0, Math.min(50, req.getTask().length())) : "null");

        String task = req.getTask(); // 取出用户问题
        if (!StringUtils.hasText(task)) { // 空问题抛异常
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        Long conversationId = req.getConversationId(); // 取出会话ID
        Long finalConversationId; // 声明最终会话ID

        // 未提供会话ID时自动创建（逻辑同同步版本）
        if (conversationId == null) {
            Conversation conversation = conversationService.createConversation(
                    userId,
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "search-agent"
            );
            finalConversationId = conversation.getId();
            req.setConversationId(finalConversationId);
        } else {
            Conversation existing = conversationService.getConversationById(userId, conversationId); // 验证归属权
            if (existing == null) {
                throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
            }
            finalConversationId = conversationId;
        }

        // 保存用户消息
        conversationService.saveMessage(userId, finalConversationId, "user", task);
        // 保存为final变量供lambda使用（Java闭包要求：lambda引用的局部变量必须final或有效final）
        final Long convId = finalConversationId; // 会话ID的final副本
        final Long uid = userId; // 用户ID的final副本

        // 创建响应累积缓冲区（流式输出是一个token一个token来的，要累积成完整回答才能存库）
        StringBuilder responseBuilder = new StringBuilder();
        // 搜索中的提示消息（先推给前端，让用户知道系统在工作）
        String searchingMsg = "🔍 正在联网搜索...\n\n";

        // 在弹性线程池上异步执行联网搜索（搜索是阻塞IO操作，不能阻塞Reactor的事件循环线程）
        Mono<String> searchMono = Mono.fromCallable(() -> { // Mono.fromCallable把同步搜索包装成异步单值容器
            try {
                return buildUserPromptWithSearch(task); // 执行搜索并返回提示词
            } catch (Exception e) {
                // 搜索失败时降级
                log.error("联网搜索失败: conversationId={}, error={}", convId, e.getMessage(), e);
                return "用户问题：" + task + "\n\n注意：联网搜索暂时不可用，请基于你的知识回答并告知用户。";
            }
        }).subscribeOn(Schedulers.boundedElastic()); // subscribeOn指定在弹性线程池执行（适合阻塞IO，不占用事件循环）

        // 先发送搜索中提示，然后等待搜索完成后流式输出LLM回答
        return Flux.just(searchingMsg) // Flux.just创建一个只含"搜索中"提示的流
                // 将搜索提示累积到缓冲区（doOnNext是副作用操作，每经过一个元素就执行一次）
                .doOnNext(responseBuilder::append) // 方法引用：等价于 s -> responseBuilder.append(s)
                // 搜索完成后拼接LLM流式输出（concatWith保证顺序：先发完前面的，再接后面的）
                .concatWith(searchMono.flatMapMany(userPrompt -> { // flatMapMany把Mono<String>展开成Flux<String>
                    // 检查搜索是否失败（降级标记）
                    boolean searchFailed = userPrompt.contains("联网搜索暂时不可用"); // 通过提示词中的降级标记判断
                    if (searchFailed) {
                        // 搜索失败时发送失败提示
                        String failMsg = "⚠️ 联网搜索失败，将基于已有知识回答...\n\n";
                        responseBuilder.append(failMsg); // 累积到缓冲区
                        return Flux.just(failMsg) // 先发失败提示
                                .concatWith(streamLlmResponse(userPrompt, finalConversationId, responseBuilder)); // 再拼接LLM流式回答
                    }
                    // 搜索成功，直接流式输出LLM回答
                    return streamLlmResponse(userPrompt, finalConversationId, responseBuilder);
                }))
                // 流完成时保存完整AI回复到数据库（doOnComplete在所有元素发完后触发）
                .doOnComplete(() -> {
                    String fullResponse = responseBuilder.toString(); // 取出累积的完整响应
                    if (StringUtils.hasText(fullResponse)) { // 非空才保存
                        // 去除搜索状态提示消息，只保存有效回答内容（提示消息不该入库）
                        String assistantContent = fullResponse
                                .replace("🔍 正在联网搜索...\n\n", "") // 去掉搜索中提示
                                .replace("⚠️ 联网搜索失败，将基于已有知识回答...\n\n", ""); // 去掉失败提示
                        conversationService.saveMessage(uid, finalConversationId, "assistant", assistantContent); // 保存纯回答
                        log.info("搜索Agent流式调用完成: conversationId={}, responseLength={}",
                                finalConversationId, assistantContent.length());
                    }
                })
                // 流异常时记录错误日志（doOnError在流出错时触发）
                .doOnError(e -> log.error("搜索Agent流式调用失败: conversationId={}, error={}", finalConversationId, e.getMessage(), e))
                // 异常处理：返回错误消息防止前端无限等待（onErrorResume把异常流替换为正常流）
                .onErrorResume(e -> {
                    String errMsg = e.getMessage() != null ? e.getMessage() : "搜索Agent调用失败"; // 取异常消息
                    log.warn("搜索Agent流异常，发送错误消息: {}", errMsg);
                    return Flux.just("[ERROR] " + errMsg); // 用错误消息替换整个流，前端能收到明确的错误提示
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
    private Flux<String> streamLlmResponse(String userPrompt, Long conversationId, StringBuilder responseBuilder) { // 私有辅助方法
        // 调用搜索Agent流式输出，使用search_前缀隔离记忆
        return searchAgentClient.prompt() // 构建一次AI调用
                .user(userPrompt) // 设置用户提示词
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "search_" + conversationId)) // 指定记忆会话ID
                .stream() // 流式调用（token一个个返回，而非等全部生成完）
                .content() // 取出每个token的文本内容，形成Flux<String>
                // 每个token同时累积到缓冲区（一边推给前端一边累积，最后存库用）
                .doOnNext(responseBuilder::append);
    }
}
