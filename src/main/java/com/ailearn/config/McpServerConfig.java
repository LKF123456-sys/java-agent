package com.ailearn.config; // 声明当前类所在的包：config（配置层）

// 导入系统工具类（时间查询、系统信息等）
import com.ailearn.mcp.SystemTools;
// 导入计算器工具类（数学表达式计算）
import com.ailearn.tools.CalculatorTool;
// 导入天气查询工具类（调用外部天气API）
import com.ailearn.tools.WeatherTool;
// 导入联网搜索工具类（Tavily搜索API）
import com.ailearn.tools.WebSearchTool;
// 导入Lombok日志注解，自动生成log对象
import lombok.extern.slf4j.Slf4j;
// 导入工具回调提供者接口，Spring AI工具体系的核心抽象（AgentService/MultiAgentService都靠它拿工具）
import org.springframework.ai.tool.ToolCallbackProvider;
// 导入基于方法的工具回调提供者实现，扫描@Tool注解的方法并包装成工具
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
// 导入@Bean注解，把方法返回值注册为Spring Bean
import org.springframework.context.annotation.Bean;
// 导入@Configuration注解，标记这是配置类
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
@Slf4j // Lombok注解：自动生成log日志对象
@Configuration // 标记为Spring配置类，启动时被扫描并执行@Bean方法
public class McpServerConfig { // 定义MCP服务器配置类

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
     * @param webSearchTool  联网搜索工具Bean，提供Tavily联网搜索功能
     * @return ToolCallbackProvider 工具回调提供者，包含所有已注册的MCP工具
     */
    @Bean // 把ToolCallbackProvider注册为Spring Bean（AgentService、MultiAgentService都会注入它来获取全部工具）
    public ToolCallbackProvider toolCallbackProvider( // 方法参数由Spring容器自动注入对应的工具Bean
            WeatherTool weatherTool, // 注入天气工具（@Component已声明为Bean）
            CalculatorTool calculatorTool, // 注入计算器工具
            SystemTools systemTools, // 注入系统工具
            WebSearchTool webSearchTool) { // 注入联网搜索工具
        log.info("注册MCP工具: WeatherTool(天气查询), CalculatorTool(计算器), SystemTools(系统工具), WebSearchTool(联网搜索)"); // 打印注册日志，启动时确认工具加载
        return MethodToolCallbackProvider.builder() // 用构建器模式创建基于方法的工具提供者
                .toolObjects(weatherTool, calculatorTool, systemTools, webSearchTool) // 传入4个工具对象：扫描它们所有@Tool注解的方法并注册（Spring AI 2.0中工具循环由ToolCallingAdvisor驱动）
                .build(); // 构建完成，返回包含全部工具的Provider
    }
}
