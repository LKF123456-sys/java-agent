package com.ailearn.tools;

import com.ailearn.common.Result;
import com.ailearn.security.UserPrincipal;
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

import java.util.HashMap;
import java.util.Map;

/**
 * 工具测试控制器
 * 提供直接测试系统工具函数的接口，无需通过AI Agent即可直接调用天气查询、
 * 数学计算等工具，方便开发调试和功能验证
 *
 * @author AiLearn Platform
 */
@Slf4j
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
@Tag(name = "工具测试", description = "直接测试工具函数")
public class ToolsController {

    /**
     * 天气查询工具，提供城市天气信息查询功能
     */
    private final WeatherTool weatherTool;

    /**
     * 数学计算工具，提供基础运算和复杂表达式计算功能
     */
    private final CalculatorTool calculatorTool;

    /**
     * 天气查询接口
     * 直接调用天气工具查询指定城市的当前天气信息，包括天气状况、温度、湿度等
     * 目前返回模拟天气数据（无需真实天气API Key）
     * 接口路径：GET /api/tools/weather
     *
     * @param city 要查询天气的城市名称，如：北京、上海、深圳、成都等，必填
     * @return Result<Map> 查询结果，包含：
     *         - city: String 查询的城市名称
     *         - result: String 格式化的天气信息字符串
     */
    @GetMapping("/weather")
    @Operation(summary = "天气查询", description = "直接调用天气工具查询指定城市的当前天气信息，返回天气状况、温度、湿度等")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（城市名为空）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<Map<String, Object>> weather(
            @Parameter(description = "城市名称，如：北京、上海、深圳、成都等", required = true)
            @RequestParam String city) {
        log.info("天气查询请求: city={}", city);
        String weatherResult = weatherTool.getWeather(city, null);
        Map<String, Object> data = new HashMap<>();
        data.put("city", city);
        data.put("result", weatherResult);
        return Result.success(data);
    }

    /**
     * 计算器接口
     * 直接调用计算工具执行数学表达式计算，支持加减乘除、括号、幂运算、
     * 三角函数、开方等复杂数学运算
     * 接口路径：GET /api/tools/calculator
     *
     * @param expression 要计算的数学表达式，如："2 + 3 * 4"、"(100 + 50) * 0.8"、"Math.sqrt(16) + Math.pow(2, 3)"等，必填
     * @return Result<Map> 计算结果，包含：
     *         - expression: String 原始表达式
     *         - result: String 计算结果字符串
     */
    @GetMapping("/calculator")
    @Operation(summary = "计算器", description = "直接调用计算工具执行数学表达式计算，支持加减乘除、括号、幂运算、三角函数、开方等")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "计算成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（表达式为空或语法错误）"),
            @ApiResponse(responseCode = "401", description = "未登录或Token无效")
    })
    public Result<Map<String, Object>> calculator(
            @Parameter(description = "数学表达式，如：2 + 3 * 4、(100+50)*0.8、Math.sqrt(16)+Math.pow(2,3)", required = true)
            @RequestParam String expression) {
        log.info("计算器请求: expression={}", expression);
        String calcResult = calculatorTool.calculateExpression(expression);
        Map<String, Object> data = new HashMap<>();
        data.put("expression", expression);
        data.put("result", calcResult);
        return Result.success(data);
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
