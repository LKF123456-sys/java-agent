package com.ailearn.agent;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.common.StreamUtils;
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
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RateLimiter(name = "agentService")
public class MultiAgentService {

    private final ChatModel chatModel;

    private final DatabaseChatMemory chatMemory;

    private final ConversationService conversationService;

    private final ToolCallbackProvider toolCallbackProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_CRITIC_ITERATIONS = 3;

    private static class TaskRoute {
        String complexity = "medium";
        boolean needResearch = true;
        boolean needCoder = false;
        String planContent = "";
    }

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

    private static final String RESEARCHER_SYSTEM_PROMPT = """
            你是一个研究员（Researcher Agent）。
            你的职责是收集和分析信息，使用可用工具获取真实、准确的数据。
            请基于事实回答问题，提供详细的信息和数据支持。
            可用工具：天气查询、数学计算、联网搜索(searchWeb)、系统信息。
            对于实时信息、最新动态、新闻、价格等，必须使用searchWeb工具联网搜索。
            请用中文回答。
            """;

    private static final String CODER_SYSTEM_PROMPT = """
            你是一个高级编程专家（Coder Agent）。
            你的职责是生成高质量、可运行的代码，解决编程问题。
            请遵循以下原则：
            1. 代码完整、可直接运行
            2. 添加必要的注释说明
            3. 遵循Java语言规范和Spring Boot最佳实践
            请用中文解释代码思路，然后给出完整代码。
            """;

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

    private static final String EXECUTOR_SYSTEM_PROMPT = """
            你是一个最终执行专家（Executor Agent）。
            你的职责是综合规划、研究、编程、审查等各环节的输出，
            整合信息，给出用户最终需要的完整答案。
            要求答案完整、准确、有条理，语言流畅自然。
            请直接给出最终结果，不需要展示中间过程。
            """;

    public MultiAgentService(ChatModel chatModel,
                             DatabaseChatMemory chatMemory,
                             ConversationService conversationService,
                             ToolCallbackProvider toolCallbackProvider) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.conversationService = conversationService;
        this.toolCallbackProvider = toolCallbackProvider;
        var callbacks = toolCallbackProvider.getToolCallbacks();
        log.info("MultiAgentService初始化完成，工具数量: {}", callbacks.length);
    }

    private ChatClient createAgent(String systemPrompt, boolean withTools) {
        var builder = ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        if (withTools) {
            builder.defaultToolCallbacks(toolCallbackProvider.getToolCallbacks());
        }
        return builder.build();
    }

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

    private TaskRoute parseTaskRoute(String plannerOutput, String originalTask) {
        TaskRoute route = new TaskRoute();
        route.planContent = plannerOutput != null ? plannerOutput : "";

        if (!StringUtils.hasText(plannerOutput)) {
            route.needCoder = isCodingTask(originalTask);
            route.needResearch = !isCodingTask(originalTask);
            return route;
        }

        try {
            String[] lines = plannerOutput.split("\n", 2);
            String firstLine = lines[0].trim();

            if (firstLine.startsWith("{") && firstLine.contains("complexity")) {
                Map<String, Object> jsonMap = objectMapper.readValue(firstLine, new TypeReference<Map<String, Object>>() {});
                if (jsonMap.get("complexity") != null) {
                    route.complexity = jsonMap.get("complexity").toString();
                }
                if (jsonMap.get("needResearch") != null) {
                    route.needResearch = Boolean.parseBoolean(jsonMap.get("needResearch").toString());
                }
                if (jsonMap.get("needCoder") != null) {
                    route.needCoder = Boolean.parseBoolean(jsonMap.get("needCoder").toString());
                }
                if (lines.length > 1) {
                    route.planContent = lines[1].trim();
                } else {
                    route.planContent = "";
                }
            } else {
                route.needCoder = isCodingTask(originalTask);
                route.needResearch = "medium".equals(route.complexity) || "complex".equals(route.complexity);
            }
        } catch (Exception e) {
            log.warn("解析Planner路由决策失败，使用兜底策略: {}", e.getMessage());
            route.needCoder = isCodingTask(originalTask);
            route.needResearch = !isCodingTask(originalTask) && originalTask != null && originalTask.length() > 10;
        }

        if (isCodingTask(originalTask)) {
            route.needCoder = true;
            route.complexity = "complex";
        }

        log.info("任务路由决策: complexity={}, needResearch={}, needCoder={}",
                route.complexity, route.needResearch, route.needCoder);
        return route;
    }

    public String plannerAgent(String task, String conversationId) {
        ChatClient agent = createAgent(PLANNER_SYSTEM_PROMPT, false);
        String result = agent.prompt()
                .user("请分析并规划任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "planner_" + conversationId))
                .call()
                .content();
        return result != null ? result : "";
    }

    public String researcherAgent(String query, String conversationId) {
        ChatClient agent = createAgent(RESEARCHER_SYSTEM_PROMPT, true);
        String result = agent.prompt()
                .user("请研究并回答：" + query)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "researcher_" + conversationId))
                .call()
                .content();
        return result != null ? result : "";
    }

    public String coderAgent(String task, String conversationId) {
        ChatClient agent = createAgent(CODER_SYSTEM_PROMPT, false);
        String result = agent.prompt()
                .user("请完成编程任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "coder_" + conversationId))
                .call()
                .content();
        return result != null ? result : "";
    }

    public String executorAgent(String task, String conversationId) {
        ChatClient agent = createAgent(EXECUTOR_SYSTEM_PROMPT, false);
        String result = agent.prompt()
                .user("基于之前的讨论，请给出最终结果：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "executor_" + conversationId))
                .call()
                .content();
        return result != null ? result : "";
    }

    public String collaborativeExecute(String task, String conversationId) {
        if (!StringUtils.hasText(task)) {
            return "";
        }
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

    public String collaborativeExecute(AgentChatRequest req, UserPrincipal user) {
        Long userId = user.getUserId();
        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        Long conversationId = req.getConversationId();
        String convIdStr;
        if (conversationId == null) {
            Conversation conversation = conversationService.createConversation(
                    userId,
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "multi-agent"
            );
            conversationId = conversation.getId();
            convIdStr = String.valueOf(conversationId);
            req.setConversationId(conversationId);
        } else {
            convIdStr = String.valueOf(conversationId);
            Conversation existing = conversationService.getConversationById(userId, conversationId);
            if (existing == null) {
                throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
            }
        }
        conversationService.saveMessage(userId, conversationId, "user", task);
        String result;
        try {
            result = collaborativeExecute(task, convIdStr);
        } catch (Exception e) {
            log.error("多Agent协作执行失败", e);
            throw new BusinessException(ErrorCode.AGENT_MULTI_COLLABORATION_FAILED, e);
        }
        if (StringUtils.hasText(result)) {
            conversationService.saveMessage(userId, conversationId, "assistant", result);
        }
        return result;
    }

    private Flux<String> streamPlannerAgent(String task, String conversationId) {
        ChatClient agent = createAgent(PLANNER_SYSTEM_PROMPT, false);
        return agent.prompt()
                .user("请分析并规划任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "planner_" + conversationId))
                .stream()
                .content();
    }

    private Flux<String> streamResearcherAgent(String query, String conversationId) {
        ChatClient agent = createAgent(RESEARCHER_SYSTEM_PROMPT, true);
        Mono<String> researchMono = Mono.fromCallable(() -> {
            String result = agent.prompt()
                    .user("请研究并回答：" + query)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "researcher_" + conversationId))
                    .call()
                    .content();
            return result != null ? result : "";
        }).subscribeOn(Schedulers.boundedElastic());

        return researchMono.flatMapMany(fullResponse -> {
            if (!StringUtils.hasText(fullResponse)) {
                return Flux.just("");
            }
            return Flux.fromArray(StreamUtils.splitIntoChunks(fullResponse))
                    .delayElements(Duration.ofMillis(20));
        });
    }

    private Flux<String> streamCoderAgent(String task, String conversationId) {
        ChatClient agent = createAgent(CODER_SYSTEM_PROMPT, false);
        return agent.prompt()
                .user("请完成编程任务：" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "coder_" + conversationId))
                .stream()
                .content();
    }

    private Flux<String> streamCoderReviseAgent(String originalCode, String feedback, String conversationId, int round) {
        ChatClient agent = createAgent(CODER_REVISE_SYSTEM_PROMPT, false);
        String userPrompt = String.format("""
                这是第%d轮修改。
                
                原始任务要求和你之前生成的代码：
                %s
                
                审查专家的反馈意见：
                %s
                
                请根据以上反馈修改代码，输出完整的修改后代码和修改说明。
                """, round, originalCode, feedback);
        return agent.prompt()
                .user(userPrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "coder_revise_" + round + "_" + conversationId))
                .stream()
                .content();
    }

    private Flux<String> streamCriticAgent(String content, String conversationId) {
        ChatClient agent = createAgent(CRITIC_SYSTEM_PROMPT, false);
        return agent.prompt()
                .user("请审查以下内容并给出改进建议：\n" + content)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "critic_" + conversationId))
                .stream()
                .content();
    }

    private Flux<String> streamExecutorAgent(String task, String conversationId) {
        ChatClient agent = createAgent(EXECUTOR_SYSTEM_PROMPT, false);
        return agent.prompt()
                .user("基于之前的讨论，请给出最终结果：\n" + task)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "executor_" + conversationId))
                .stream()
                .content();
    }

    public Flux<String> streamCollaborativeExecute(String task, String conversationId) {
        if (!StringUtils.hasText(task)) {
            return Flux.just(createSseEvent("error", null, "任务内容不能为空"));
        }
        StringBuilder planBuilder = new StringBuilder();
        StringBuilder researchBuilder = new StringBuilder();
        StringBuilder codeBuilder = new StringBuilder();
        StringBuilder criticBuilder = new StringBuilder();
        final TaskRoute[] routeHolder = new TaskRoute[1];

        Flux<String> plannerStream = Flux.concat(
                Flux.just(createSseEvent("agent_start", "planner", "Planner Agent 开始任务规划...")),
                streamPlannerAgent(task, conversationId)
                        .map(token -> {
                            planBuilder.append(token != null ? token : "");
                            return createSseEvent("token", "planner", token);
                        })
                        .onErrorResume(e -> Flux.just(createSseEvent("error", "planner", e.getMessage()))),
                Flux.just(createSseEvent("agent_end", "planner", ""))
        );

        Flux<String> dynamicRouteStream = Flux.defer(() -> {
            TaskRoute route = parseTaskRoute(planBuilder.toString(), task);
            routeHolder[0] = route;

            if ("simple".equals(route.complexity) && !route.needResearch && !route.needCoder) {
                return Flux.just(createSseEvent("info", null, "简单任务，跳过Researcher和Coder，直接生成回答..."));
            }

            Flux<String> stream = Flux.empty();

            if (route.needResearch) {
                Flux<String> researcherStream = Flux.concat(
                        Flux.just(createSseEvent("agent_start", "researcher", "Researcher Agent 开始信息收集...")),
                        streamResearcherAgent(task, conversationId)
                                .map(token -> {
                                    researchBuilder.append(token != null ? token : "");
                                    return createSseEvent("token", "researcher", token);
                                })
                                .onErrorResume(e -> Flux.just(createSseEvent("error", "researcher", e.getMessage()))),
                        Flux.just(createSseEvent("agent_end", "researcher", ""))
                );
                stream = Flux.concat(stream, researcherStream);
            } else {
                stream = Flux.concat(stream, Flux.just(createSseEvent("info", null, "无需信息收集，跳过Researcher...")));
            }

            if (route.needCoder) {
                Flux<String> coderStream = Flux.concat(
                        Flux.just(createSseEvent("agent_start", "coder", "Coder Agent 开始代码生成...")),
                        streamCoderAgent(task, conversationId)
                                .map(token -> {
                                    codeBuilder.append(token != null ? token : "");
                                    return createSseEvent("token", "coder", token);
                                })
                                .onErrorResume(e -> Flux.just(createSseEvent("error", "coder", e.getMessage()))),
                        Flux.just(createSseEvent("agent_end", "coder", ""))
                );
                Flux<String> criticStream = runCriticIteration(planBuilder, researchBuilder, codeBuilder, criticBuilder, conversationId, 1);
                stream = Flux.concat(stream, coderStream, criticStream);
            } else if ("complex".equals(route.complexity)) {
                Flux<String> criticOnlyStream = Flux.defer(() -> {
                    criticBuilder.setLength(0);
                    String criticInput = "规划结果：" + route.planContent + "\n\n研究结果：" + researchBuilder;
                    return Flux.concat(
                            Flux.just(createSseEvent("agent_start", "critic", "Critic Agent 开始质量审查...")),
                            streamCriticAgent(criticInput, conversationId + "_review")
                                    .map(token -> {
                                        criticBuilder.append(token != null ? token : "");
                                        return createSseEvent("token", "critic", token);
                                    })
                                    .onErrorResume(e -> Flux.just(createSseEvent("error", "critic", e.getMessage()))),
                            Flux.just(createSseEvent("agent_end", "critic", "")),
                            Flux.just(createSseEvent("info", null, "审查完成，进入最终整合阶段..."))
                    );
                });
                stream = Flux.concat(stream, criticOnlyStream);
            }

            return stream;
        });

        Flux<String> executorStream = Flux.defer(() -> {
            TaskRoute route = routeHolder[0];
            String planContent = (route != null ? route.planContent : planBuilder.toString());
            String executorInput = task + "\n\n规划结果：" + (planContent != null ? planContent : "")
                    + "\n\n研究结果：" + researchBuilder;
            if (route != null && route.needCoder) {
                executorInput += "\n\n最终代码结果：" + codeBuilder;
            }
            if (criticBuilder.length() > 0) {
                executorInput += "\n\n审查意见：" + criticBuilder;
            }
            return Flux.concat(
                    Flux.just(createSseEvent("agent_start", "executor", "Executor Agent 开始整合最终结果...")),
                    streamExecutorAgent(executorInput, conversationId)
                            .map(token -> createSseEvent("token", "executor", token))
                            .onErrorResume(e -> Flux.just(createSseEvent("error", "executor", e.getMessage()))),
                    Flux.just(createSseEvent("agent_end", "executor", "")),
                    Flux.just(createSseEvent("done", null, "所有Agent协作完成"))
            );
        });

        return Flux.concat(plannerStream, dynamicRouteStream, executorStream);
    }

    private Flux<String> runCriticIteration(StringBuilder planBuilder,
                                            StringBuilder researchBuilder,
                                            StringBuilder codeBuilder,
                                            StringBuilder criticBuilder,
                                            String conversationId,
                                            int iteration) {
        final String finalPrevCriticOutput = criticBuilder.toString();
        criticBuilder.setLength(0);

        String criticInput = "规划结果：" + planBuilder + "\n\n研究结果：" + researchBuilder
                + "\n\n" + (iteration == 1 ? "代码结果" : "修改后的代码") + "：" + codeBuilder;
        if (iteration > 1) {
            criticInput += "\n\n上一轮审查意见：" + finalPrevCriticOutput;
        }

        String agentName = iteration == 1 ? "critic" : "critic_review" + iteration;
        String startMsg = iteration == 1
                ? "Critic Agent 开始质量审查..."
                : "Critic Agent 第" + iteration + "轮审查...";

        Flux<String> reviewStream = Flux.concat(
                Flux.just(createSseEvent("agent_start", agentName, startMsg)),
                streamCriticAgent(criticInput, conversationId + "_iter" + iteration)
                        .map(token -> {
                            criticBuilder.append(token != null ? token : "");
                            return createSseEvent("token", agentName, token);
                        })
                        .onErrorResume(e -> Flux.just(createSseEvent("error", agentName, e.getMessage()))),
                Flux.just(createSseEvent("agent_end", agentName, ""))
        );

        return reviewStream.thenMany(Flux.defer(() -> {
            String criticOutput = criticBuilder.toString();
            boolean passed = criticOutput.contains("【审查结果：通过】")
                    || criticOutput.contains("审查结果：通过");

            if (passed || iteration >= MAX_CRITIC_ITERATIONS) {
                String infoMsg;
                if (passed) {
                    infoMsg = "审查通过，进入最终整合阶段...";
                } else {
                    infoMsg = "已达最大迭代次数(" + MAX_CRITIC_ITERATIONS + "轮)，进入最终整合阶段...";
                }
                return Flux.just(createSseEvent("info", null, infoMsg));
            }

            String originalCode = codeBuilder.toString();
            codeBuilder.setLength(0);
            String reviseAgentName = "coder_revise" + iteration;

            Flux<String> reviseStream = Flux.concat(
                    Flux.just(createSseEvent("info", null,
                            "审查未通过，开始第" + (iteration + 1) + "轮修改...")),
                    Flux.just(createSseEvent("agent_start", reviseAgentName,
                            "Coder Agent 根据审查意见修改代码（第" + iteration + "轮）...")),
                    streamCoderReviseAgent(originalCode, criticOutput, conversationId, iteration)
                            .map(token -> {
                                codeBuilder.append(token != null ? token : "");
                                return createSseEvent("token", reviseAgentName, token);
                            })
                            .onErrorResume(e -> Flux.just(createSseEvent("error", reviseAgentName, e.getMessage()))),
                    Flux.just(createSseEvent("agent_end", reviseAgentName, ""))
            );

            return Flux.concat(
                    reviseStream,
                    runCriticIteration(planBuilder, researchBuilder, codeBuilder, criticBuilder, conversationId, iteration + 1)
            );
        }));
    }

    public Flux<String> streamCollaborativeExecute(AgentChatRequest req, UserPrincipal user) {
        Long userId = user.getUserId();
        String task = req.getTask();
        if (!StringUtils.hasText(task)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        Long conversationId = req.getConversationId();
        String convIdStr;
        if (conversationId == null) {
            Conversation conversation = conversationService.createConversation(
                    userId,
                    task.length() > 50 ? task.substring(0, 50) + "..." : task,
                    "multi-agent"
            );
            conversationId = conversation.getId();
            convIdStr = String.valueOf(conversationId);
            req.setConversationId(conversationId);
        } else {
            convIdStr = String.valueOf(conversationId);
            Conversation existing = conversationService.getConversationById(userId, conversationId);
            if (existing == null) {
                throw new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
            }
        }
        conversationService.saveMessage(userId, conversationId, "user", task);
        Long finalConversationId = conversationId;
        final Long uid = userId;
        StringBuilder fullResponseBuilder = new StringBuilder();

        String initEvent = createSseEvent("init", null, String.valueOf(finalConversationId));

        return Flux.just(initEvent)
                .concatWith(streamCollaborativeExecute(task, convIdStr)
                        .doOnNext(event -> {
                            try {
                                Map<String, String> evt = objectMapper.readValue(event, new TypeReference<Map<String, String>>() {});
                                String type = evt.get("type");
                                if ("token".equals(type) && evt.get("content") != null) {
                                    fullResponseBuilder.append(evt.get("content"));
                                }
                            } catch (Exception e) {
                                log.warn("解析SSE事件失败: {}", e.getMessage());
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                String fullResponse = fullResponseBuilder.toString();
                                if (StringUtils.hasText(fullResponse)) {
                                    conversationService.saveMessage(uid, finalConversationId, "assistant", fullResponse);
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
                        }));
    }
}
