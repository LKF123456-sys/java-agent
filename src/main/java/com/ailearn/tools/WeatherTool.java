package com.ailearn.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 天气查询工具类
 * 提供给AI Agent调用的天气查询功能，目前返回模拟天气数据（因为真实天气API需要API Key）。
 * 支持查询主要城市的天气状况、温度和湿度信息。
 *
 * <p>使用Spring AI 1.0.0的@Tool注解方式声明工具函数，
 * 框架会自动将方法签名和@ToolParam注解转换为Function Calling的JSON Schema，
 * 大模型可以自主判断何时调用此工具获取天气信息。
 *
 * @author AiLearn Platform
 */
@Slf4j
@Component
public class WeatherTool {

    /**
     * 获取指定城市的当前天气信息
     * 根据城市名称返回模拟的天气数据，包含天气状况、温度和湿度。
     * 支持的城市：北京、上海、深圳、成都，其他城市返回默认晴天数据。
     *
     * @param city 城市名称，如：北京、上海、深圳、成都、广州等
     * @param unit 温度单位：celsius（摄氏度）或 fahrenheit（华氏度），默认为celsius
     * @return String 格式化的天气信息字符串，包含城市、天气状况、温度、湿度
     */
    @Tool(description = "获取指定城市的当前天气信息，包括天气状况、温度和湿度。当用户询问天气、气温、是否下雨等问题时使用此工具。")
    public String getWeather(
            @ToolParam(description = "城市名称，必须是中文城市名，如：北京、上海、深圳、成都、广州、杭州等") String city,
            @ToolParam(description = "温度单位，可选值：celsius（摄氏度，默认）或 fahrenheit（华氏度）", required = false) String unit) {
        log.info("天气查询工具被调用: city={}, unit={}", city, unit);

        String tempUnit = unit != null ? unit : "celsius";
        String unitSymbol = "celsius".equals(tempUnit) ? "°C" : "°F";

        String condition;
        double temp;
        int humidity;

        switch (city) {
            case "北京" -> {
                condition = "晴天";
                temp = 15.5;
                humidity = 45;
            }
            case "上海" -> {
                condition = "多云";
                temp = 20.0;
                humidity = 70;
            }
            case "深圳" -> {
                condition = "小雨";
                temp = 28.0;
                humidity = 85;
            }
            case "成都" -> {
                condition = "阴天";
                temp = 18.0;
                humidity = 75;
            }
            case "广州" -> {
                condition = "多云转晴";
                temp = 26.0;
                humidity = 78;
            }
            case "杭州" -> {
                condition = "小雨";
                temp = 22.0;
                humidity = 80;
            }
            default -> {
                condition = "晴天";
                temp = 20.0;
                humidity = 60;
                log.debug("未知城市 {}，返回默认天气数据", city);
            }
        }

        if ("fahrenheit".equals(tempUnit)) {
            temp = temp * 9 / 5 + 32;
        }

        String result = String.format(
                "%s天气：%s，温度%.1f%s，湿度%d%%，空气质量良好",
                city, condition, temp, unitSymbol, humidity
        );
        log.info("天气查询结果: {}", result);
        return result;
    }

    /**
     * 获取未来几天的天气预报
     * 返回指定城市未来3天的简单天气预报（模拟数据）。
     *
     * @param city 城市名称
     * @return String 未来3天的天气预报
     */
    @Tool(description = "获取指定城市未来3天的天气预报，包括天气状况和温度范围。当用户询问未来天气、天气预报时使用。")
    public String getWeatherForecast(
            @ToolParam(description = "城市名称，如：北京、上海、深圳等") String city) {
        log.info("天气预报工具被调用: city={}", city);
        return String.format("""
                %s未来3天天气预报：
                明天：多云转晴，12°C ~ 20°C，微风
                后天：晴天，10°C ~ 18°C
                大后天：小雨，15°C ~ 19°C
                建议携带雨具，注意增减衣物。
                """, city);
    }
}
