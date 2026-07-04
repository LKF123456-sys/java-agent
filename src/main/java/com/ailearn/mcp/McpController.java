package com.ailearn.mcp;

import com.ailearn.common.Result;
import com.ailearn.security.UserPrincipal;
import com.ailearn.tools.CalculatorTool;
import com.ailearn.tools.WeatherTool;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP协议控制器
 * 提供Model Context Protocol（MCP）服务器的信息查询和工具列表接口
 * Spring AI MCP Server已自动暴露/mcp/sse端点用于MCP客户端连接，
 * 本控制器提供REST API用于查询MCP服务器状态和已注册的工具信息
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP协议", description = "MCP服务器信息和工具列表（Spring AI MCP Server已自动暴露/mcp/sse端点）")
public class McpController {

    /**
     * 天气查询工具，已注册到MCP服务器
     */
    private final WeatherTool weatherTool;

    /**
     * 数学计算工具，已注册到MCP服务器
     */
    private final CalculatorTool calculatorTool;

    /**
     * 系统工具，提供时间、系统信息、Agent列表等
     */
    private final SystemTools systemTools;

    /**
     * 获取MCP服务器信息
     * 返回MCP服务器的基本信息，包括服务器名称、版本、状态、端点地址等
     * 接口路径：GET /api/mcp/info
     *
     * @return Result<Map> MCP服务器信息，包含：
     *         - name: String 服务器名称
     *         - version: String 服务器版本
     *         - type: String 服务器类型（SYNC/ASYNC）
     *         - sseEndpoint: String SSE连接端点
     *         - messageEndpoint: String 消息发送端点
     *         - status: String 服务器运行状态
     *         - toolCount: int 已注册工具数量
     */
    @GetMapping("/info")
    @Operation(summary = "获取MCP服务器信息", description = "获取MCP（Model Context Protocol）服务器的基本信息，包括名称、版本、状态、端点地址和工具数量")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<Map<String, Object>> getMcpInfo() {
        log.debug("获取MCP服务器信息");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "cyber-ai-platform");
        info.put("version", "1.0.0");
        info.put("type", "SYNC");
        info.put("sseEndpoint", "/sse");
        info.put("messageEndpoint", "/mcp/message");
        info.put("status", "running");
        info.put("toolCount", 6);
        return Result.success(info);
    }

    /**
     * 获取注册的MCP工具列表
     * 返回所有已注册到MCP服务器的工具信息，包括工具名称、描述、参数列表等
     * 接口路径：GET /api/mcp/tools
     *
     * @return Result<List<Map>> MCP工具列表，每个工具包含：
     *         - beanName: String 工具Bean名称
     *         - methodName: String 工具方法名
     *         - name: String 工具显示名称
     *         - description: String 工具功能描述
     *         - parameters: List<Map> 参数列表（名称、类型、描述、是否必填）
     */
    @GetMapping("/tools")
    @Operation(summary = "获取注册的MCP工具列表", description = "获取所有已注册到MCP服务器的工具列表，包括工具名称、功能描述和参数说明")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<List<Map<String, Object>>> getTools() {
        log.debug("获取MCP工具列表");
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(createToolInfo("weatherTool", "getWeather", "获取指定城市的当前天气信息",
                List.of(
                        createParamInfo("city", "String", "城市名称，如：北京、上海、深圳", true),
                        createParamInfo("unit", "String", "温度单位：celsius或fahrenheit，默认celsius", false)
                )));

        tools.add(createToolInfo("calculatorTool", "calculate", "执行基础数学运算：加减乘除",
                List.of(
                        createParamInfo("a", "double", "第一个数字", true),
                        createParamInfo("operator", "String", "运算符：+、-、*、/", true),
                        createParamInfo("b", "double", "第二个数字", true)
                )));

        tools.add(createToolInfo("systemTools", "getCurrentTime", "获取当前服务器时间", List.of()));

        tools.add(createToolInfo("systemTools", "getSystemInfo", "获取系统信息，包括Java版本、可用处理器、内存等", List.of()));

        tools.add(createToolInfo("systemTools", "listAvailableAgents", "列出系统可用的AI Agent类型", List.of()));

        tools.add(createToolInfo("calculatorTool", "calculateExpression", "执行数学表达式计算，支持加减乘除运算、括号、函数",
                List.of(
                        createParamInfo("expression", "String", "数学表达式，如：(2+3)*4、Math.sqrt(16)+Math.pow(2,3)", true)
                )));

        return Result.success(tools);
    }

    /**
     * 创建工具信息Map
     * 私有辅助方法，用于构建工具信息的Map结构
     *
     * @param beanName    工具Bean名称
     * @param methodName  工具方法名
     * @param description 工具功能描述
     * @param parameters  参数列表
     * @return Map<String, Object> 工具信息Map
     */
    private Map<String, Object> createToolInfo(String beanName, String methodName, String description,
                                                List<Map<String, Object>> parameters) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("beanName", beanName);
        tool.put("methodName", methodName);
        tool.put("name", methodName);
        tool.put("description", description);
        tool.put("parameters", parameters);
        return tool;
    }

    /**
     * 创建参数信息Map
     * 私有辅助方法，用于构建参数信息的Map结构
     *
     * @param name        参数名称
     * @param type        参数类型
     * @param description 参数描述
     * @param required    是否必填
     * @return Map<String, Object> 参数信息Map
     */
    private Map<String, Object> createParamInfo(String name, String type, String description, boolean required) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", name);
        param.put("type", type);
        param.put("description", description);
        param.put("required", required);
        return param;
    }

    /**
     * 从SecurityContext获取当前登录用户信息
     * 私有辅助方法，用于在各个接口中获取当前用户
     *
     * @return UserPrincipal 当前用户主体，未登录时返回null
     */
    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
