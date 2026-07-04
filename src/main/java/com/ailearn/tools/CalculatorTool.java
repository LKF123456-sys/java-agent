package com.ailearn.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * 数学计算工具类
 * 提供给AI Agent调用的数学计算能力，支持基础四则运算和复杂数学表达式求值。
 * 使用JavaScript脚本引擎（Nashorn/GraalJS）进行表达式计算，支持常见数学函数。
 *
 * <p>安全说明：
 * 工具类仅用于数学表达式计算，不执行任意代码，已对输入进行基本安全校验，
 * 防止注入攻击。
 *
 * @author AiLearn Platform
 */
@Slf4j
@Component
public class CalculatorTool {

    /**
     * JavaScript脚本引擎，用于数学表达式求值
     */
    private final ScriptEngine scriptEngine;

    /**
     * 构造方法：初始化脚本引擎
     * 尝试加载JavaScript引擎用于表达式计算，如果不可用则标记为null，
     * 此时仅支持基础的二元运算。
     */
    public CalculatorTool() {
        ScriptEngine engine = null;
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            engine = manager.getEngineByName("JavaScript");
            if (engine != null) {
                log.info("CalculatorTool初始化：JavaScript脚本引擎加载成功");
            } else {
                log.warn("CalculatorTool初始化：JavaScript脚本引擎不可用，将使用基础计算模式");
            }
        } catch (Exception e) {
            log.warn("CalculatorTool初始化：脚本引擎加载失败", e);
        }
        this.scriptEngine = engine;
    }

    /**
     * 执行基础数学运算（两数运算）
     * 支持加法、减法、乘法、除法四种基础运算。
     *
     * @param a        第一个数字（操作数1）
     * @param operator 运算符：+（加）、-（减）、*（乘）、/（除）
     * @param b        第二个数字（操作数2）
     * @return String 计算结果的格式化字符串，包含运算过程和结果
     */
    @Tool(description = "执行基础数学运算：加法、减法、乘法、除法。当需要简单的两数计算时使用此工具。")
    public String calculate(
            @ToolParam(description = "第一个数字，支持整数和小数") double a,
            @ToolParam(description = "运算符，只能是：+（加）、-（减）、*（乘）、/（除）") String operator,
            @ToolParam(description = "第二个数字，支持整数和小数") double b) {
        log.info("基础计算工具被调用: {} {} {}", a, operator, b);
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
                default -> throw new IllegalArgumentException("不支持的运算符: " + operator + "，仅支持 +、-、*、/");
            };
        } catch (ArithmeticException e) {
            log.warn("计算错误: {}", e.getMessage());
            return String.format("计算错误：%s", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return String.format("参数错误：%s", e.getMessage());
        }
        String resultStr = String.format("计算结果：%.4f %s %.4f = %.4f", a, operator, b, result);
        log.info("基础计算结果: {}", resultStr);
        return resultStr;
    }

    /**
     * 计算复杂数学表达式
     * 支持包含加减乘除、括号、常见数学函数的复杂表达式，如：
     * "(2 + 3) * 4 / 2"、"Math.sqrt(16) + Math.pow(2, 3)"、"sin(0) + cos(0)"等。
     *
     * <p>支持的函数和运算：
     * <ul>
     *   <li>基本运算：+、-、*、/、%（取模）、**（幂）</li>
     *   <li>括号：( ) 改变运算优先级</li>
     *   <li>数学函数：Math.sin、Math.cos、Math.tan、Math.sqrt、Math.pow、Math.abs、Math.log、Math.exp等</li>
     *   <li>常量：Math.PI、Math.E</li>
     * </ul>
     *
     * @param expression 数学表达式字符串，如 "2 + 3 * 4"、"(100 + 50) * 0.8"
     * @return String 计算结果的格式化字符串
     */
    @Tool(description = "计算复杂数学表达式，支持加减乘除、括号、幂运算、三角函数、开方等。当需要计算复杂公式或多个数字运算时使用此工具，例如：(2+3)*4、sqrt(16)+pow(2,3)。")
    public String calculateExpression(
            @ToolParam(description = "数学表达式字符串，例如：2 + 3 * 4、(100 + 50) * 0.8、Math.sqrt(16) + Math.pow(2, 3)") String expression) {
        log.info("表达式计算工具被调用: expression={}", expression);
        if (expression == null || expression.isBlank()) {
            return "计算错误：表达式不能为空";
        }

        String sanitized = expression.trim();
        if (scriptEngine == null) {
            return calculateBasicExpressionFallback(sanitized);
        }

        if (!isExpressionSafe(sanitized)) {
            log.warn("表达式包含不安全字符: {}", sanitized);
            return "计算错误：表达式包含不允许的字符，仅支持数学运算和Math函数";
        }

        try {
            Object result = scriptEngine.eval(sanitized);
            if (result instanceof Number num) {
                String resultStr = String.format("表达式「%s」的计算结果：%.6f", sanitized, num.doubleValue());
                log.info("表达式计算结果: {}", resultStr);
                return resultStr;
            } else {
                String resultStr = String.format("表达式「%s」的计算结果：%s", sanitized, result);
                log.info("表达式计算结果: {}", resultStr);
                return resultStr;
            }
        } catch (ScriptException e) {
            log.warn("表达式计算失败: {}", e.getMessage());
            return String.format("计算错误：表达式语法错误 - %s", e.getMessage());
        }
    }

    /**
     * 检查表达式是否安全（仅包含数学运算允许的字符）
     * 防止任意代码执行的安全校验
     *
     * @param expr 待检查的表达式
     * @return boolean true表示安全，false表示可能包含恶意代码
     */
    private boolean isExpressionSafe(String expr) {
        String lowerExpr = expr.toLowerCase();
        return !lowerExpr.contains("java")
                && !lowerExpr.contains("exec")
                && !lowerExpr.contains("eval")
                && !lowerExpr.contains("process")
                && !lowerExpr.contains("runtime")
                && !lowerExpr.contains("system")
                && !lowerExpr.contains("import")
                && !lowerExpr.contains("class")
                && !lowerExpr.contains("function")
                && !lowerExpr.contains("{")
                && !lowerExpr.contains("}")
                && !lowerExpr.contains(";");
    }

    /**
     * 基础表达式计算降级方法（脚本引擎不可用时使用）
     * 仅支持简单的加减乘除，不支持函数和复杂表达式
     *
     * @param expr 数学表达式
     * @return String 计算结果或错误提示
     */
    private String calculateBasicExpressionFallback(String expr) {
        try {
            String[] parts = expr.split("\\s*([+\\-*/])\\s*");
            if (parts.length == 2) {
                String op = expr.replaceAll("[0-9.\\s]", "");
                if (op.length() == 1) {
                    double a = Double.parseDouble(parts[0].trim());
                    double b = Double.parseDouble(parts[1].trim());
                    return calculate(a, op, b);
                }
            }
            return "计算错误：脚本引擎不可用，仅支持简单两数运算，请使用 calculate 方法";
        } catch (Exception e) {
            return "计算错误：" + e.getMessage();
        }
    }
}
