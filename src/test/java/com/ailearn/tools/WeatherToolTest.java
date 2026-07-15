package com.ailearn.tools; // 声明包名

import org.junit.jupiter.api.BeforeEach; // JUnit前置方法注解
import org.junit.jupiter.api.Disabled; // JUnit禁用测试注解
import org.junit.jupiter.api.DisplayName; // JUnit显示名称注解
import org.junit.jupiter.api.Test; // JUnit测试方法注解

import static org.junit.jupiter.api.Assertions.*; // JUnit断言静态导入

@DisplayName("天气工具测试") // 测试类显示名称
class WeatherToolTest { // 天气工具测试类

    private WeatherTool weatherTool; // 被测天气工具实例

    @BeforeEach // 每个测试前执行
    void setUp() { // 初始化方法
        weatherTool = new WeatherTool(); // 创建实例
    } // setUp方法结束

    @Test
    @DisplayName("实例化测试 - WeatherTool能正常创建")
    void testInstantiation() { // 测试实例化
        assertNotNull(weatherTool, "WeatherTool实例不应为空"); // 实例不为空
    } // testInstantiation方法结束

    @Test
    @DisplayName("天气代码映射 - 工具能正常构造（包含天气代码映射）")
    void testWeatherCodeMappingExists() { // 测试天气代码映射
        assertNotNull(weatherTool, "WeatherTool构造应成功，天气代码映射表应已初始化"); // 构造成功
    } // testWeatherCodeMappingExists方法结束

    @Test
    @Disabled("需要网络连接和外部API，单元测试中禁用")
    @DisplayName("真实API调用 - 查询北京天气（禁用，需要网络）")
    void testGetWeather_Beijing() { // 测试查询北京天气（禁用）
        String result = weatherTool.getWeather("北京", "celsius"); // 查询北京

        assertNotNull(result, "天气查询结果不应为空"); // 结果不为空
        assertTrue(result.contains("北京"), "结果应包含北京"); // 包含北京
    } // testGetWeather_Beijing方法结束

    @Test
    @Disabled("需要网络连接和外部API，单元测试中禁用")
    @DisplayName("真实API调用 - 查询不存在城市（禁用，需要网络）")
    void testGetWeather_NonexistentCity() { // 测试不存在城市（禁用）
        String result = weatherTool.getWeather("不存在的城市XYZ123", null); // 查询不存在城市

        assertNotNull(result, "结果不应为空"); // 结果不为空
        assertTrue(result.contains("抱歉") || result.contains("未能找到"), "不存在的城市应返回未找到提示"); // 返回未找到
    } // testGetWeather_NonexistentCity方法结束

    @Test
    @Disabled("需要网络连接和外部API，单元测试中禁用")
    @DisplayName("真实API调用 - 查询上海天气预报（禁用，需要网络）")
    void testGetWeatherForecast_Shanghai() { // 测试上海预报（禁用）
        String result = weatherTool.getWeatherForecast("上海"); // 查询上海预报

        assertNotNull(result, "天气预报结果不应为空"); // 结果不为空
        assertTrue(result.contains("上海"), "结果应包含上海"); // 包含上海
        assertTrue(result.contains("明天") || result.contains("后天"), "预报结果应包含明天/后天等标签"); // 包含预报标签
    } // testGetWeatherForecast_Shanghai方法结束

    @Test
    @Disabled("需要网络连接和外部API，单元测试中禁用")
    @DisplayName("真实API调用 - 华氏度单位查询（禁用，需要网络）")
    void testGetWeather_FahrenheitUnit() { // 测试华氏度（禁用）
        String result = weatherTool.getWeather("深圳", "fahrenheit"); // 华氏度查询深圳

        assertNotNull(result); // 结果不为空
        assertTrue(result.contains("°F"), "使用华氏度时结果应包含°F符号"); // 包含华氏度符号
    } // testGetWeather_FahrenheitUnit方法结束
} // WeatherToolTest类结束
