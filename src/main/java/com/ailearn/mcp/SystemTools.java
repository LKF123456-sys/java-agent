package com.ailearn.mcp; // 声明包名，mcp包存放MCP协议工具类

import lombok.extern.slf4j.Slf4j; // Lombok注解，自动生成log日志对象
import org.springframework.ai.tool.annotation.Tool; // Spring AI注解，标记方法为可被大模型调用的工具
import org.springframework.ai.tool.annotation.ToolParam; // Spring AI注解，描述工具的参数
import org.springframework.stereotype.Component; // Spring注解，标记为容器管理的组件

import java.lang.management.ManagementFactory; // JDK管理工厂，获取OS/JVM/Memory等MXBean
import java.lang.management.MemoryMXBean; // 内存MXBean，获取堆/非堆内存使用情况
import java.lang.management.OperatingSystemMXBean; // 操作系统MXBean，获取OS信息
import java.lang.management.RuntimeMXBean; // 运行时MXBean，获取JVM启动信息
import java.net.InetAddress; // 网络地址类，获取主机名
import java.time.LocalDateTime; // 本地日期时间类（不带时区）
import java.time.ZoneId; // 时区ID
import java.time.format.DateTimeFormatter; // 日期格式化器

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
@Slf4j // 自动注入log对象
@Component // 注册为Spring Bean，被McpServerConfig的ToolCallbackProvider扫描注册
public class SystemTools { // 系统工具类定义

    /**
     * 日期时间格式化器，用于格式化LocalDateTime输出
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = // 静态常量，类加载时初始化
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); // 模式：年-月-日 时:分:秒

    /**
     * 获取当前服务器时间
     * 返回服务器所在时区的当前日期和时间，精确到秒。
     *
     * @return String 格式化的当前时间字符串，格式：yyyy-MM-dd HH:mm:ss
     */
    @Tool(description = "获取当前服务器的日期和时间。当用户询问现在几点、当前时间、今天日期等问题时使用此工具。") // @Tool：把此方法暴露为AI可调用工具，description是给大模型看的工具说明
    public String getCurrentTime() { // 工具方法定义，无参数
        log.debug("系统工具被调用: getCurrentTime"); // 调试日志，记录工具被调用
        LocalDateTime now = LocalDateTime.now(); // 获取当前本地时间
        String result = String.format("当前服务器时间：%s（时区：%s）", // 拼装结果字符串
                now.format(DATE_TIME_FORMATTER), // 格式化时间为字符串
                ZoneId.systemDefault().getId()); // 获取系统默认时区ID
        log.debug("当前时间: {}", result); // 调试日志
        return result; // 返回时间字符串
    }

    /**
     * 获取当前Unix时间戳
     * 返回从1970年1月1日00:00:00 UTC到现在的秒数/毫秒数。
     *
     * @param inMilliseconds 是否返回毫秒级时间戳，true=毫秒，false=秒（默认）
     * @return String 时间戳字符串
     */
    @Tool(description = "获取当前Unix时间戳（从1970年1月1日开始计算的秒数或毫秒数）。当需要时间戳、计时等场景时使用。") // 工具说明
    public String currentTimestamp( // 带参数的工具方法
            @ToolParam(description = "是否返回毫秒级时间戳，true为毫秒，false为秒，默认false", required = false) Boolean inMilliseconds) { // @ToolParam描述参数；required=false表示可选
        log.debug("系统工具被调用: currentTimestamp, inMilliseconds={}", inMilliseconds); // 调试日志
        long timestamp; // 时间戳数值
        String unit; // 单位描述（秒/毫秒）
        if (Boolean.TRUE.equals(inMilliseconds)) { // 若要求毫秒（用Boolean.TRUE.equals避免null拆箱NPE）
            timestamp = System.currentTimeMillis(); // 取毫秒时间戳
            unit = "毫秒"; // 单位为毫秒
        } else { // 否则返回秒级
            timestamp = System.currentTimeMillis() / 1000; // 毫秒除以1000得到秒
            unit = "秒"; // 单位为秒
        }
        return String.format("当前Unix时间戳：%d（%s）", timestamp, unit); // 返回格式化结果
    }

    /**
     * 获取系统详细信息
     * 返回操作系统、CPU、内存、主机名等系统级信息。
     *
     * @return String 格式化的系统信息字符串
     */
    @Tool(description = "获取服务器系统信息，包括操作系统名称和版本、CPU核心数、内存使用情况、主机名等。当用户询问系统配置、服务器信息、内存使用等问题时使用。") // 工具说明
    public String getSystemInfo() { // 无参工具方法
        log.debug("系统工具被调用: getSystemInfo"); // 调试日志
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean(); // 获取操作系统MXBean
        Runtime runtime = Runtime.getRuntime(); // 获取JVM运行时对象（查内存用）

        String osName = osBean.getName(); // OS名称
        String osVersion = osBean.getVersion(); // OS版本
        String osArch = osBean.getArch(); // CPU架构
        int availableProcessors = osBean.getAvailableProcessors(); // 可用CPU核心数
        double systemLoadAverage = osBean.getSystemLoadAverage(); // 系统平均负载（最近1分钟）

        long maxMemory = runtime.maxMemory() / (1024 * 1024); // JVM最大可用内存（字节转MB）
        long totalMemory = runtime.totalMemory() / (1024 * 1024); // JVM已分配内存
        long freeMemory = runtime.freeMemory() / (1024 * 1024); // JVM空闲内存
        long usedMemory = totalMemory - freeMemory; // 已使用内存 = 已分配 - 空闲

        String hostname = "未知"; // 主机名默认值
        try { // 尝试获取主机名
            hostname = InetAddress.getLocalHost().getHostName(); // 通过InetAddress获取
        } catch (Exception e) { // 获取失败
            log.debug("获取主机名失败", e); // 仅调试日志，不抛异常
        }

        // 用文本块拼装多行结果（注意：文本块起始"""后不能有注释）
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
                hostname, osName, osVersion, osArch, // 占位参数1-4
                availableProcessors, systemLoadAverage, // 占位参数5-6
                maxMemory, totalMemory, usedMemory, freeMemory); // 占位参数7-10
        log.debug("系统信息获取完成"); // 调试日志
        return result; // 返回系统信息字符串
    }

    /**
     * 获取JVM运行时信息
     * 返回Java版本、JVM名称、启动时间、运行时长等JVM相关信息。
     *
     * @return String 格式化的JVM信息字符串
     */
    @Tool(description = "获取JVM（Java虚拟机）运行时信息，包括Java版本、JVM名称、启动参数、运行时长等。") // 工具说明
    public String getJvmInfo() { // 无参工具方法
        log.debug("系统工具被调用: getJvmInfo"); // 调试日志
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean(); // 获取运行时MXBean
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean(); // 获取内存MXBean

        String javaVersion = System.getProperty("java.version"); // Java版本（系统属性）
        String javaVendor = System.getProperty("java.vendor"); // Java厂商
        String jvmName = runtimeBean.getVmName(); // JVM名称
        String jvmVersion = runtimeBean.getVmVersion(); // JVM版本
        long startTime = runtimeBean.getStartTime(); // JVM启动时间戳（毫秒）
        long uptime = runtimeBean.getUptime() / 1000; // 运行时长（毫秒转秒）

        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024); // 堆内存已用（MB）
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024); // 堆内存最大（MB）
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024); // 非堆内存已用（MB）

        long uptimeHours = uptime / 3600; // 运行时长-小时
        long uptimeMinutes = (uptime % 3600) / 60; // 运行时长-分钟
        long uptimeSeconds = uptime % 60; // 运行时长-秒

        LocalDateTime startDateTime = LocalDateTime.ofInstant( // 把启动毫秒时间戳转本地时间
                java.time.Instant.ofEpochMilli(startTime), ZoneId.systemDefault()); // 用Instant+时区转换

        // 文本块拼装JVM信息（起始"""后不能有注释）
        return String.format("""
                        JVM运行时信息：
                        - Java版本：%s（%s）
                        - JVM名称：%s %s
                        - 启动时间：%s
                        - 运行时长：%d小时%d分钟%d秒
                        - 堆内存使用：%dMB / %dMB
                        - 非堆内存使用：%dMB""",
                javaVersion, javaVendor, jvmName, jvmVersion, // 占位参数1-4
                startDateTime.format(DATE_TIME_FORMATTER), // 启动时间格式化
                uptimeHours, uptimeMinutes, uptimeSeconds, // 运行时长
                heapUsed, heapMax, nonHeapUsed); // 内存
    }

    /**
     * 列出系统可用的AI Agent类型
     * 返回平台支持的所有AI Agent功能列表。
     *
     * @return String 可用Agent类型列表
     */
    @Tool(description = "列出系统当前支持的所有AI Agent（智能体）类型及其功能说明。当用户询问有哪些功能、支持什么Agent时使用。") // 工具说明
    public String listAvailableAgents() { // 无参工具方法
        log.debug("系统工具被调用: listAvailableAgents"); // 调试日志
        // 直接返回固定文本块（无需计算，起始"""后不能有注释）
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
    @Tool(description = "执行简单的两数基础数学运算：加法、减法、乘法、除法。简单计算场景使用，复杂表达式计算请使用计算器工具。") // 工具说明
    public String simpleCalculate( // 带三个参数的工具方法
            @ToolParam(description = "第一个数字") double a, // 参数a
            @ToolParam(description = "运算符：+、-、*、/") String operator, // 参数operator
            @ToolParam(description = "第二个数字") double b) { // 参数b
        log.info("系统工具被调用: simpleCalculate, {} {} {}", a, operator, b); // 业务日志
        double result; // 计算结果
        try { // 尝试计算
            result = switch (operator) { // 用switch表达式按运算符分支（JDK 14+）
                case "+" -> a + b; // 加法
                case "-" -> a - b; // 减法
                case "*" -> a * b; // 乘法
                case "/" -> { // 除法
                    if (b == 0) { // 除数为0
                        throw new ArithmeticException("除数不能为零"); // 抛算术异常
                    }
                    yield a / b; // switch表达式分支用yield返回值
                }
                default -> throw new IllegalArgumentException("不支持的运算符: " + operator); // 未知运算符
            };
        } catch (Exception e) { // 捕获计算异常
            log.warn("计算错误: {}", e.getMessage()); // 警告日志
            return String.format("计算错误：%s", e.getMessage()); // 返回错误信息给大模型
        }
        return String.format("计算结果：%.2f %s %.2f = %.2f", a, operator, b, result); // 返回格式化结果
    }

    /**
     * 计算字符串长度
     * 返回输入字符串的字符数。
     *
     * @param text 需要计算长度的字符串
     * @return String 字符串长度信息
     */
    @Tool(description = "计算文本字符串的长度（字符数）。当需要统计字数、检查长度限制时使用。") // 工具说明
    public String stringLength( // 带一个参数的工具方法
            @ToolParam(description = "需要计算长度的文本字符串") String text) { // 参数text
        log.debug("系统工具被调用: stringLength"); // 调试日志
        if (text == null) { // 入参为null
            return "字符串长度：0（空字符串）"; // 返回0
        }
        int length = text.length(); // 字符串总字符数
        int chineseCount = 0; // 中文字符计数
        for (char c : text.toCharArray()) { // 遍历每个字符
            if (c >= 0x4E00 && c <= 0x9FFF) { // CJK统一汉字Unicode范围
                chineseCount++; // 命中则中文字符+1
            }
        }
        return String.format("字符串长度：%d个字符，其中中文字符约%d个", length, chineseCount); // 返回长度信息
    }
} // SystemTools类结束
