package com.ailearn.tools; // 声明包名

import com.ailearn.common.Result; // 统一响应
import com.ailearn.security.UserPrincipal; // 用户主体
import io.swagger.v3.oas.annotations.Operation; // OpenAPI操作注解
import io.swagger.v3.oas.annotations.Parameter; // OpenAPI参数注解
import io.swagger.v3.oas.annotations.responses.ApiResponse; // OpenAPI响应注解
import io.swagger.v3.oas.annotations.responses.ApiResponses; // OpenAPI响应组
import io.swagger.v3.oas.annotations.tags.Tag; // OpenAPI分组
import lombok.RequiredArgsConstructor; // 构造器注入
import lombok.extern.slf4j.Slf4j; // 日志
import org.springframework.security.core.Authentication; // 认证接口
import org.springframework.security.core.context.SecurityContextHolder; // 安全上下文
import org.springframework.web.bind.annotation.*; // Web注解

import java.util.HashMap; // 哈希表
import java.util.Map; // Map接口

/**
 * 工具测试控制器
 * 提供直接测试系统工具函数的接口，无需通过AI Agent即可直接调用
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log
@RestController // REST控制器
@RequestMapping("/api/tools") // 根路径
@RequiredArgsConstructor // 构造器注入
@Tag(name = "工具测试", description = "直接测试工具函数") // API分组
public class ToolsController { // 工具测试控制器

    /** 天气查询工具 */
    private final WeatherTool weatherTool; // 注入

    /** 数学计算工具 */
    private final CalculatorTool calculatorTool; // 注入

    /**
     * 天气查询接口
     * 接口路径：GET /api/tools/weather
     *
     * @param city 城市名称
     * @return Result<Map> 包含city和result
     */
    @GetMapping("/weather") // GET映射
    @Operation(summary = "天气查询", description = "直接调用天气工具查询指定城市的当前天气") // API描述
    @ApiResponses(value = { // 响应码
            @ApiResponse(responseCode = "200", description = "查询成功"), // 200
            @ApiResponse(responseCode = "400", description = "参数校验失败"), // 400
            @ApiResponse(responseCode = "401", description = "未登录或Token无效") // 401
    })
    public Result<Map<String, Object>> weather( // 天气查询方法
            @Parameter(description = "城市名称，如：北京、上海、深圳、成都等", required = true) // 参数描述
            @RequestParam String city) { // 绑定city参数
        log.info("天气查询请求: city={}", city); // 业务日志
        String weatherResult = weatherTool.getWeather(city, null); // 直接调用天气工具（单位默认）
        Map<String, Object> data = new HashMap<>(); // 结果Map
        data.put("city", city); // 城市名
        data.put("result", weatherResult); // 天气结果
        return Result.success(data); // 返回
    }

    /**
     * 计算器接口
     * 接口路径：GET /api/tools/calculator
     *
     * @param expression 数学表达式
     * @return Result<Map> 包含expression和result
     */
    @GetMapping("/calculator") // GET映射
    @Operation(summary = "计算器", description = "直接调用计算工具执行数学表达式计算") // API描述
    @ApiResponses(value = { // 响应码
            @ApiResponse(responseCode = "200", description = "计算成功"), // 200
            @ApiResponse(responseCode = "400", description = "参数校验失败"), // 400
            @ApiResponse(responseCode = "401", description = "未登录或Token无效") // 401
    })
    public Result<Map<String, Object>> calculator( // 计算器方法
            @Parameter(description = "数学表达式，如：2 + 3 * 4、(100+50)*0.8、Math.sqrt(16)+Math.pow(2,3)", required = true) // 参数描述
            @RequestParam String expression) { // 绑定expression参数
        log.info("计算器请求: expression={}", expression); // 业务日志
        String calcResult = calculatorTool.calculateExpression(expression); // 直接调用计算器工具
        Map<String, Object> data = new HashMap<>(); // 结果Map
        data.put("expression", expression); // 原始表达式
        data.put("result", calcResult); // 计算结果
        return Result.success(data); // 返回
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
} // ToolsController类结束
