package com.ailearn.config;

import com.ailearn.mcp.SystemTools;
import com.ailearn.tools.CalculatorTool;
import com.ailearn.tools.WeatherTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP（Model Context Protocol）服务器配置类
 * 配置MCP服务端可用的工具（Tools），将Spring Bean中带有@Tool注解的方法
 * 注册为MCP工具，供AI模型在推理过程中调用。
 *
 * MCP协议说明：
 * - MCP是Model Context Protocol的缩写，是Spring AI定义的模型上下文协议
 * - 通过MCP，AI模型可以发现并调用外部工具获取实时数据或执行操作
 * - 工具以SSE（Server-Sent Events）方式暴露给客户端，端点为/mcp/message
 *
 * 已注册的工具：
 * - WeatherTool：天气查询工具（调用外部天气API获取实时天气信息）
 * - CalculatorTool：计算器工具（执行数学表达式计算）
 * - SystemTools：系统工具（获取系统信息、时间等系统级操作）
 *
 * @author AiLearn Platform
 */
@Slf4j
@Configuration
public class McpServerConfig {

    /**
     * 创建ToolCallbackProvider Bean
     * 使用MethodToolCallbackProvider自动扫描指定Bean中带有@Tool注解的方法，
     * 将它们包装为ToolCallback并注册到Spring AI的工具回调体系中。
     *
     * 工具注册机制：
     * - MethodToolCallbackProvider会扫描传入的toolObjects中所有public方法
     * - 带有@Tool注解的方法会被自动识别为可调用工具
     * - 工具名称默认使用方法名，描述从@Tool注解的description属性获取
     * - 方法参数会自动映射为工具的输入参数（支持@Param注解描述参数）
     *
     * 注意：新增工具只需将其声明为Spring Bean（@Component/@Service），
     * 然后在此方法的toolObjects中添加即可，无需其他额外配置。
     *
     * @param weatherTool    天气查询工具Bean，提供实时天气查询功能
     * @param calculatorTool 计算器工具Bean，提供数学表达式计算功能
     * @param systemTools    系统工具Bean，提供系统信息获取等功能
     * @return ToolCallbackProvider 工具回调提供者，包含所有已注册的MCP工具
     */
    @Bean
    public ToolCallbackProvider toolCallbackProvider(
            WeatherTool weatherTool,
            CalculatorTool calculatorTool,
            SystemTools systemTools) {
        // 记录MCP工具注册日志，列出所有注册的工具Bean
        log.info("注册MCP工具: WeatherTool(天气查询), CalculatorTool(计算器), SystemTools(系统工具)");
        // 使用Builder模式构建MethodToolCallbackProvider
        // 将所有工具Bean传入，框架会自动扫描其中的@Tool方法
        return MethodToolCallbackProvider.builder()
                // 指定包含工具方法的Spring Bean对象列表
                // 这些Bean中被@Tool注解标记的public方法将被注册为AI可调用工具
                .toolObjects(weatherTool, calculatorTool, systemTools)
                // 构建并返回ToolCallbackProvider实例
                .build();
    }
}
