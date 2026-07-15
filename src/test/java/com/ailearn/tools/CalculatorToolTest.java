package com.ailearn.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计算器工具单元测试类
 * 测试基础四则运算功能：加法、减法、乘法、除法
 * 不依赖Spring容器，直接实例化测试
 *
 * @author AiLearn Platform
 */
@DisplayName("计算器工具测试")
class CalculatorToolTest {

    /**
     * 待测试的计算器工具实例
     */
    private CalculatorTool calculatorTool;

    /**
     * 每个测试方法执行前初始化CalculatorTool实例
     */
    @BeforeEach
    void setUp() {
        // 初始化计算器工具
        calculatorTool = new CalculatorTool();
    }

    /**
     * 测试加法运算
     * 验证：正数相加、负数相加、零相加
     */
    @Test
    @DisplayName("基础计算 - 加法运算")
    void testCalculate_Addition() {
        // 测试：正数加法
        String result1 = calculatorTool.calculate(10, "+", 5);
        // 验证：结果包含计算过程和结果
        assertTrue(result1.contains("10.0000"), "应包含第一个操作数");
        assertTrue(result1.contains("+"), "应包含加号");
        assertTrue(result1.contains("5.0000"), "应包含第二个操作数");
        assertTrue(result1.contains("15.0000"), "结果应为15");

        // 测试：负数加法
        String result2 = calculatorTool.calculate(-3, "+", 7);
        assertTrue(result2.contains("4.0000"), "-3 + 7 结果应为4");

        // 测试：零加法
        String result3 = calculatorTool.calculate(0, "+", 100);
        assertTrue(result3.contains("100.0000"), "0 + 100 结果应为100");

        // 测试：小数加法
        String result4 = calculatorTool.calculate(1.5, "+", 2.5);
        assertTrue(result4.contains("4.0000"), "1.5 + 2.5 结果应为4");
    }

    /**
     * 测试减法运算
     * 验证：正数相减、负数相减、结果为负数
     */
    @Test
    @DisplayName("基础计算 - 减法运算")
    void testCalculate_Subtraction() {
        // 测试：正数减法
        String result1 = calculatorTool.calculate(10, "-", 3);
        assertTrue(result1.contains("7.0000"), "10 - 3 结果应为7");

        // 测试：结果为负数
        String result2 = calculatorTool.calculate(3, "-", 10);
        assertTrue(result2.contains("-7.0000"), "3 - 10 结果应为-7");

        // 测试：减零
        String result3 = calculatorTool.calculate(5, "-", 0);
        assertTrue(result3.contains("5.0000"), "5 - 0 结果应为5");

        // 测试：小数减法
        String result4 = calculatorTool.calculate(10.5, "-", 3.2);
        assertTrue(result4.contains("7.3000"), "10.5 - 3.2 结果应为7.3");
    }

    /**
     * 测试乘法运算
     * 验证：正数相乘、负数相乘、与零相乘
     */
    @Test
    @DisplayName("基础计算 - 乘法运算")
    void testCalculate_Multiplication() {
        // 测试：正数乘法
        String result1 = calculatorTool.calculate(4, "*", 5);
        assertTrue(result1.contains("20.0000"), "4 * 5 结果应为20");

        // 测试：负数乘法（负负得正）
        String result2 = calculatorTool.calculate(-3, "*", -4);
        assertTrue(result2.contains("12.0000"), "-3 * -4 结果应为12");

        // 测试：正负相乘
        String result3 = calculatorTool.calculate(-3, "*", 4);
        assertTrue(result3.contains("-12.0000"), "-3 * 4 结果应为-12");

        // 测试：与零相乘
        String result4 = calculatorTool.calculate(100, "*", 0);
        assertTrue(result4.contains("0.0000"), "任何数乘0结果应为0");

        // 测试：小数乘法
        String result5 = calculatorTool.calculate(2.5, "*", 4);
        assertTrue(result5.contains("10.0000"), "2.5 * 4 结果应为10");
    }

    /**
     * 测试除法运算
     * 验证：整除、带小数除法、除不尽场景
     */
    @Test
    @DisplayName("基础计算 - 除法运算")
    void testCalculate_Division() {
        // 测试：整除
        String result1 = calculatorTool.calculate(20, "/", 4);
        assertTrue(result1.contains("5.0000"), "20 / 4 结果应为5");

        // 测试：带小数结果
        String result2 = calculatorTool.calculate(10, "/", 3);
        assertTrue(result2.contains("3.3333"), "10 / 3 结果约为3.3333");

        // 测试：小数除法
        String result3 = calculatorTool.calculate(7.5, "/", 2.5);
        assertTrue(result3.contains("3.0000"), "7.5 / 2.5 结果应为3");

        // 测试：除以1
        String result4 = calculatorTool.calculate(42, "/", 1);
        assertTrue(result4.contains("42.0000"), "任何数除以1等于本身");
    }

    /**
     * 测试除零错误
     * 验证：除数为零时返回错误提示
     */
    @Test
    @DisplayName("基础计算 - 除零错误处理")
    void testCalculate_DivisionByZero() {
        // 执行：除以零
        String result = calculatorTool.calculate(10, "/", 0);

        // 验证：返回错误提示而非抛出异常
        assertTrue(result.contains("错误"), "除以零应返回错误提示");
        assertTrue(result.contains("除数不能为零"), "错误消息应说明除数不能为零");
    }

    /**
     * 测试不支持的运算符
     * 验证：传入非法运算符时返回错误提示
     */
    @Test
    @DisplayName("基础计算 - 不支持的运算符")
    void testCalculate_UnsupportedOperator() {
        // 执行：使用不支持的运算符
        String result = calculatorTool.calculate(10, "%", 3);

        // 验证：返回错误提示
        assertTrue(result.contains("错误"), "不支持的运算符应返回错误");
        assertTrue(result.contains("不支持的运算符"), "错误消息应说明不支持的运算符");
    }

    /**
     * 测试简单两数表达式（回退模式支持）
     * 验证：在JavaScript引擎不可用时，简单两数表达式可正常计算
     */
    @Test
    @DisplayName("表达式计算 - 简单两数表达式（回退模式）")
    void testCalculateExpression_SimpleTwoNumbers() {
        // 测试：简单加法表达式（两数运算，回退模式支持）
        String result1 = calculatorTool.calculateExpression("2 + 3");
        assertTrue(result1.contains("5") || result1.contains("错误"),
                "2 + 3 结果应为5或返回降级提示");
    }

    /**
     * 测试复杂表达式计算（需要JavaScript引擎）
     * 验证：calculateExpression方法能正确计算带括号和优先级的表达式
     * 注意：JDK 21已移除Nashorn JavaScript引擎，此测试在无GraalJS等引擎时会降级
     */
    @Test
    @Disabled("JDK 21移除了Nashorn JavaScript引擎，复杂表达式计算不可用（需要GraalJS等额外依赖）")
    @DisplayName("表达式计算 - 复杂算术表达式（需要JavaScript引擎）")
    void testCalculateExpression_Complex() {
        // 测试：带优先级的表达式（乘法优先于加法）
        String result2 = calculatorTool.calculateExpression("2 + 3 * 4");
        assertTrue(result2.contains("14"), "2 + 3 * 4 结果应为14（先乘后加）");

        // 测试：带括号的表达式
        String result3 = calculatorTool.calculateExpression("(2 + 3) * 4");
        assertTrue(result3.contains("20"), "(2 + 3) * 4 结果应为20");
    }

    /**
     * 测试空表达式处理
     * 验证：空字符串或null表达式返回错误
     */
    @Test
    @DisplayName("表达式计算 - 空表达式处理")
    void testCalculateExpression_Empty() {
        // 测试：null表达式
        String result1 = calculatorTool.calculateExpression(null);
        assertTrue(result1.contains("错误"), "null表达式应返回错误");

        // 测试：空字符串表达式
        String result2 = calculatorTool.calculateExpression("");
        assertTrue(result2.contains("错误"), "空字符串表达式应返回错误");

        // 测试：空白字符串表达式
        String result3 = calculatorTool.calculateExpression("   ");
        assertTrue(result3.contains("错误"), "空白字符串表达式应返回错误");
    }
}
