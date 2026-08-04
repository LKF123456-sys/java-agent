package com.ailearn.agent;

// 导入业务异常类，用于抛出业务层面的可预期异常
import com.ailearn.common.BusinessException;
// 导入错误码枚举，定义所有业务错误码常量
import com.ailearn.common.ErrorCode;
// 导入文本分块工具类，用于将长文本拆分为小片段模拟SSE流式输出
import com.ailearn.common.StreamUtils;
// 导入Agent聊天请求DTO，包含task和conversationId字段
import com.ailearn.dto.AgentChatRequest;
// 导入会话实体类，对应数据库conversation表
import com.ailearn.entity.Conversation;
// 导入基于数据库的聊天记忆实现，用于Agent对话上下文记忆
import com.ailearn.memory.DatabaseChatMemory;
// 导入用户安全主体，包含当前登录用户的ID和角色信息
import com.ailearn.security.UserPrincipal;
// 导入会话服务，管理会话的创建、查询和消息保存
import com.ailearn.service.ConversationService;
// 导入Jackson数据绑定异常类（Jackson 3：JsonProcessingException已移除，
// 序列化异常统一抛出非受检的DatabindException，包名为tools.jackson.databind）
import tools.jackson.databind.DatabindException;
// 导入Jackson泛型类型引用，用于反序列化JSON为Map等泛型类型（Jackson 3 新包名）
import tools.jackson.core.type.TypeReference;
// 导入Jackson对象映射器，用于JSON序列化和反序列化（Jackson 3 新包名，API不变）
import tools.jackson.databind.ObjectMapper;
// 导入Resilience4j限流器注解，用于API限流保护
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
// 导入Lombok日志注解，自动生成log对象
import lombok.extern.slf4j.Slf4j;
// 导入Spring AI ChatClient，AI对话客户端核心类
import org.springframework.ai.chat.client.ChatClient;
// 导入消息记忆顾问，自动将聊天记忆注入对话上下文
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
// 导入Spring AI ChatMemory接口，定义记忆存储的conversationId参数名
import org.springframework.ai.chat.memory.ChatMemory;
// 导入Spring AI ChatModel接口，底层AI模型抽象
import org.springframework.ai.chat.model.ChatModel;
// 导入Spring AI工具回调提供者，注册所有可用工具（天气、计算、搜索等）
import org.springframework.ai.tool.ToolCallbackProvider;
// 导入Spring Service注解，标记为服务层组件
import org.springframework.stereotype.Service;
// 导入Spring字符串工具类，提供hasText等常用判断
import org.springframework.util.StringUtils;
// 导入Reactor Flux响应式流，用于SSE流式输出
import reactor.core.publisher.Flux;
// 导入Reactor Mono响应式单值容器，用于异步操作
import reactor.core.publisher.Mono;
// 导入Reactor调度器，提供弹性线程池用于阻塞操作
import reactor.core.scheduler.Schedulers;

// 导入时间Duration类，用于设置流式延迟间隔
import java.time.Duration;
// 导入ArrayList动态数组
import java.util.ArrayList;
// 导入HashMap哈希表，用于构建SSE事件数据
import java.util.HashMap;
// 导入Map接口
import java.util.Map;

/**
 * 多Agent协作服务
 * 实现Planner（规划）、Researcher（研究）、Coder（编码）、Critic（审查）、Executor（执行）
 * 五种Agent角色的协作流程，支持动态路由和Critic迭代审查机制。
 *
 * <p>核心功能：
 * <ul>
 *   <li>任务规划：Planner Agent分析任务复杂度，输出路由决策JSON</li>
 *   <li>动态路由：根据任务复杂度动态决定启用哪些Agent角色</li>
 *   <li>信息收集：Researcher Agent调用工具获取实时信息</li>
 *   <li>代码生成：Coder Agent生成高质量代码</li>
 *   <li>迭代审查：Critic Agent审查代码质量，不通过时Coder修改，最多3轮</li>
 *   <li>结果整合：Executor Agent综合所有Agent输出，给出最终答案</li>
 *   <li>SSE流式输出：所有Agent的输出均通过SSE实时推送到前端</li>
 * </ul>
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
// 使用Resilience4j限流器，限制agentService的调用频率，防止AI接口被过度调用
@RateLimiter(name = "agentService")
public class MultiAgentService {

    // AI模型实例，所有Agent共享同一个底层大模型
    private final ChatModel chatModel;

    // 数据库聊天记忆，用于各Agent保持对话上下文连贯性
    private final DatabaseChatMemory chatMemory;

    // 会话管理服务，负责会话的创建、查询和消息持久化
    private final ConversationService conversationService;

    // 工具回调提供者，注册天气查询、数学计算、联网搜索等所有可用工具
    private final ToolCallbackProvider toolCallbackProvider;

    // JSON序列化/反序列化工具，用于SSE事件JSON构建和Planner输出解析
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Critic最大迭代次数：代码审查-修改循环最多执行3轮，防止无限循环
    private static final int MAX_CRITIC_ITERATIONS = 3;

    /**
     * 任务路由决策内部类
     * 封装Planner Agent的分析结果，用于决定后续流程中启用哪些Agent
     */
    private static class TaskRoute {
        // 任务复杂度等级：simple（简单）、medium（中等）、complex（复杂）
        String complexity = "medium";
        // 是否需要Researcher Agent进行信息收集（联网搜索等）
        boolean needResearch = true;
        // 是否需要Coder Agent进行代码生成
        boolean needCoder = false;
        // Planner输出的详细规划内容（去除JSON头后的文本部分）
        String planContent = "";
    }

    // ======================== 五种Agent角色的系统提示词 ========================

    /**
     * Planner Agent系统提示词
     * 要求Planner在第一行输出JSON格式的路由决策，后续行输出详细规划
     */
    private static final String PLANNER_SYSTEM_PROMPT = """
            你是一个任务规划专家（Planner Agent）。
            你的职责是分析用户需求，评估任务复杂度，将复杂任务分解为清晰、可执行的步骤。
            
            【重要】请在回复的第一行输出JSON格式的任务评估结果（不要有任何其他前缀）：
            {"complexity":"simple|medium|complex","needResearch":true|false,"needCoder":true|false}
            
            复杂度判断标准：
            - simple：简单问候、闲聊、常识问答、概念解释，不需要实时数据或代码
            - medium：需要实时信息查询、数据分析、多步骤推理，但不需要写代码
            - complex：编程任务、复杂系统设计、需要多轮迭代优化的任务
            
            needResearch判断：是否需要联网搜索或使用工具查询实时信息
            needCoder判断：是否需要编写代码来解决问题
            
            从第二行开始，给出详细的任务规划和执行步骤。请用中文回答，结构清晰，条理分明。
            """;

    /**
     * Researcher Agent系统提示词
     * 研究员角色，负责使用工具收集和分析信息
     */
    private static final String RESEARCHER_SYSTEM_PROMPT = """
            你是一个研究员（Researcher Agent）。
            你的职责是收集和分析信息，使用可用工具获取真实、准确的数据。
            请基于事实回答问题，提供详细的信息和数据支持。
            可用工具：天气查询、数学计算、联网搜索(searchWeb)、系统信息。
            对于实时信息、最新动态、新闻、价格等，必须使用searchWeb工具联网搜索。
            请用中文回答。
            """;

    /**
     * Coder Agent系统提示词
     * 编程专家角色，负责生成高质量可运行的代码
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
     * Coder修改Agent系统提示词
     * 当Critic审查不通过时，Coder根据审查意见修改代码
     */
    private static final String CODER_REVISE_SYSTEM_PROMPT = """
            你是一个高级编程专家（Coder Agent）。
            现在你需要根据审查专家的反馈意见，对你之前生成的代码进行修改和优化。
            请仔细阅读审查意见，针对性地修复问题、改进代码质量。
            要求：
            1. 完整输出修改后的全部代码（不要只输出修改部分）
            2. 简要说明你做了哪些修改以及为什么
            3. 确保修改后的代码完整可运行
            请用中文回答。
            """;

    /**
     * Critic Agent系统提示词
     * 质量审查专家，审查其他Agent输出并给出改进建议
     */
    private static final String CRITIC_SYSTEM_PROMPT = """
            你是一个严格的质量审查专家（Critic Agent）。
            你的职责是审查其他Agent的输出，评估其质量：
            1. 检查内容的准确性和完整性
            2. 识别潜在的问题、漏洞和风险
            3. 提出具体、可操作的改进建议
            
            请在回复开头第一行明确标注审查结果：
            - 如果内容质量合格、无需修改，输出：【审查结果：通过】
            - 如果内容需要改进，输出：【审查结果：需要修改】
            
            然后给出详细的评审意见和具体修改建议。请客观公正、严格要求，用中文回答。
            """;

    /**
     * Executor Agent系统提示词
     * 最终执行专家，综合所有Agent输出给出最终答案
     */
    private static final String EXECUTOR_SYSTEM_PROMPT = """
            你是一个最终执行专家（Executor Agent）。
            你的职责是综合规划、研究、编程、审查等各环节的输出，
            整合信息，给出用户最终需要的完整答案。
            要求答案完整、准确、有条理，语言流畅自然。
            请直接给出最终结果，不需要展示中间过程。
            """;

    /**
     * 构造方法：通过Spring依赖注入初始化所有必需的组件
     *
     * @param chatModel            AI大模型客户端，底层LLM调用接口
     * @param chatMemory           数据库聊天记忆，用于多轮对话上下文保持
     * @param conversationService  会话管理服务
     * @param toolCallbackProvider 工具回调提供者，包含所有注册的AI工具
     */
    public MultiAgentService(ChatModel chatModel,
                             DatabaseChatMemory chatMemory,
                             ConversationService conversationService,
                             ToolCallbackProvider toolCallbackProvider) {
        // 保存AI模型引用
        this.chatModel = chatModel;
        // 保存聊天记忆引用
        this.chatMemory = chatMemory;
        // 保存会话服务引用
        this.conversationService = conversationService;
        // 保存工具提供者引用
        this.toolCallbackProvider = toolCallbackProvider;
        // 打印初始化完成日志
        log.info("MultiAgentService初始化完成（Spring AI 2.0），工具由ToolCallbackProvider提供");
    }

    /**
     * 创建Agent实例的工厂方法
     * 根据系统提示词和是否需要工具来构建ChatClient
     *
     * @param systemPrompt Agent的系统提示词，定义Agent的角色和行为规范
     * @param withTools    是否为Agent注册工具调用能力（如天气、搜索、计算）
     * @return 构建好的ChatClient实例
     */
    private ChatClient createAgent(String systemPrompt, boolean withTools) {
        // 使用ChatClient.builder模式构建，设置底层模型、系统提示词和记忆顾问
        var builder = ChatClient.builder(chatModel)
                // 设置Agent的系统提示词，定义其角色行为
                .defaultSystem(systemPrompt)
                // 注册消息记忆顾问，自动将历史对话注入上下文
                // Spring AI 2.0：记忆Advisor默认位于工具循环外侧，只保存最终一问一答
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        // 如果该Agent需要工具调用能力，则注册工具提供者
        if (withTools) {
            // Spring AI 2.0 迁移：defaultToolCallbacks(数组) 已废弃，
            // 改用 defaultTools() 直接接收 ToolCallbackProvider
            // 2.0 检测到工具后自动注册 ToolCallingAdvisor，无需手动添加
            builder.defaultTools(toolCallbackProvider);
        }
        // 构建并返回ChatClient实例
        return builder.build();
    }

    /**
     * 创建SSE事件JSON字符串
     * 将事件类型、Agent名称、内容封装为JSON格式供前端解析
     *
     * @param type    事件类型：agent_start/token/agent_end/info/error/done/init
     * @param agent   Agent角色名称：planner/researcher/coder/critic/executor，系统事件可为null
     * @param content 事件内容：token文本、提示消息、错误信息等
     * @return JSON格式的事件字符串
     */
    private String createSseEvent(String type, String agent, String content) {
        try {
            // 创建HashMap存储事件字段
            Map<String, String> event = new HashMap<>();
            // 设置事件类型字段
            event.put("type", type);
            // 如果agent名称不为null，则设置agent字段
            if (agent != null) {
                event.put("agent", agent);
            }
            // 设置内容字段，null时默认空字符串防止前端解析异常
            event.put("content", content != null ? content : "");
            // 序列化为JSON字符串并返回
            return objectMapper.writeValueAsString(event);
        } catch (DatabindException e) { // Jackson 3：序列化异常为非受检异常DatabindException（原JsonProcessingException已移除）
            // 序列化异常时记录错误日志，返回兜底错误JSON
            log.error("SSE事件序列化失败", e);
            return "{\"type\":\"error\",\"content\":\"事件序列化失败\"}";
        }
    }

    /**
     * 判断任务是否为编程类任务
     * 通过关键词匹配检测任务描述中是否包含编程相关词汇
     *
     * @param task 用户任务描述文本
     * @return true表示是编程任务，false表示非编程任务
     */
    private boolean isCodingTask(String task) {
        // 任务为null时直接返回false
        if (task == null) {
            return false;
        }
        // 将任务文本转为小写，实现大小写不敏感的关键词匹配
        String lowerTask = task.toLowerCase();
        // 检查是否包含中英文编程关键词：代码/code/程序/编程/java/python/开发/函数
        return lowerTask.contains("代码") || lowerTask.contains("code")
                || lowerTask.contains("程序") || lowerTask.contains("编程")
                || lowerTask.contains("java") || lowerTask.contains("python")
                || lowerTask.contains("开发") || lowerTask.contains("函数");
    }

    /**
     * 解析Planner Agent的输出，提取任务路由决策
     * Planner的第一行输出为JSON格式的路由决策，后续行为详细规划内容
     *
     * @param plannerOutput Planner Agent的完整输出文本
     * @param originalTask  用户原始任务描述，用于兜底策略判断
     * @return TaskRoute对象，包含复杂度、是否需要Research/Coder等决策信息
     */
    private TaskRoute parseTaskRoute(String plannerOutput, String originalTask) {
        // 创建默认的路由决策对象（默认medium复杂度、需要Research、不需要Coder）
        TaskRoute route = new TaskRoute();
        // 将Planner完整输出暂存为规划内容（后续可能被截取）
        route.planContent = plannerOutput != null ? plannerOutput : "";

        // 如果Planner输出为空，使用基于关键词的兜底策略
        if (!StringUtils.hasText(plannerOutput)) {
            // 根据原始任务是否包含编程关键词决定是否需要Coder
            route.needCoder = isCodingTask(originalTask);
            // 非编程任务默认需要Research收集信息
            route.needResearch = !isCodingTask(originalTask);
            return route;
        }

        try {
            // 将Planner输出按第一个换行符拆分为两行：第一行为JSON，第二行为规划内容
            String[] lines = plannerOutput.split("\n", 2);
            // 获取第一行并去除前后空格
            String firstLine = lines[0].trim();

            // 判断第一行是否为JSON格式的路由决策（以{开头且包含complexity关键字）
            if (firstLine.startsWith("{") && firstLine.contains("complexity")) {
                // 使用Jackson将JSON字符串反序列化为Map
                Map<String, Object> jsonMap = objectMapper.readValue(firstLine, new TypeReference<Map<String, Object>>() {});
                // 提取complexity字段（任务复杂度：simple/medium/complex）
                if (jsonMap.get("complexity") != null) {
                    route.complexity = jsonMap.get("complexity").toString();
                }
                // 提取needResearch字段（是否需要信息收集）
                if (jsonMap.get("needResearch") != null) {
                    route.needResearch = Boolean.parseBoolean(jsonMap.get("needResearch").toString());
                }
                // 提取needCoder字段（是否需要代码生成）
                if (jsonMap.get("needCoder") != null) {
                    route.needCoder = Boolean.parseBoolean(jsonMap.get("needCoder").toString());
                }
                // 如果有第二行，将其作为规划内容
                if (lines.length > 1) {
                    route.planContent = lines[1].trim();
                } else {
                    // 仅有JSON行，无规划内容
                    route.planContent = "";
                }
            } else {
                // 第一行不是JSON格式，使用关键词匹配兜底策略
                route.needCoder = isCodingTask(originalTask);
                // 中等或复杂任务默认需要Research
                route.needResearch = "medium".equals(route.complexity) || "complex".equals(route.complexity);
            }
        } catch (Exception e) {
            // JSON解析或其他异常时，记录警告日志并使用兜底策略
            log.warn("解析Planner路由决策失败，使用兜底策略: {}", e.getMessage());
            // 根据编程关键词判断是否需要Coder
            route.needCoder = isCodingTask(originalTask);
            // 非编程任务且原始任务长度大于10时，认为需要Research
            route.needResearch = !isCodingTask(originalTask) && originalTask != null && originalTask.length() > 10;
        }

        // 最终强制修正：如果原始任务本身是编程任务，强制启用Coder并设为complex复杂度
        if (isCodingTask(originalTask)) {
            route.needCoder = true;
            route.complexity = "complex";
        }

        // 记录最终的路由决策结果到日志
        log.info("任务路由决策: complexity={}, needResearch={}, needCoder={}",
                route.complexity, route.needResearch, route.needCoder);
        return route;
    }

    // ======================== 五种Agent的同步调用方法 ========================

    /**
     * Planner Agent同步调用：分析任务并输出规划方案
     *
     * @param task           用户任务描述
     * @param conversationId 会话ID，用于记忆隔离
     * @return Planner的规划输出文本
     */
    public String plannerAgent(String task, String conversationId) {
        // 创建Planner Agent，不启用工具（Planner只做规划不调用工具）
        ChatClient agent = createAgent(PLANNER_SYSTEM_PROMPT, false);
        // 发送任务规划请求，设置独立的记忆会话ID（前缀planner_避免与其他Agent记忆冲突）
        String result = agent.prompt()
                .user("请分析并规划任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "planner_" + conversationId))
                .call()
                .content();
        // 返回结果，null时返回空字符串
        return result != null ? result : "";
    }

    /**
     * Researcher Agent同步调用：收集信息并回答问题
     *
     * @param query          需要研究的问题
     * @param conversationId 会话ID
     * @return Researcher的研究结果文本
     */
    public String researcherAgent(String query, String conversationId) {
        // 创建Researcher Agent，启用工具调用（天气、搜索、计算等）
        ChatClient agent = createAgent(RESEARCHER_SYSTEM_PROMPT, true);
        // 发送研究请求，使用researcher_前缀隔离记忆
        String result = agent.prompt()
                .user("请研究并回答：" + query)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "researcher_" + conversationId))
                .call()
                .content();
        return result != null ? result : "";
    }

    /**
     * Coder Agent同步调用：完成编程任务
     *
     * @param task           编程任务描述
     * @param conversationId 会话ID
     * @return Coder生成的代码和解释文本
     */
    public String coderAgent(String task, String conversationId) {
        // 创建Coder Agent，不启用工具（Coder专注于代码生成）
        ChatClient agent = createAgent(CODER_SYSTEM_PROMPT, false);
        // 发送编程请求，使用coder_前缀隔离记忆
        String result = agent.prompt()
                .user("请完成编程任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "coder_" + conversationId))
                .call()
                .content();
        return result != null ? result : "";
    }

    /**
     * Executor Agent同步调用：整合所有Agent输出，给出最终答案
     *
     * @param task           包含各Agent输出的综合任务描述
     * @param conversationId 会话ID
     * @return Executor整合后的最终答案
     */
    public String executorAgent(String task, String conversationId) {
        // 创建Executor Agent，不启用工具（Executor只做信息整合和最终输出）
        ChatClient agent = createAgent(EXECUTOR_SYSTEM_PROMPT, false);
        // 发送整合请求，使用executor_前缀隔离记忆
        String result = agent.prompt()
                .user("基于之前的讨论，请给出最终结果：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "executor_" + conversationId))
                .call()
                .content();
        return result != null ? result : "";
    }

    // ======================== 同步协作执行方法 ========================

    /**
     * 多Agent同步协作执行（纯文本版）
     * 按顺序执行Planner→Researcher→Coder(可选)→Executor，拼接所有输出
     *
     * @param task           用户任务描述
     * @param conversationId 会话ID字符串
     * @return 包含所有Agent输出的完整协作结果
     */
    public String collaborativeExecute(String task, String conversationId) {
        // 任务为空时返回空字符串
        if (!StringUtils.hasText(task)) {
            return "";
        }
        // 使用StringBuilder拼接各Agent的输出结果
        StringBuilder result = new StringBuilder();
        // 第一步：调用Planner Agent进行任务规划
        result.append("【Planner Agent - 任务规划】\n");
        String plan = plannerAgent(task, conversationId);
        result.append(plan).append("\n\n");
        // 第二步：调用Researcher Agent进行信息收集
        result.append("【Researcher Agent - 信息收集】\n");
        String research = researcherAgent(task, conversationId);
        result.append(research).append("\n\n");
        // 第三步：如果是编程任务，调用Coder Agent生成代码
        if (isCodingTask(task)) {
            result.append("【Coder Agent - 代码生成】\n");
            String code = coderAgent(task, conversationId);
            result.append(code).append("\n\n");
        }
        // 第四步：调用Executor Agent整合所有结果，生成最终答案
        result.append("【Executor Agent - 最终结果】\n");
        String finalResult = executorAgent(task + "\n\n规划结果：" + plan + "\n\n研究结果：" + research, conversationId);
        result.append(finalResult);
        // 返回完整的协作结果
        return result.toString();
    }

    /**
     * 多Agent同步协作执行（带会话管理版）
     * 处理会话创建/验证、消息保存等业务逻辑，委托核心方法执行协作
     *
     * @param req  Agent聊天请求，包含task和可选conversationId
     * @param user 当前登录用户主体
     * @return 协作执行的完整结果文本
     * @throws BusinessException 任务为空、会话不存在或执行失败时抛出
     */
    public String collaborativeExecute(AgentChatRequest req, UserPrincipal user) {
        // 获取当前用户ID
        Long userId = user.getUserId();
        // 获取用户任务描述
        String task = req.getTask();
        // 校验任务内容不能为空
        if (!StringUtils.hasText(task)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        // 获取请求中的会话ID（可能为null）
        Long conversationId = req.getConversationId();
        String convIdStr;
        // 如果未提供会话ID，自动创建新会话
        if (conversationId == null) {
            // 创建新会话，标题取任务前50字符，类型为multi-agent
            Conversation conversation = conversationService.createConversation(
                    userId,
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "multi-agent"
            );
            // 获取新创建的会话ID
            conversationId = conversation.getId();
            convIdStr = String.valueOf(conversationId);
            // 将新会话ID回填到请求对象
            req.setConversationId(conversationId);
        } else {
            // 使用已有会话ID
            convIdStr = String.valueOf(conversationId);
            // 验证会话归属权：确保该会话属于当前用户
            Conversation existing = conversationService.getConversationById(userId, conversationId);
            if (existing == null) {
                throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
            }
        }
        // 保存用户发送的任务消息到数据库
        conversationService.saveMessage(userId, conversationId, "user", task);
        String result;
        try {
            // 调用核心协作方法执行多Agent协作
            result = collaborativeExecute(task, convIdStr);
        } catch (Exception e) {
            // 协作执行异常时记录错误日志并抛出业务异常
            log.error("多Agent协作执行失败", e);
            throw new BusinessException(ErrorCode.AGENT_MULTI_COLLABORATION_FAILED, e);
        }
        // 如果协作结果非空，保存AI回复消息到数据库
        if (StringUtils.hasText(result)) {
            conversationService.saveMessage(userId, conversationId, "assistant", result);
        }
        // 返回协作结果
        return result;
    }

    // ======================== 各Agent的流式调用方法 ========================

    /**
     * Planner Agent流式调用：实时输出规划过程
     *
     * @param task           用户任务描述
     * @param conversationId 会话ID
     * @return Flux流式token序列
     */
    private Flux<String> streamPlannerAgent(String task, String conversationId) {
        // 创建Planner Agent（不启用工具）
        ChatClient agent = createAgent(PLANNER_SYSTEM_PROMPT, false);
        // 使用stream()方法发起流式请求，实时输出token
        return agent.prompt()
                .user("请分析并规划任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "planner_" + conversationId))
                .stream()
                .content();
    }

    /**
     * Researcher Agent流式调用
     * 由于工具调用不支持原生stream，使用同步调用+分块模拟流式输出
     *
     * @param query          需要研究的问题
     * @param conversationId 会话ID
     * @return Flux流式token序列（模拟）
     */
    private Flux<String> streamResearcherAgent(String query, String conversationId) {
        // 创建Researcher Agent（启用工具）
        ChatClient agent = createAgent(RESEARCHER_SYSTEM_PROMPT, true);
        // 使用Mono封装同步调用，在弹性线程池上异步执行（因为工具调用是阻塞的）
        Mono<String> researchMono = Mono.fromCallable(() -> {
            // 执行同步调用获取完整响应
            String result = agent.prompt()
                    .user("请研究并回答：" + query)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "researcher_" + conversationId))
                    .call()
                    .content();
            return result != null ? result : "";
        }).subscribeOn(Schedulers.boundedElastic()); // 在弹性线程池执行，避免阻塞事件循环

        // 将完整响应拆分为小片段，以20ms间隔模拟流式输出
        return researchMono.flatMapMany(fullResponse -> {
            if (!StringUtils.hasText(fullResponse)) {
                return Flux.just("");
            }
            // 使用StreamUtils将文本拆分为小chunk，每个chunk间隔20ms发送
            return Flux.fromArray(StreamUtils.splitIntoChunks(fullResponse))
                    .delayElements(Duration.ofMillis(20));
        });
    }

    /**
     * Coder Agent流式调用：实时输出代码生成过程
     *
     * @param task           编程任务描述
     * @param conversationId 会话ID
     * @return Flux流式token序列
     */
    private Flux<String> streamCoderAgent(String task, String conversationId) {
        // 创建Coder Agent（不启用工具，专注于代码生成）
        ChatClient agent = createAgent(CODER_SYSTEM_PROMPT, false);
        // 流式输出代码生成过程
        return agent.prompt()
                .user("请完成编程任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "coder_" + conversationId))
                .stream()
                .content();
    }

    /**
     * Coder修改Agent流式调用：根据Critic审查意见修改代码
     *
     * @param originalCode   Coder之前生成的原始代码
     * @param feedback       Critic的审查反馈意见
     * @param conversationId 会话ID
     * @param round          当前修改轮次（从1开始）
     * @return Flux流式token序列
     */
    private Flux<String> streamCoderReviseAgent(String originalCode, String feedback, String conversationId, int round) {
        // 创建代码修改Agent，使用专用的修改提示词
        ChatClient agent = createAgent(CODER_REVISE_SYSTEM_PROMPT, false);
        // 构建包含轮次、原始代码、审查意见的结构化用户提示
        String userPrompt = String.format("""
                这是第%d轮修改。
                
                原始任务要求和你之前生成的代码：
                %s
                
                审查专家的反馈意见：
                %s
                
                请根据以上反馈修改代码，输出完整的修改后代码和修改说明。
                """, round, originalCode, feedback);
        // 流式输出修改后的代码
        return agent.prompt()
                .user(userPrompt)
                // 使用轮次信息隔离每轮修改的记忆上下文
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "coder_revise_" + round + "_" + conversationId))
                .stream()
                .content();
    }

    /**
     * Critic Agent流式调用：实时输出审查过程
     *
     * @param content        待审查的内容（如Coder生成的代码）
     * @param conversationId 会话ID
     * @return Flux流式token序列
     */
    private Flux<String> streamCriticAgent(String content, String conversationId) {
        // 创建Critic Agent（不启用工具，专注于质量审查）
        ChatClient agent = createAgent(CRITIC_SYSTEM_PROMPT, false);
        // 流式输出审查意见
        return agent.prompt()
                .user("请审查以下内容并给出改进建议：\n" + content)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "critic_" + conversationId))
                .stream()
                .content();
    }

    /**
     * Executor Agent流式调用：实时输出最终整合结果
     *
     * @param task           包含所有Agent输出的综合信息
     * @param conversationId 会话ID
     * @return Flux流式token序列
     */
    private Flux<String> streamExecutorAgent(String task, String conversationId) {
        // 创建Executor Agent（不启用工具，专注于结果整合）
        ChatClient agent = createAgent(EXECUTOR_SYSTEM_PROMPT, false);
        // 流式输出最终答案
        return agent.prompt()
                .user("基于之前的讨论，请给出最终结果：\n" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "executor_" + conversationId))
                .stream()
                .content();
    }

    // ======================== SSE流式协作执行核心方法 ========================

    /**
     * 多Agent流式协作执行核心方法
     * 将Planner→动态路由→Researcher(可选)→Coder(可选)→Critic迭代(可选)→Executor的
     * 完整流程以SSE事件流的形式实时推送到前端
     *
     * @param task           用户任务描述
     * @param conversationId 会话ID字符串
     * @return Flux&lt;String&gt; SSE事件流，每个事件为JSON格式
     */
    public Flux<String> streamCollaborativeExecute(String task, String conversationId) { // SSE流式协作核心方法（无会话管理的纯流程版）
        if (!StringUtils.hasText(task)) { // 任务为空校验
            return Flux.just(createSseEvent("error", null, "任务内容不能为空")); // 返回只含错误事件的流
        }
        // 各Agent输出的累积缓冲区（流式token要累积成完整文本，供后续Agent引用和Executor整合）
        StringBuilder planBuilder = new StringBuilder(); // Planner输出累积
        StringBuilder researchBuilder = new StringBuilder(); // Researcher输出累积
        StringBuilder codeBuilder = new StringBuilder(); // Coder输出累积
        StringBuilder criticBuilder = new StringBuilder(); // Critic输出累积
        // 使用数组持有TaskRoute引用（lambda中不能修改局部变量，用数组绕过Java闭包限制）
        final TaskRoute[] routeHolder = new TaskRoute[1]; // 数组第一个元素存路由决策，供Flux.defer中使用

        // 阶段1：Planner Agent流式输出（Flux.concat按顺序拼接：开始事件→token流→结束事件）
        Flux<String> plannerStream = Flux.concat(
                Flux.just(createSseEvent("agent_start", "planner", "Planner Agent 开始任务规划...")), // 先推"开始"事件，前端显示Planner工作状态
                streamPlannerAgent(task, conversationId) // 调用Planner流式方法，得到token流
                        .map(token -> { // map：每个token到达时执行
                            planBuilder.append(token != null ? token : ""); // 累积到缓冲区（防空指针）
                            return createSseEvent("token", "planner", token); // 包装成token事件推给前端
                        })
                        .onErrorResume(e -> Flux.just(createSseEvent("error", "planner", e.getMessage()))), // Planner出错时降级为错误事件，不中断整个流程
                Flux.just(createSseEvent("agent_end", "planner", "")) // 最后推"结束"事件，前端关闭Planner状态
        );

        // 阶段2：根据路由决策动态执行后续Agent（Flux.defer延迟执行：等阶段1的planBuilder填满后才解析路由）
        Flux<String> dynamicRouteStream = Flux.defer(() -> { // defer关键！订阅时才执行，保证读到的是最新缓冲区内容
            TaskRoute route = parseTaskRoute(planBuilder.toString(), task); // 解析Planner输出，得到路由决策
            routeHolder[0] = route; // 存入数组，供阶段3的Executor使用
            if ("simple".equals(route.complexity) && !route.needResearch && !route.needCoder) { // 简单任务短路
                return Flux.just(createSseEvent("info", null, "简单任务，跳过Researcher和Coder，直接生成回答...")); // 只推提示事件，跳过中间Agent
            }
            Flux<String> stream = Flux.empty(); // 初始化空流，后续按需拼接
            if (route.needResearch) { // 需要信息收集时
                Flux<String> researcherStream = Flux.concat(
                        Flux.just(createSseEvent("agent_start", "researcher", "Researcher Agent 开始信息收集...")), // 开始事件
                        streamResearcherAgent(task, conversationId) // Researcher流式调用（内部是同步+模拟流式）
                                .map(token -> { // 每个token
                                    researchBuilder.append(token != null ? token : ""); // 累积
                                    return createSseEvent("token", "researcher", token); // 推token事件
                                })
                                .onErrorResume(e -> Flux.just(createSseEvent("error", "researcher", e.getMessage()))), // 出错降级
                        Flux.just(createSseEvent("agent_end", "researcher", "")) // 结束事件
                );
                stream = Flux.concat(stream, researcherStream); // 拼接到主流
            } else { // 不需要信息收集
                stream = Flux.concat(stream, Flux.just(createSseEvent("info", null, "无需信息收集，跳过Researcher..."))); // 推跳过提示
            }
            if (route.needCoder) { // 需要代码生成时
                Flux<String> coderStream = Flux.concat(
                        Flux.just(createSseEvent("agent_start", "coder", "Coder Agent 开始代码生成...")), // 开始事件
                        streamCoderAgent(task, conversationId) // Coder流式调用
                                .map(token -> { // 每个token
                                    codeBuilder.append(token != null ? token : ""); // 累积代码
                                    return createSseEvent("token", "coder", token); // 推token事件
                                })
                                .onErrorResume(e -> Flux.just(createSseEvent("error", "coder", e.getMessage()))), // 出错降级
                        Flux.just(createSseEvent("agent_end", "coder", "")) // 结束事件
                );
                Flux<String> criticStream = runCriticIteration(planBuilder, researchBuilder, codeBuilder, criticBuilder, conversationId, 1); // Coder完成后启动Critic迭代审查（第1轮）
                stream = Flux.concat(stream, coderStream, criticStream); // 拼接：先代码生成，再迭代审查
            } else if ("complex".equals(route.complexity)) { // 不写代码但任务复杂：只对规划+研究结果做一次质量审查
                Flux<String> criticOnlyStream = Flux.defer(() -> { // defer延迟到订阅时执行
                    criticBuilder.setLength(0); // 清空Critic缓冲区（复用对象前先清零）
                    String criticInput = "规划结果：" + route.planContent + "\n\n研究结果：" + researchBuilder; // 拼装审查输入
                    return Flux.concat(
                            Flux.just(createSseEvent("agent_start", "critic", "Critic Agent 开始质量审查...")), // 开始事件
                            streamCriticAgent(criticInput, conversationId + "_review") // Critic流式审查（会话ID加后缀隔离记忆）
                                    .map(token -> { // 每个token
                                        criticBuilder.append(token != null ? token : ""); // 累积审查意见
                                        return createSseEvent("token", "critic", token); // 推token事件
                                    })
                                    .onErrorResume(e -> Flux.just(createSseEvent("error", "critic", e.getMessage()))), // 出错降级
                            Flux.just(createSseEvent("agent_end", "critic", "")), // 结束事件
                            Flux.just(createSseEvent("info", null, "审查完成，进入最终整合阶段...")) // 推进入整合阶段的提示
                    );
                });
                stream = Flux.concat(stream, criticOnlyStream); // 拼接到主流
            }
            return stream; // 返回动态路由阶段的事件流
        });

        // 阶段3：Executor Agent整合所有结果（同样用defer确保读到最新的缓冲区内容）
        Flux<String> executorStream = Flux.defer(() -> {
            TaskRoute route = routeHolder[0]; // 取出路由决策
            String planContent = (route != null ? route.planContent : planBuilder.toString()); // 优先用解析出的规划内容，否则用原始Planner输出
            String executorInput = task + "\n\n规划结果：" + (planContent != null ? planContent : "") // 拼装Executor输入：原始任务+规划
                    + "\n\n研究结果：" + researchBuilder; // +研究结果
            if (route != null && route.needCoder) { // 如果做过代码生成
                executorInput += "\n\n最终代码结果：" + codeBuilder; // +最终代码（含迭代修改后的版本）
            }
            if (criticBuilder.length() > 0) { // 如果有审查意见
                executorInput += "\n\n审查意见：" + criticBuilder; // +审查意见
            }
            return Flux.concat(
                    Flux.just(createSseEvent("agent_start", "executor", "Executor Agent 开始整合最终结果...")), // 开始事件
                    streamExecutorAgent(executorInput, conversationId) // Executor流式整合
                            .map(token -> createSseEvent("token", "executor", token)) // 包装token事件（Executor输出即最终答案，无需累积）
                            .onErrorResume(e -> Flux.just(createSseEvent("error", "executor", e.getMessage()))), // 出错降级
                    Flux.just(createSseEvent("agent_end", "executor", "")), // 结束事件
                    Flux.just(createSseEvent("done", null, "所有Agent协作完成")) // 全流程done事件，前端收到后关闭连接
            );
        });

        return Flux.concat(plannerStream, dynamicRouteStream, executorStream); // 三阶段按顺序拼接成完整SSE事件流
    }

    /**
     * Critic迭代审查方法（递归实现）
     * Critic审查代码→不通过则Coder修改→再审查→最多MAX_CRITIC_ITERATIONS轮
     *
     * @param planBuilder     Planner输出缓冲区
     * @param researchBuilder Researcher输出缓冲区
     * @param codeBuilder     Coder代码缓冲区（会被修改后的代码替换）
     * @param criticBuilder   Critic审查意见缓冲区（每轮清空重写）
     * @param conversationId  会话ID
     * @param iteration       当前迭代轮次（从1开始）
     * @return Flux&lt;String&gt; 包含审查和可能的修改过程的SSE事件流
     */
    private Flux<String> runCriticIteration(StringBuilder planBuilder, // 递归实现Critic迭代审查
                                            StringBuilder researchBuilder,
                                            StringBuilder codeBuilder,
                                            StringBuilder criticBuilder,
                                            String conversationId,
                                            int iteration) {
        final String finalPrevCriticOutput = criticBuilder.toString(); // 保存上一轮审查意见（final才能被lambda引用）
        criticBuilder.setLength(0); // 清空缓冲区，准备接收本轮审查意见

        String criticInput = "规划结果：" + planBuilder + "\n\n研究结果：" + researchBuilder // 拼装审查输入：规划+研究
                + "\n\n" + (iteration == 1 ? "代码结果" : "修改后的代码") + "：" + codeBuilder; // 第1轮叫"代码结果"，后续轮叫"修改后的代码"
        if (iteration > 1) { // 第2轮起，附上上一轮审查意见（让Critic知道之前提了什么问题、是否已修复）
            criticInput += "\n\n上一轮审查意见：" + finalPrevCriticOutput;
        }

        String agentName = iteration == 1 ? "critic" : "critic_review" + iteration; // 第1轮叫critic，后续轮加轮次后缀（前端区分显示）
        String startMsg = iteration == 1 // 开始消息也按轮次区分
                ? "Critic Agent 开始质量审查..."
                : "Critic Agent 第" + iteration + "轮审查...";

        Flux<String> reviewStream = Flux.concat( // 拼接本轮审查的事件流
                Flux.just(createSseEvent("agent_start", agentName, startMsg)), // 开始事件
                streamCriticAgent(criticInput, conversationId + "_iter" + iteration) // Critic流式审查（会话ID加轮次后缀隔离记忆）
                        .map(token -> { // 每个token
                            criticBuilder.append(token != null ? token : ""); // 累积审查意见
                            return createSseEvent("token", agentName, token); // 推token事件
                        })
                        .onErrorResume(e -> Flux.just(createSseEvent("error", agentName, e.getMessage()))), // 出错降级
                Flux.just(createSseEvent("agent_end", agentName, "")) // 结束事件
        );

        return reviewStream.thenMany(Flux.defer(() -> { // thenMany：等审查流全部发完后，再决定下一步（defer延迟判断）
            String criticOutput = criticBuilder.toString(); // 取出本轮完整审查意见
            boolean passed = criticOutput.contains("【审查结果：通过】") // 判断是否通过：匹配两种格式（带括号和不带括号，提高容错）
                    || criticOutput.contains("审查结果：通过");
            if (passed || iteration >= MAX_CRITIC_ITERATIONS) { // 终止条件：通过 或 达到最大迭代次数（防无限循环）
                String infoMsg = passed // 根据不同终止原因生成不同提示
                        ? "审查通过，进入最终整合阶段..." // 审查通过
                        : "已达最大迭代次数(" + MAX_CRITIC_ITERATIONS + "轮)，进入最终整合阶段..."; // 达到上限
                return Flux.just(createSseEvent("info", null, infoMsg)); // 推提示事件，迭代结束
            }
            String originalCode = codeBuilder.toString(); // 未通过：保存当前代码作为"原始代码"供修改
            codeBuilder.setLength(0); // 清空代码缓冲区，准备接收修改后的代码
            String reviseAgentName = "coder_revise" + iteration; // 修改Agent名称带轮次（前端区分）
            Flux<String> reviseStream = Flux.concat( // 拼接代码修改的事件流
                    Flux.just(createSseEvent("info", null, "审查未通过，开始第" + (iteration + 1) + "轮修改...")), // 提示即将修改
                    Flux.just(createSseEvent("agent_start", reviseAgentName, // 修改开始事件
                            "Coder Agent 根据审查意见修改代码（第" + iteration + "轮）...")),
                    streamCoderReviseAgent(originalCode, criticOutput, conversationId, iteration) // 调用修改Agent：传入原代码+审查意见
                            .map(token -> { // 每个token
                                codeBuilder.append(token != null ? token : ""); // 累积修改后的代码
                                return createSseEvent("token", reviseAgentName, token); // 推token事件
                            })
                            .onErrorResume(e -> Flux.just(createSseEvent("error", reviseAgentName, e.getMessage()))), // 出错降级
                    Flux.just(createSseEvent("agent_end", reviseAgentName, "")) // 修改结束事件
            );
            return Flux.concat( // 拼接：修改流 + 下一轮审查（递归！）
                    reviseStream,
                    runCriticIteration(planBuilder, researchBuilder, codeBuilder, criticBuilder, conversationId, iteration + 1) // 递归调用自己，轮次+1
            );
        }));
    }

    // ======================== SSE流式协作执行（带会话管理） ========================

    /**
     * 多Agent流式协作执行（带会话管理的入口方法）
     * 处理会话创建/验证、消息保存，然后将核心流式协作结果以SSE推送
     *
     * @param req  Agent聊天请求
     * @param user 当前登录用户
     * @return Flux&lt;String&gt; SSE事件流
     * @throws BusinessException 任务为空或会话不存在时抛出
     */
    public Flux<String> streamCollaborativeExecute(AgentChatRequest req, UserPrincipal user) { // 带会话管理的SSE入口（Controller调用这个）
        Long userId = user.getUserId(); // 取出用户ID
        String task = req.getTask(); // 取出任务描述
        if (!StringUtils.hasText(task)) { // 任务为空抛业务异常
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        Long conversationId = req.getConversationId(); // 取出会话ID（可能为null）
        String convIdStr; // 声明字符串形式的会话ID（记忆隔离用）
        if (conversationId == null) { // 情况1：未传会话ID，自动创建
            Conversation conversation = conversationService.createConversation( // 创建multi-agent类型的新会话
                    userId,
                    task.length() > 50 ? task.substring(0, 50) + "..." : task, // 标题取任务前50字符
                    "multi-agent"
            );
            conversationId = conversation.getId(); // 拿到新会话ID
            convIdStr = String.valueOf(conversationId); // 转字符串
            req.setConversationId(conversationId); // 回填到请求对象
        } else { // 情况2：传了会话ID，验证归属权
            convIdStr = String.valueOf(conversationId); // 转字符串
            Conversation existing = conversationService.getConversationById(userId, conversationId); // 按用户+会话ID查询
            if (existing == null) { // 不存在或不属于当前用户
                throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
            }
        }
        conversationService.saveMessage(userId, conversationId, "user", task); // 保存用户消息到数据库
        Long finalConversationId = conversationId; // 有效final变量供lambda使用
        final Long uid = userId; // 用户ID的final副本
        StringBuilder fullResponseBuilder = new StringBuilder(); // 完整响应累积缓冲区（存库用）
        String initEvent = createSseEvent("init", null, String.valueOf(finalConversationId)); // init事件：把会话ID第一时间推给前端（前端后续对话要用）

        return Flux.just(initEvent) // 先发init事件
                .concatWith(streamCollaborativeExecute(task, convIdStr) // 再拼接核心协作流
                        .doOnNext(event -> { // 每个事件经过时：解析JSON提取token累积完整响应
                            try {
                                Map<String, String> evt = objectMapper.readValue(event, new TypeReference<Map<String, String>>() {}); // 把事件JSON反序列化为Map
                                String type = evt.get("type"); // 取事件类型
                                if ("token".equals(type) && evt.get("content") != null) { // 只累积token事件的内容
                                    fullResponseBuilder.append(evt.get("content")); // 追加到缓冲区
                                }
                            } catch (Exception e) { // 解析失败（不该发生，防御性处理）
                                log.warn("解析SSE事件失败: {}", e.getMessage()); // 只记警告，不中断流
                            }
                        })
                        .doOnComplete(() -> { // 流全部完成时：把累积的完整响应存库
                            try {
                                String fullResponse = fullResponseBuilder.toString(); // 取出完整响应
                                if (StringUtils.hasText(fullResponse)) { // 非空才保存
                                    conversationService.saveMessage(uid, finalConversationId, "assistant", fullResponse); // role="assistant"存AI回复
                                }
                            } catch (Exception e) { // 保存失败（如数据库连接断了）
                                log.warn("保存多Agent协作消息失败: {}", e.getMessage()); // 只记警告（响应已推给前端，存库失败不影响用户体验）
                            }
                        })
                        .doOnError(e -> log.error("多Agent流式协作失败", e)) // 流出错时记录错误日志
                        .onErrorResume(e -> { // 异常兜底：推错误事件+done事件，让前端正常收尾
                            String errMsg = e.getMessage() != null ? e.getMessage() : "多Agent协作失败"; // 取异常消息
                            log.warn("多Agent流异常，发送错误事件: {}", errMsg);
                            return Flux.just(createSseEvent("error", null, errMsg), // 错误事件
                                    createSseEvent("done", null, "协作因错误终止")); // done事件（前端收到后关闭连接）
                        }));
    }
}