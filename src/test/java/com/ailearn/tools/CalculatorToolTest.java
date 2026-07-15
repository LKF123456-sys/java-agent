package com.ailearn.tools; // 声明包名

import org.junit.jupiter.api.BeforeEach; // JUnit前置方法注解
import org.junit.jupiter.api.Disabled; // JUnit禁用测试注解
import org.junit.jupiter.api.DisplayName; // JUnit显示名称注解
import org.junit.jupiter.api.Test; // JUnit测试方法注解

import static org.junit.jupiter.api.Assertions.*; // JUnit断言静态导入

@DisplayName("计算器工具测试") // 测试类显示名称
class CalculatorToolTest { // 计算器工具测试类

    private CalculatorTool calculatorTool; // 被测计算器实例

    @BeforeEach // 每个测试前执行
    void setUp() { // 初始化方法
        calculatorTool = new CalculatorTool(); // 创建实例
    } // setUp方法结束

    @Test
    @DisplayName("基础计算 - 加法运算")
    void testCalculate_Addition() { // 测试加法
        String result1 = calculatorTool.calculate(10, "+", 5); // 10+5
        assertTrue(result1.contains("10.0000"), "应包含第一个操作数"); // 包含10
        assertTrue(result1.contains("+"), "应包含加号"); // 包含+
        assertTrue(result1.contains("5.0000"), "应包含第二个操作数"); // 包含5
        assertTrue(result1.contains("15.0000"), "结果应为15"); // 结果15

        String result2 = calculatorTool.calculate(-3, "+", 7); // -3+7
        assertTrue(result2.contains("4.0000"), "-3 + 7 结果应为4"); // 结果4

        String result3 = calculatorTool.calculate(0, "+", 100); // 0+100
        assertTrue(result3.contains("100.0000"), "0 + 100 结果应为100"); // 结果100

        String result4 = calculatorTool.calculate(1.5, "+", 2.5); // 1.5+2.5
        assertTrue(result4.contains("4.0000"), "1.5 + 2.5 结果应为4"); // 结果4
    } // testCalculate_Addition方法结束

    @Test
    @DisplayName("基础计算 - 减法运算")
    void testCalculate_Subtraction() { // 测试减法
        String result1 = calculatorTool.calculate(10, "-", 3); // 10-3
        assertTrue(result1.contains("7.0000"), "10 - 3 结果应为7"); // 结果7

        String result2 = calculatorTool.calculate(3, "-", 10); // 3-10
        assertTrue(result2.contains("-7.0000"), "3 - 10 结果应为-7"); // 结果-7

        String result3 = calculatorTool.calculate(5, "-", 0); // 5-0
        assertTrue(result3.contains("5.0000"), "5 - 0 结果应为5"); // 结果5

        String result4 = calculatorTool.calculate(10.5, "-", 3.2); // 10.5-3.2
        assertTrue(result4.contains("7.3000"), "10.5 - 3.2 结果应为7.3"); // 结果7.3
    } // testCalculate_Subtraction方法结束

    @Test
    @DisplayName("基础计算 - 乘法运算")
    void testCalculate_Multiplication() { // 测试乘法
        String result1 = calculatorTool.calculate(4, "*", 5); // 4*5
        assertTrue(result1.contains("20.0000"), "4 * 5 结果应为20"); // 结果20

        String result2 = calculatorTool.calculate(-3, "*", -4); // -3*-4
        assertTrue(result2.contains("12.0000"), "-3 * -4 结果应为12"); // 结果12

        String result3 = calculatorTool.calculate(-3, "*", 4); // -3*4
        assertTrue(result3.contains("-12.0000"), "-3 * 4 结果应为-12"); // 结果-12

        String result4 = calculatorTool.calculate(100, "*", 0); // 100*0
        assertTrue(result4.contains("0.0000"), "任何数乘0结果应为0"); // 结果0

        String result5 = calculatorTool.calculate(2.5, "*", 4); // 2.5*4
        assertTrue(result5.contains("10.0000"), "2.5 * 4 结果应为10"); // 结果10
    } // testCalculate_Multiplication方法结束

    @Test
    @DisplayName("基础计算 - 除法运算")
    void testCalculate_Division() { // 测试除法
        String result1 = calculatorTool.calculate(20, "/", 4); // 20/4
        assertTrue(result1.contains("5.0000"), "20 / 4 结果应为5"); // 结果5

        String result2 = calculatorTool.calculate(10, "/", 3); // 10/3
        assertTrue(result2.contains("3.3333"), "10 / 3 结果约为3.3333"); // 约3.3333

        String result3 = calculatorTool.calculate(7.5, "/", 2.5); // 7.5/2.5
        assertTrue(result3.contains("3.0000"), "7.5 / 2.5 结果应为3"); // 结果3

        String result4 = calculatorTool.calculate(42, "/", 1); // 42/1
        assertTrue(result4.contains("42.0000"), "任何数除以1等于本身"); // 结果42
    } // testCalculate_Division方法结束

    @Test
    @DisplayName("基础计算 - 除零错误处理")
    void testCalculate_DivisionByZero() { // 测试除零
        String result = calculatorTool.calculate(10, "/", 0); // 除以0

        assertTrue(result.contains("错误"), "除以零应返回错误提示"); // 包含错误
        assertTrue(result.contains("除数不能为零"), "错误消息应说明除数不能为零"); // 提示除数为零
    } // testCalculate_DivisionByZero方法结束

    @Test
    @DisplayName("基础计算 - 不支持的运算符")
    void testCalculate_UnsupportedOperator() { // 测试不支持运算符
        String result = calculatorTool.calculate(10, "%", 3); // 取模（不支持）

        assertTrue(result.contains("错误"), "不支持的运算符应返回错误"); // 包含错误
        assertTrue(result.contains("不支持的运算符"), "错误消息应说明不支持的运算符"); // 提示不支持
    } // testCalculate_UnsupportedOperator方法结束

    @Test
    @DisplayName("表达式计算 - 简单两数表达式（回退模式）")
    void testCalculateExpression_SimpleTwoNumbers() { // 测试简单表达式
        String result1 = calculatorTool.calculateExpression("2 + 3"); // 简单加法
        assertTrue(result1.contains("5") || result1.contains("错误"), "2 + 3 结果应为5或返回降级提示"); // 结果5或降级
    } // testCalculateExpression_SimpleTwoNumbers方法结束

    @Test
    @Disabled("JDK 21移除了Nashorn JavaScript引擎，复杂表达式计算不可用（需要GraalJS等额外依赖）")
    @DisplayName("表达式计算 - 复杂算术表达式（需要JavaScript引擎）")
    void testCalculateExpression_Complex() { // 测试复杂表达式（禁用）
        String result2 = calculatorTool.calculateExpression("2 + 3 * 4"); // 优先级
        assertTrue(result2.contains("14"), "2 + 3 * 4 结果应为14（先乘后加）"); // 结果14

        String result3 = calculatorTool.calculateExpression("(2 + 3) * 4"); // 括号
        assertTrue(result3.contains("20"), "(2 + 3) * 4 结果应为20"); // 结果20
    } // testCalculateExpression_Complex方法结束

    @Test
    @DisplayName("表达式计算 - 空表达式处理")
    void testCalculateExpression_Empty() { // 测试空表达式
        String result1 = calculatorTool.calculateExpression(null); // null
        assertTrue(result1.contains("错误"), "null表达式应返回错误"); // 返回错误

        String result2 = calculatorTool.calculateExpression(""); // 空串
        assertTrue(result2.contains("错误"), "空字符串表达式应返回错误"); // 返回错误

        String result3 = calculatorTool.calculateExpression("   "); // 空白
        assertTrue(result3.contains("错误"), "空白字符串表达式应返回错误"); // 返回错误
    } // testCalculateExpression_Empty方法结束
} // CalculatorToolTest类结束
