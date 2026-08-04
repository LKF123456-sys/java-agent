package com.ailearn.tools; // 声明包名，tools包存放AI可调用的工具类

import lombok.extern.slf4j.Slf4j; // Lombok注解，自动生成log对象
import org.springframework.ai.tool.annotation.Tool; // Spring AI注解，标记方法为可被大模型调用的工具
import org.springframework.ai.tool.annotation.ToolParam; // Spring AI注解，描述工具参数
import org.springframework.stereotype.Component; // Spring注解，注册为容器管理的Bean

import javax.script.ScriptEngine; // JSR223脚本引擎接口（JDK自带，JDK15+已移除Nashorn）
import javax.script.ScriptEngineManager; // 脚本引擎管理器，按名称查找引擎
import javax.script.ScriptException; // 脚本执行异常

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
@Slf4j // 自动注入log
@Component // 注册为Spring Bean
public class CalculatorTool { // 计算器工具类定义

    /**
     * JavaScript脚本引擎，用于数学表达式求值
     */
    private final ScriptEngine scriptEngine; // 脚本引擎实例，可能为null（引擎不可用时）

    /**
     * 构造方法：初始化脚本引擎
     * 尝试加载JavaScript引擎用于表达式计算，如果不可用则标记为null，
     * 此时仅支持基础的二元运算。
     */
    public CalculatorTool() { // 无参构造（Spring通过@Component默认走无参构造实例化）
        ScriptEngine engine = null; // 临时变量，先置null
        try { // 尝试加载引擎
            ScriptEngineManager manager = new ScriptEngineManager(); // 创建引擎管理器
            engine = manager.getEngineByName("JavaScript"); // 按名称查找JavaScript引擎
            if (engine != null) { // 引擎加载成功
                log.info("CalculatorTool初始化：JavaScript脚本引擎加载成功"); // 信息日志
            } else { // 引擎不可用（JDK15+无内置Nashorn，需GraalJS）
                log.warn("CalculatorTool初始化：JavaScript脚本引擎不可用，将使用基础计算模式"); // 警告日志
            }
        } catch (Exception e) { // 加载过程异常
            log.warn("CalculatorTool初始化：脚本引擎加载失败", e); // 警告日志
        }
        this.scriptEngine = engine; // 赋值给final字段
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
    @Tool(description = "执行基础数学运算：加法、减法、乘法、除法。当需要简单的两数计算时使用此工具。") // 工具说明
    public String calculate( // 基础两数计算方法
            @ToolParam(description = "第一个数字，支持整数和小数") double a, // 参数a
            @ToolParam(description = "运算符，只能是：+（加）、-（减）、*（乘）、/（除）") String operator, // 参数operator
            @ToolParam(description = "第二个数字，支持整数和小数") double b) { // 参数b
        log.info("基础计算工具被调用: {} {} {}", a, operator, b); // 业务日志
        double result; // 计算结果变量
        try { // 尝试计算
            result = switch (operator) { // switch表达式按运算符分支
                case "+" -> a + b; // 加法
                case "-" -> a - b; // 减法
                case "*" -> a * b; // 乘法
                case "/" -> { // 除法
                    if (b == 0) { // 除数为0
                        throw new ArithmeticException("除数不能为零"); // 抛算术异常
                    }
                    yield a / b; // switch表达式分支返回值
                }
                default -> throw new IllegalArgumentException("不支持的运算符: " + operator + "，仅支持 +、-、*、/"); // 未知运算符
            };
        } catch (ArithmeticException e) { // 算术异常（除零）
            log.warn("计算错误: {}", e.getMessage()); // 警告日志
            return String.format("计算错误：%s", e.getMessage()); // 返回错误信息
        } catch (IllegalArgumentException e) { // 参数异常（未知运算符）
            log.warn("参数错误: {}", e.getMessage()); // 警告日志
            return String.format("参数错误：%s", e.getMessage()); // 返回错误信息
        }
        String resultStr = String.format("计算结果：%.4f %s %.4f = %.4f", a, operator, b, result); // 格式化结果（4位小数）
        log.info("基础计算结果: {}", resultStr); // 信息日志
        return resultStr; // 返回结果字符串
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
    @Tool(description = "计算复杂数学表达式，支持加减乘除、括号、幂运算、三角函数、开方等。当需要计算复杂公式或多个数字运算时使用此工具，例如：(2+3)*4、sqrt(16)+pow(2,3)。") // 工具说明
    public String calculateExpression( // 复杂表达式计算方法
            @ToolParam(description = "数学表达式字符串，例如：2 + 3 * 4、(100 + 50) * 0.8、Math.sqrt(16) + Math.pow(2, 3)") String expression) { // 表达式参数
        log.info("表达式计算工具被调用: expression={}", expression); // 业务日志
        if (expression == null || expression.isBlank()) { // 表达式为空
            return "计算错误：表达式不能为空"; // 返回错误
        }

        String sanitized = expression.trim(); // 去首尾空白
        if (scriptEngine == null) { // 脚本引擎不可用
            return calculateBasicExpressionFallback(sanitized); // 走降级基础计算
        }

        if (!isExpressionSafe(sanitized)) { // 安全校验不通过
            log.warn("表达式包含不安全字符: {}", sanitized); // 警告日志
            return "计算错误：表达式包含不允许的字符，仅支持数学运算和Math函数"; // 返回错误
        }

        try { // 尝试执行表达式
            Object result = scriptEngine.eval(sanitized); // 用JS引擎求值
            if (result instanceof Number num) { // 结果是数字（JDK16+模式匹配）
                String resultStr = String.format("表达式「%s」的计算结果：%.6f", sanitized, num.doubleValue()); // 格式化为6位小数
                log.info("表达式计算结果: {}", resultStr); // 信息日志
                return resultStr; // 返回结果
            } else { // 结果非数字
                String resultStr = String.format("表达式「%s」的计算结果：%s", sanitized, result); // 直接转字符串
                log.info("表达式计算结果: {}", resultStr); // 信息日志
                return resultStr; // 返回结果
            }
        } catch (ScriptException e) { // 脚本执行异常
            log.warn("表达式计算失败: {}", e.getMessage()); // 警告日志
            return String.format("计算错误：表达式语法错误 - %s", e.getMessage()); // 返回错误
        }
    }

    /**
     * 检查表达式是否安全（仅包含数学运算允许的字符）
     * 防止任意代码执行的安全校验
     *
     * @param expr 待检查的表达式
     * @return boolean true表示安全，false表示可能包含恶意代码
     */
    private boolean isExpressionSafe(String expr) { // 私有安全校验方法
        String lowerExpr = expr.toLowerCase(); // 转小写统一比较
        return !lowerExpr.contains("java") // 不含java关键字
                && !lowerExpr.contains("exec") // 不含exec
                && !lowerExpr.contains("eval") // 不含eval
                && !lowerExpr.contains("process") // 不含process
                && !lowerExpr.contains("runtime") // 不含runtime
                && !lowerExpr.contains("system") // 不含system
                && !lowerExpr.contains("import") // 不含import
                && !lowerExpr.contains("class") // 不含class
                && !lowerExpr.contains("function") // 不含function
                && !lowerExpr.contains("{") // 不含左大括号（防语句块）
                && !lowerExpr.contains("}") // 不含右大括号
                && !lowerExpr.contains(";"); // 不含分号（防多语句）
    }

    /**
     * 基础表达式计算降级方法（脚本引擎不可用时使用）
     * 仅支持简单的加减乘除，不支持函数和复杂表达式
     *
     * @param expr 数学表达式
     * @return String 计算结果或错误提示
     */
    private String calculateBasicExpressionFallback(String expr) { // 私有降级方法
        try { // 尝试解析
            String[] parts = expr.split("\\s*([+\\-*/])\\s*"); // 按运算符切分出两个操作数
            if (parts.length == 2) { // 恰好两部分
                String op = expr.replaceAll("[0-9.\\s]", ""); // 删除数字和空白，剩下运算符
                if (op.length() == 1) { // 单字符运算符
                    double a = Double.parseDouble(parts[0].trim()); // 解析第一个数
                    double b = Double.parseDouble(parts[1].trim()); // 解析第二个数
                    return calculate(a, op, b); // 复用calculate方法计算
                }
            }
            return "计算错误：脚本引擎不可用，仅支持简单两数运算，请使用 calculate 方法"; // 无法降级时返回错误
        } catch (Exception e) { // 解析异常
            return "计算错误：" + e.getMessage(); // 返回错误信息
        }
    }
} // CalculatorTool类结束
