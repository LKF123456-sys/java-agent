package com.ailearn.mcp; // 声明包名

import com.ailearn.common.Result; // 统一响应
import com.ailearn.security.UserPrincipal; // 用户主体
import com.ailearn.tools.CalculatorTool; // 计算器工具
import com.ailearn.tools.WeatherTool; // 天气工具
import io.swagger.v3.oas.annotations.Operation; // OpenAPI操作注解
import io.swagger.v3.oas.annotations.Parameter; // OpenAPI参数注解
import io.swagger.v3.oas.annotations.responses.ApiResponse; // OpenAPI响应注解
import io.swagger.v3.oas.annotations.responses.ApiResponses; // OpenAPI响应组注解
import io.swagger.v3.oas.annotations.tags.Tag; // OpenAPI分组注解
import lombok.RequiredArgsConstructor; // 构造器注入
import lombok.extern.slf4j.Slf4j; // 日志
import org.springframework.security.core.Authentication; // 认证接口
import org.springframework.security.core.context.SecurityContextHolder; // 安全上下文
import org.springframework.web.bind.annotation.*; // Web注解

import java.util.ArrayList; // 动态数组
import java.util.LinkedHashMap; // 有序Map
import java.util.List; // 列表
import java.util.Map; // Map接口

/**
 * MCP协议控制器
 * 提供Model Context Protocol（MCP）服务器的信息查询和工具列表接口
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log
@RestController // REST控制器
@RequestMapping("/api/mcp") // 根路径
@RequiredArgsConstructor // 构造器注入
@Tag(name = "MCP协议", description = "MCP服务器信息和工具列表") // API分组
public class McpController { // MCP控制器类

    /** 天气查询工具 */
    private final WeatherTool weatherTool; // 注入

    /** 数学计算工具 */
    private final CalculatorTool calculatorTool; // 注入

    /** 系统工具 */
    private final SystemTools systemTools; // 注入

    /**
     * 获取MCP服务器信息
     * 接口路径：GET /api/mcp/info
     *
     * @return Result<Map> MCP服务器信息
     */
    @GetMapping("/info") // GET映射
    @Operation(summary = "获取MCP服务器信息", description = "获取MCP服务器的基本信息") // API描述
    @ApiResponses(value = { // 响应码
            @ApiResponse(responseCode = "200", description = "获取成功"), // 200
            @ApiResponse(responseCode = "401", description = "未登录或Token无效") // 401
    })
    public Result<Map<String, Object>> getMcpInfo() { // 服务器信息方法
        log.debug("获取MCP服务器信息"); // 调试日志
        Map<String, Object> info = new LinkedHashMap<>(); // 有序Map
        info.put("name", "cyber-ai-platform"); // 服务器名
        info.put("version", "1.0.0"); // 版本
        info.put("type", "SYNC"); // 类型
        info.put("sseEndpoint", "/sse"); // SSE端点
        info.put("messageEndpoint", "/mcp/message"); // 消息端点
        info.put("status", "running"); // 运行状态
        info.put("toolCount", 6); // 工具数量
        return Result.success(info); // 返回信息
    }

    /**
     * 获取注册的MCP工具列表
     * 接口路径：GET /api/mcp/tools
     *
     * @return Result<List<Map>> MCP工具列表
     */
    @GetMapping("/tools") // GET映射
    @Operation(summary = "获取注册的MCP工具列表", description = "获取所有已注册的工具列表") // API描述
    @ApiResponses(value = { // 响应码
            @ApiResponse(responseCode = "200", description = "获取成功"), // 200
            @ApiResponse(responseCode = "401", description = "未登录或Token无效") // 401
    })
    public Result<List<Map<String, Object>>> getTools() { // 工具列表方法
        log.debug("获取MCP工具列表"); // 调试日志
        List<Map<String, Object>> tools = new ArrayList<>(); // 工具列表

        // 天气工具
        tools.add(createToolInfo("weatherTool", "getWeather", "获取指定城市的当前天气信息", // 添加天气工具
                List.of( // 参数列表
                        createParamInfo("city", "String", "城市名称，如：北京、上海、深圳", true), // city参数
                        createParamInfo("unit", "String", "温度单位：celsius或fahrenheit，默认celsius", false) // unit参数
                )));

        // 计算器工具
        tools.add(createToolInfo("calculatorTool", "calculate", "执行基础数学运算：加减乘除", // 添加计算器
                List.of( // 参数
                        createParamInfo("a", "double", "第一个数字", true), // a
                        createParamInfo("operator", "String", "运算符：+、-、*、/", true), // operator
                        createParamInfo("b", "double", "第二个数字", true) // b
                )));

        // 系统工具-获取时间
        tools.add(createToolInfo("systemTools", "getCurrentTime", "获取当前服务器时间", List.of())); // 无参数

        // 系统工具-获取系统信息
        tools.add(createToolInfo("systemTools", "getSystemInfo", "获取系统信息，包括Java版本、可用处理器、内存等", List.of())); // 无参数

        // 系统工具-Agent列表
        tools.add(createToolInfo("systemTools", "listAvailableAgents", "列出系统可用的AI Agent类型", List.of())); // 无参数

        // 计算器-表达式计算
        tools.add(createToolInfo("calculatorTool", "calculateExpression", "执行数学表达式计算，支持加减乘除运算、括号、函数", // 表达式工具
                List.of( // 参数
                        createParamInfo("expression", "String", "数学表达式，如：(2+3)*4、Math.sqrt(16)+Math.pow(2,3)", true) // expression
                )));

        return Result.success(tools); // 返回工具列表
    }

    /**
     * 创建工具信息Map
     *
     * @param beanName    工具Bean名称
     * @param methodName  工具方法名
     * @param description 工具功能描述
     * @param parameters  参数列表
     * @return Map 工具信息
     */
    private Map<String, Object> createToolInfo(String beanName, String methodName, String description, // 工具信息构造
                                                List<Map<String, Object>> parameters) { // 参数列表
        Map<String, Object> tool = new LinkedHashMap<>(); // 有序Map
        tool.put("beanName", beanName); // Bean名
        tool.put("methodName", methodName); // 方法名
        tool.put("name", methodName); // 显示名
        tool.put("description", description); // 描述
        tool.put("parameters", parameters); // 参数
        return tool; // 返回
    }

    /**
     * 创建参数信息Map
     *
     * @param name        参数名称
     * @param type        参数类型
     * @param description 参数描述
     * @param required    是否必填
     * @return Map 参数信息
     */
    private Map<String, Object> createParamInfo(String name, String type, String description, boolean required) { // 参数信息构造
        Map<String, Object> param = new LinkedHashMap<>(); // 有序Map
        param.put("name", name); // 参数名
        param.put("type", type); // 类型
        param.put("description", description); // 描述
        param.put("required", required); // 是否必填
        return param; // 返回
    }

    /**
     * 从SecurityContext获取当前登录用户信息
     *
     * @return UserPrincipal 当前用户，未登录返回null
     */
    private UserPrincipal getCurrentUser() { // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // 取认证对象
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) { // 类型匹配
            return (UserPrincipal) authentication.getPrincipal(); // 强转返回
        }
        return null; // 未登录返回null
    }
} // McpController类结束
