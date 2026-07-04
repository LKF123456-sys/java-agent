package com.ailearn.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 系统工具类
 * 提供给AI Agent调用的系统信息查询功能，包括获取当前时间、系统信息、JVM信息、
 * 网络信息等。作为MCP（Model Context Protocol）工具暴露给大模型调用。
 *
 * <p>工具列表：
 * <ul>
 *   <li>getCurrentTime - 获取当前服务器时间</li>
 *   <li>getSystemInfo - 获取系统详细信息（OS、CPU、内存等）</li>
 *   <li>getJvmInfo - 获取JVM运行时信息</li>
 *   <li>listAvailableAgents - 列出系统可用的AI Agent类型</li>
 *   <li>simpleCalculate - 执行简单基础数学运算</li>
 *   <li>stringLength - 计算字符串长度</li>
 *   <li>currentTimestamp - 获取当前Unix时间戳</li>
 * </ul>
 *
 * @author AiLearn Platform
 */
@Slf4j
@Component
public class SystemTools {

    /**
     * 日期时间格式化器，用于格式化LocalDateTime输出
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取当前服务器时间
     * 返回服务器所在时区的当前日期和时间，精确到秒。
     *
     * @return String 格式化的当前时间字符串，格式：yyyy-MM-dd HH:mm:ss
     */
    @Tool(description = "获取当前服务器的日期和时间。当用户询问现在几点、当前时间、今天日期等问题时使用此工具。")
    public String getCurrentTime() {
        log.debug("系统工具被调用: getCurrentTime");
        LocalDateTime now = LocalDateTime.now();
        String result = String.format("当前服务器时间：%s（时区：%s）",
                now.format(DATE_TIME_FORMATTER),
                ZoneId.systemDefault().getId());
        log.debug("当前时间: {}", result);
        return result;
    }

    /**
     * 获取当前Unix时间戳
     * 返回从1970年1月1日00:00:00 UTC到现在的秒数/毫秒数。
     *
     * @param inMilliseconds 是否返回毫秒级时间戳，true=毫秒，false=秒（默认）
     * @return String 时间戳字符串
     */
    @Tool(description = "获取当前Unix时间戳（从1970年1月1日开始计算的秒数或毫秒数）。当需要时间戳、计时等场景时使用。")
    public String currentTimestamp(
            @ToolParam(description = "是否返回毫秒级时间戳，true为毫秒，false为秒，默认false", required = false) Boolean inMilliseconds) {
        log.debug("系统工具被调用: currentTimestamp, inMilliseconds={}", inMilliseconds);
        long timestamp;
        String unit;
        if (Boolean.TRUE.equals(inMilliseconds)) {
            timestamp = System.currentTimeMillis();
            unit = "毫秒";
        } else {
            timestamp = System.currentTimeMillis() / 1000;
            unit = "秒";
        }
        return String.format("当前Unix时间戳：%d（%s）", timestamp, unit);
    }

    /**
     * 获取系统详细信息
     * 返回操作系统、CPU、内存、主机名等系统级信息。
     *
     * @return String 格式化的系统信息字符串
     */
    @Tool(description = "获取服务器系统信息，包括操作系统名称和版本、CPU核心数、内存使用情况、主机名等。当用户询问系统配置、服务器信息、内存使用等问题时使用。")
    public String getSystemInfo() {
        log.debug("系统工具被调用: getSystemInfo");
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        Runtime runtime = Runtime.getRuntime();

        String osName = osBean.getName();
        String osVersion = osBean.getVersion();
        String osArch = osBean.getArch();
        int availableProcessors = osBean.getAvailableProcessors();
        double systemLoadAverage = osBean.getSystemLoadAverage();

        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        String hostname = "未知";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.debug("获取主机名失败", e);
        }

        String result = String.format("""
                        系统信息：
                        - 主机名：%s
                        - 操作系统：%s %s (%s)
                        - 可用处理器：%d核
                        - 系统负载：%.2f（最近1分钟平均）
                        - JVM最大内存：%dMB
                        - JVM已分配内存：%dMB
                        - JVM已使用内存：%dMB
                        - JVM空闲内存：%dMB""",
                hostname, osName, osVersion, osArch,
                availableProcessors, systemLoadAverage,
                maxMemory, totalMemory, usedMemory, freeMemory);
        log.debug("系统信息获取完成");
        return result;
    }

    /**
     * 获取JVM运行时信息
     * 返回Java版本、JVM名称、启动时间、运行时长等JVM相关信息。
     *
     * @return String 格式化的JVM信息字符串
     */
    @Tool(description = "获取JVM（Java虚拟机）运行时信息，包括Java版本、JVM名称、启动参数、运行时长等。")
    public String getJvmInfo() {
        log.debug("系统工具被调用: getJvmInfo");
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        String jvmName = runtimeBean.getVmName();
        String jvmVersion = runtimeBean.getVmVersion();
        long startTime = runtimeBean.getStartTime();
        long uptime = runtimeBean.getUptime() / 1000;

        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024);

        long uptimeHours = uptime / 3600;
        long uptimeMinutes = (uptime % 3600) / 60;
        long uptimeSeconds = uptime % 60;

        LocalDateTime startDateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(startTime), ZoneId.systemDefault());

        return String.format("""
                        JVM运行时信息：
                        - Java版本：%s（%s）
                        - JVM名称：%s %s
                        - 启动时间：%s
                        - 运行时长：%d小时%d分钟%d秒
                        - 堆内存使用：%dMB / %dMB
                        - 非堆内存使用：%dMB""",
                javaVersion, javaVendor, jvmName, jvmVersion,
                startDateTime.format(DATE_TIME_FORMATTER),
                uptimeHours, uptimeMinutes, uptimeSeconds,
                heapUsed, heapMax, nonHeapUsed);
    }

    /**
     * 列出系统可用的AI Agent类型
     * 返回平台支持的所有AI Agent功能列表。
     *
     * @return String 可用Agent类型列表
     */
    @Tool(description = "列出系统当前支持的所有AI Agent（智能体）类型及其功能说明。当用户询问有哪些功能、支持什么Agent时使用。")
    public String listAvailableAgents() {
        log.debug("系统工具被调用: listAvailableAgents");
        return """
                系统可用的AI Agent类型：
                1. chat - 基础聊天对话Agent，支持多轮对话
                2. memory - 带长期记忆功能的对话Agent
                3. agent - 工具调用Agent，可使用天气查询、数学计算等工具
                4. multi-agent - 多Agent协作系统，包含Planner/Researcher/Coder/Critic/Executor五个角色协同工作
                5. rag - 检索增强生成Agent，基于知识库文档回答问题，支持PDF/Word/图片等文件上传
                6. structured - 结构化输出Agent，可提取图书、电影等实体信息为JSON格式
                7. mcp-tools - MCP协议工具集，提供时间查询、系统信息、数学计算等系统工具
                """;
    }

    /**
     * 执行简单基础数学运算
     * 支持加减乘除等基础两数运算，是CalculatorTool的轻量代理入口。
     *
     * @param a        第一个数字
     * @param operator 运算符：+、-、*、/
     * @param b        第二个数字
     * @return String 计算结果
     */
    @Tool(description = "执行简单的两数基础数学运算：加法、减法、乘法、除法。简单计算场景使用，复杂表达式计算请使用计算器工具。")
    public String simpleCalculate(
            @ToolParam(description = "第一个数字") double a,
            @ToolParam(description = "运算符：+、-、*、/") String operator,
            @ToolParam(description = "第二个数字") double b) {
        log.info("系统工具被调用: simpleCalculate, {} {} {}", a, operator, b);
        double result;
        try {
            result = switch (operator) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> {
                    if (b == 0) {
                        throw new ArithmeticException("除数不能为零");
                    }
                    yield a / b;
                }
                default -> throw new IllegalArgumentException("不支持的运算符: " + operator);
            };
        } catch (Exception e) {
            log.warn("计算错误: {}", e.getMessage());
            return String.format("计算错误：%s", e.getMessage());
        }
        return String.format("计算结果：%.2f %s %.2f = %.2f", a, operator, b, result);
    }

    /**
     * 计算字符串长度
     * 返回输入字符串的字符数。
     *
     * @param text 需要计算长度的字符串
     * @return String 字符串长度信息
     */
    @Tool(description = "计算文本字符串的长度（字符数）。当需要统计字数、检查长度限制时使用。")
    public String stringLength(
            @ToolParam(description = "需要计算长度的文本字符串") String text) {
        log.debug("系统工具被调用: stringLength");
        if (text == null) {
            return "字符串长度：0（空字符串）";
        }
        int length = text.length();
        int chineseCount = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {
                chineseCount++;
            }
        }
        return String.format("字符串长度：%d个字符，其中中文字符约%d个", length, chineseCount);
    }
}
