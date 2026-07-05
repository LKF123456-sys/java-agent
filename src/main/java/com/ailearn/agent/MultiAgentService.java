package com.ailearn.agent;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.dto.AgentChatRequest;
import com.ailearn.entity.Conversation;
import com.ailearn.memory.DatabaseChatMemory;
import com.ailearn.security.UserPrincipal;
import com.ailearn.service.ConversationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 多智能体协作服务类
 * 实现多Agent协作完成复杂任务的核心逻辑，包含五种角色的智能体：
 * <ul>
 *   <li><b>Planner（规划专家）</b>：分析用户需求，将复杂任务分解为清晰可执行的步骤</li>
 *   <li><b>Researcher（研究员）</b>：收集和分析信息，使用天气、计算器等工具获取真实数据</li>
 *   <li><b>Coder（编程专家）</b>：当检测到编程任务时，生成高质量、可运行的代码</li>
 *   <li><b>Critic（审查专家）</b>：审查其他Agent的输出，评估质量并提出改进建议</li>
 *   <li><b>Executor（执行专家）</b>：整合所有Agent的输出，给出最终完整答案</li>
 * </ul>
 *
 * <p>支持同步和SSE流式两种执行模式：
 * <ul>
 *   <li>同步模式：所有Agent按顺序执行完成后一次性返回完整结果</li>
 *   <li>流式模式：实时推送每个Agent的输出token，可以看到完整的协作过程</li>
 * </ul>
 *
 * <p>执行流程：Planner规划 → Researcher研究 → Coder编码（仅编程任务）→ Critic审查 → Executor整合
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
@RateLimiter(name = "agentService")
public class MultiAgentService {

    private final ChatModel chatModel;

    private final DatabaseChatMemory chatMemory;

    private final ConversationService conversationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Planner Agent系统提示词
     * 定义规划专家的角色职责：分析需求、分解任务、列出执行计划
     */
    private static final String PLANNER_SYSTEM_PROMPT = """
            你是一个任务规划专家（Planner Agent）。
            你的职责是分析用户需求，将复杂任务分解为清晰、可执行的步骤。
            请考虑任务的各个方面，列出详细的执行计划。
            请用中文回答，结构清晰，条理分明。
            """;

    /**
     * Researcher Agent系统提示词
     * 定义研究员的角色职责：收集信息、使用工具获取真实数据、提供事实支持
     */
    private static final String RESEARCHER_SYSTEM_PROMPT = """
            你是一个研究员（Researcher Agent）。
            你的职责是收集和分析信息，使用可用工具获取真实、准确的数据。
            请基于事实回答问题，提供详细的信息和数据支持。
            可用工具：天气查询、数学计算。
            请用中文回答。
            """;

    /**
     * Coder Agent系统提示词
     * 定义编程专家的角色职责：生成完整可运行代码、添加注释、遵循最佳实践
     */
    private static final String CODER_SYSTEM_PROMPT = """
            你是一个高级编程专家（Coder Agent）。
            你的职责是生成高质量、可运行的代码，解决编程问题。
            请遵循以下原则：
            1. 代码完整、可直接运行
            2. 添加必要的注释说明
            3. 遵循Java语言规范和Spring Boot最佳实践
            请用中文解释代码思路，然后给出完整代码。
            """;

    /**
     * Critic Agent系统提示词
     * 定义审查专家的角色职责：检查准确性完整性、识别问题风险、提出改进建议
     */
    private static final String CRITIC_SYSTEM_PROMPT = """
            你是一个严格的质量审查专家（Critic Agent）。
            你的职责是审查其他Agent的输出，评估其质量：
            1. 检查内容的准确性和完整性
            2. 识别潜在的问题和风险
            3. 提出具体的改进建议
            请客观公正地评价，给出建设性的反馈意见。请用中文回答。
            """;

    /**
     * Executor Agent系统提示词
     * 定义执行专家的角色职责：整合各环节输出、给出最终完整答案、不展示中间过程
     */
    private static final String EXECUTOR_SYSTEM_PROMPT = """
            你是一个最终执行专家（Executor Agent）。
            你的职责是综合规划、研究、编程、审查等各环节的输出，
            整合信息，给出用户最终需要的完整答案。
            要求答案完整、准确、有条理，语言流畅自然。
            请直接给出最终结果，不需要展示中间过程。
            """;

    /**
     * 构造方法：初始化多Agent服务
     * 注入所需的依赖组件
     *
     * @param chatClientBuilder ChatClient构建器
     * @param chatMemory        数据库聊天记忆实现
     * @param weatherTool       天气查询工具
     * @param calculatorTool    数学计算工具
     * @param conversationService 会话服务
     */
    public MultiAgentService(ChatModel chatModel,
                             DatabaseChatMemory chatMemory,
                             ConversationService conversationService) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.conversationService = conversationService;
        log.info("MultiAgentService初始化完成");
    }

    /**
     * 创建指定角色的Agent ChatClient实例
     * 根据系统提示词和是否需要工具来创建独立的Agent客户端
     *
     * @param systemPrompt 该Agent的系统提示词，定义角色职责
     * @param withTools    是否携带工具（天气、计算器）
     * @return ChatClient 配置好的Agent聊天客户端
     */
    private ChatClient createAgent(String systemPrompt) {
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 构建SSE事件字符串
     * 将事件类型、Agent名称和内容封装成标准SSE格式
     *
     * @param type    事件类型：agent_start、token、agent_end、error、done
     * @param agent   产生事件的Agent名称，可为null
     * @param content 事件内容
     * @return String 格式化后的SSE事件字符串
     */
    private String createSseEvent(String type, String agent, String content) {
        try {
            Map<String, String> event = new HashMap<>();
            event.put("type", type);
            if (agent != null) {
                event.put("agent", agent);
            }
            event.put("content", content != null ? content : "");
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("SSE事件序列化失败", e);
            return "{\"type\":\"error\",\"content\":\"事件序列化失败\"}";
        }
    }

    /**
     * 判断任务是否为编程相关任务
     * 通过关键词匹配检测用户任务是否涉及代码编写，决定是否启用Coder Agent
     *
     * @param task 用户任务描述
     * @return boolean true表示是编程任务，需要Coder Agent参与
     */
    private boolean isCodingTask(String task) {
        if (task == null) {
            return false;
        }
        String lowerTask = task.toLowerCase();
        return lowerTask.contains("代码") || lowerTask.contains("code")
                || lowerTask.contains("程序") || lowerTask.contains("编程")
                || lowerTask.contains("java") || lowerTask.contains("python")
                || lowerTask.contains("开发") || lowerTask.contains("函数");
    }

    /**
     * Planner Agent同步执行
     * 调用规划专家分析任务并生成执行计划
     *
     * @param task           用户任务描述
     * @param conversationId 会话ID，用于关联对话历史
     * @return String Planner生成的任务规划结果
     */
    public String plannerAgent(String task, String conversationId) {
        ChatClient agent = createAgent(PLANNER_SYSTEM_PROMPT);
        return agent.prompt()
                .user("请分析并规划任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "planner_" + conversationId))
                .call()
                .content();
    }

    /**
     * Researcher Agent同步执行
     * 调用研究员收集信息，可使用天气、计算器等工具获取真实数据
     *
     * @param query          研究查询内容
     * @param conversationId 会话ID
     * @return String Researcher收集的信息和分析结果
     */
    public String researcherAgent(String query, String conversationId) {
        ChatClient agent = createAgent(RESEARCHER_SYSTEM_PROMPT);
        return agent.prompt()
                .user("请研究并回答：" + query)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "researcher_" + conversationId))
                .call()
                .content();
    }

    /**
     * Coder Agent同步执行
     * 调用编程专家生成代码解决编程任务
     *
     * @param task           编程任务描述
     * @param conversationId 会话ID
     * @return String Coder生成的代码和解释
     */
    public String coderAgent(String task, String conversationId) {
        ChatClient agent = createAgent(CODER_SYSTEM_PROMPT);
        return agent.prompt()
                .user("请完成编程任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "coder_" + conversationId))
                .call()
                .content();
    }

    /**
     * Executor Agent同步执行
     * 调用执行专家整合所有Agent的输出，给出最终答案
     *
     * @param task           原始用户任务和各Agent的输出结果
     * @param conversationId 会话ID
     * @return String 最终整合后的完整答案
     */
    public String executorAgent(String task, String conversationId) {
        ChatClient agent = createAgent(EXECUTOR_SYSTEM_PROMPT);
        return agent.prompt()
                .user("基于之前的讨论，请给出最终结果：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "executor_" + conversationId))
                .call()
                .content();
    }

    /**
     * 多Agent协作同步执行（核心方法）
     * 按顺序执行Planner → Researcher → Coder（编程任务）→ Executor，
     * 拼接各Agent的输出返回完整的协作过程和结果
     *
     * @param task           用户任务描述
     * @param conversationId 会话ID字符串
     * @return String 包含各Agent输出和最终结果的完整文本
     */
    public String collaborativeExecute(String task, String conversationId) {
        StringBuilder result = new StringBuilder();
        result.append("【Planner Agent - 任务规划】\n");
        String plan = plannerAgent(task, conversationId);
        result.append(plan).append("\n\n");
        result.append("【Researcher Agent - 信息收集】\n");
        String research = researcherAgent(task, conversationId);
        result.append(research).append("\n\n");
        if (isCodingTask(task)) {
            result.append("【Coder Agent - 代码生成】\n");
            String code = coderAgent(task, conversationId);
            result.append(code).append("\n\n");
        }
        result.append("【Executor Agent - 最终结果】\n");
        String finalResult = executorAgent(task + "\n\n规划结果：" + plan + "\n\n研究结果：" + research, conversationId);
        result.append(finalResult);
        return result.toString();
    }

    /**
     * 多Agent协作同步执行（带会话管理和持久化）
     * 处理完整的业务流程：参数校验、会话创建/获取、消息保存、执行协作、结果持久化
     *
     * @param req  Agent聊天请求，包含任务描述和可选会话ID
     * @param user 当前登录用户信息
     * @return String 多Agent协作完成后的完整结果
     * @throws BusinessException 参数校验失败或协作执行失败时抛出异常
     */
    public String collaborativeExecute(AgentChatRequest req, UserPrincipal user) {
        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        Long conversationId = req.getConversationId();
        String convIdStr;
        if (conversationId == null) {
            Conversation conversation = conversationService.createConversation(
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "multi-agent"
            );
            conversationId = conversation.getId();
            convIdStr = String.valueOf(conversationId);
        } else {
            convIdStr = String.valueOf(conversationId);
            conversationService.getConversationById(conversationId);
        }
        conversationService.saveMessage(conversationId, "user", task);
        String result;
        try {
            result = collaborativeExecute(task, convIdStr);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AGENT_MULTI_COLLABORATION_FAILED, e);
        }
        if (StringUtils.hasText(result)) {
            conversationService.saveMessage(conversationId, "assistant", result);
        }
        return result;
    }

    /**
     * Planner Agent流式执行
     * 以SSE流方式实时输出Planner的思考过程和token
     *
     * @param task           用户任务描述
     * @param conversationId 会话ID
     * @return Flux<String> Planner输出的token流
     */
    private Flux<String> streamPlannerAgent(String task, String conversationId) {
        ChatClient agent = createAgent(PLANNER_SYSTEM_PROMPT);
        return agent.prompt()
                .user("请分析并规划任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "planner_" + conversationId))
                .stream()
                .content();
    }

    /**
     * Researcher Agent流式执行
     * 以SSE流方式实时输出Researcher的信息收集过程和token
     *
     * @param query          研究查询内容
     * @param conversationId 会话ID
     * @return Flux<String> Researcher输出的token流
     */
    private Flux<String> streamResearcherAgent(String query, String conversationId) {
        ChatClient agent = createAgent(RESEARCHER_SYSTEM_PROMPT);
        return agent.prompt()
                .user("请研究并回答：" + query)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "researcher_" + conversationId))
                .stream()
                .content();
    }

    /**
     * Coder Agent流式执行
     * 以SSE流方式实时输出Coder的代码生成过程和token
     *
     * @param task           编程任务描述
     * @param conversationId 会话ID
     * @return Flux<String> Coder输出的token流
     */
    private Flux<String> streamCoderAgent(String task, String conversationId) {
        ChatClient agent = createAgent(CODER_SYSTEM_PROMPT);
        return agent.prompt()
                .user("请完成编程任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "coder_" + conversationId))
                .stream()
                .content();
    }

    /**
     * Critic Agent流式执行
     * 以SSE流方式实时输出Critic的审查过程和改进建议
     *
     * @param content        待审查的内容（规划+研究+代码结果）
     * @param conversationId 会话ID
     * @return Flux<String> Critic输出的token流
     */
    private Flux<String> streamCriticAgent(String content, String conversationId) {
        ChatClient agent = createAgent(CRITIC_SYSTEM_PROMPT);
        return agent.prompt()
                .user("请审查以下内容并给出改进建议：\n" + content)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "critic_" + conversationId))
                .stream()
                .content();
    }

    /**
     * Executor Agent流式执行
     * 以SSE流方式实时输出Executor整合最终答案的过程
     *
     * @param task           包含原始任务和各Agent输出的完整上下文
     * @param conversationId 会话ID
     * @return Flux<String> Executor输出的token流
     */
    private Flux<String> streamExecutorAgent(String task, String conversationId) {
        ChatClient agent = createAgent(EXECUTOR_SYSTEM_PROMPT);
        return agent.prompt()
                .user("基于之前的讨论，请给出最终结果：\n" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "executor_" + conversationId))
                .stream()
                .content();
    }

    /**
     * 多Agent协作流式执行（核心方法）
     * 按顺序流式执行每个Agent，实时推送每个Agent的启动事件、token输出、结束事件，
     * 可以看到完整的多Agent协作过程
     *
     * @param task           用户任务描述
     * @param conversationId 会话ID字符串
     * @return Flux<String> SSE事件流，包含各Agent的实时输出
     */
    public Flux<String> streamCollaborativeExecute(String task, String conversationId) {
        StringBuilder planBuilder = new StringBuilder();
        StringBuilder researchBuilder = new StringBuilder();
        StringBuilder codeBuilder = new StringBuilder();
        StringBuilder criticBuilder = new StringBuilder();
        boolean needCoder = isCodingTask(task);

        // Planner阶段：发送启动事件 → 流式输出token → 发送结束事件
        Flux<String> plannerStream = Flux.concat(
                Flux.just(createSseEvent("agent_start", "planner", "Planner Agent 开始任务规划...")),
                streamPlannerAgent(task, conversationId)
                        .map(token -> {
                            planBuilder.append(token);
                            return createSseEvent("token", "planner", token);
                        })
                        .onErrorResume(e -> Flux.just(createSseEvent("error", "planner", e.getMessage()))),
                Flux.just(createSseEvent("agent_end", "planner", ""))
        );

        // Researcher阶段：发送启动事件 → 流式输出token → 发送结束事件
        Flux<String> researcherStream = Flux.concat(
                Flux.just(createSseEvent("agent_start", "researcher", "Researcher Agent 开始信息收集...")),
                streamResearcherAgent(task, conversationId)
                        .map(token -> {
                            researchBuilder.append(token);
                            return createSseEvent("token", "researcher", token);
                        })
                        .onErrorResume(e -> Flux.just(createSseEvent("error", "researcher", e.getMessage()))),
                Flux.just(createSseEvent("agent_end", "researcher", ""))
        );

        // Coder阶段：仅编程任务时执行，发送启动事件 → 流式输出token → 发送结束事件
        Flux<String> coderStream = needCoder
                ? Flux.concat(
                        Flux.just(createSseEvent("agent_start", "coder", "Coder Agent 开始代码生成...")),
                        streamCoderAgent(task, conversationId)
                                .map(token -> {
                                    codeBuilder.append(token);
                                    return createSseEvent("token", "coder", token);
                                })
                                .onErrorResume(e -> Flux.just(createSseEvent("error", "coder", e.getMessage()))),
                        Flux.just(createSseEvent("agent_end", "coder", ""))
                )
                : Flux.empty();

        // Critic阶段：发送启动事件 → 基于前面结果审查 → 流式输出 → 发送结束事件
        Flux<String> criticStream = Flux.concat(
                Flux.just(createSseEvent("agent_start", "critic", "Critic Agent 开始质量审查...")),
                Flux.defer(() -> {
                    String criticInput = "规划结果：" + planBuilder + "\n\n研究结果：" + researchBuilder;
                    if (needCoder) {
                        criticInput += "\n\n代码结果：" + codeBuilder;
                    }
                    return streamCriticAgent(criticInput, conversationId)
                            .map(token -> {
                                criticBuilder.append(token);
                                return createSseEvent("token", "critic", token);
                            });
                })
                        .onErrorResume(e -> Flux.just(createSseEvent("error", "critic", e.getMessage()))),
                Flux.just(createSseEvent("agent_end", "critic", ""))
        );

        // Executor阶段：发送启动事件 → 整合所有结果流式输出最终答案 → 发送结束和完成事件
        Flux<String> executorStream = Flux.defer(() -> {
            String executorInput = task + "\n\n规划结果：" + planBuilder
                    + "\n\n研究结果：" + researchBuilder;
            if (needCoder) {
                executorInput += "\n\n代码结果：" + codeBuilder;
            }
            executorInput += "\n\n审查意见：" + criticBuilder;
            return Flux.concat(
                    Flux.just(createSseEvent("agent_start", "executor", "Executor Agent 开始整合最终结果...")),
                    streamExecutorAgent(executorInput, conversationId)
                            .map(token -> createSseEvent("token", "executor", token))
                            .onErrorResume(e -> Flux.just(createSseEvent("error", "executor", e.getMessage()))),
                    Flux.just(createSseEvent("agent_end", "executor", "")),
                    Flux.just(createSseEvent("done", null, "所有Agent协作完成"))
            );
        });

        // 按顺序拼接所有Agent的流
        return Flux.concat(plannerStream, researcherStream, coderStream, criticStream, executorStream);
    }

    /**
     * 多Agent协作流式执行（带会话管理和持久化）
     * 处理完整业务流程：参数校验、会话创建/获取、消息保存、流式执行、结果持久化
     *
     * @param req  Agent聊天请求
     * @param user 当前登录用户信息
     * @return Flux<String> SSE事件流
     * @throws BusinessException 参数校验失败时抛出异常
     */
    public Flux<String> streamCollaborativeExecute(AgentChatRequest req, UserPrincipal user) {
        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        Long conversationId = req.getConversationId();
        String convIdStr;
        if (conversationId == null) {
            Conversation conversation = conversationService.createConversation(
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "multi-agent"
            );
            conversationId = conversation.getId();
            convIdStr = String.valueOf(conversationId);
        } else {
            convIdStr = String.valueOf(conversationId);
            conversationService.getConversationById(conversationId);
        }
        conversationService.saveMessage(conversationId, "user", task);
        Long finalConversationId = conversationId;
        StringBuilder fullResponseBuilder = new StringBuilder();

        return streamCollaborativeExecute(task, convIdStr)
                .doOnNext(event -> {
                    try {
                        Map<String, String> evt = objectMapper.readValue(event, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                        if ("token".equals(evt.get("type")) && evt.get("content") != null) {
                            fullResponseBuilder.append(evt.get("content"));
                        }
                    } catch (Exception ignored) {}
                })
                .doOnComplete(() -> {
                    try {
                        String fullResponse = fullResponseBuilder.toString();
                        if (StringUtils.hasText(fullResponse)) {
                            conversationService.saveMessage(finalConversationId, "assistant", fullResponse);
                        }
                    } catch (Exception e) {
                        log.warn("保存多Agent协作消息失败: {}", e.getMessage());
                    }
                })
                .doOnError(e -> log.error("多Agent流式协作失败", e))
                .onErrorResume(e -> {
                    String errMsg = e.getMessage() != null ? e.getMessage() : "多Agent协作失败";
                    log.warn("多Agent流异常，发送错误事件: {}", errMsg);
                    return Flux.just(createSseEvent("error", null, errMsg),
                            createSseEvent("done", null, "协作因错误终止"));
                });
    }
}
