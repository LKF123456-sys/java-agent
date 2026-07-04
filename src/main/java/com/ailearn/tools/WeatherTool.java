package com.ailearn.tools;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 模块四：Function Calling（工具调用）
 *
 * 知识点：
 * - Function Calling 让模型决定何时调用哪个函数，并提供参数
 * - 模型本身不执行函数，只返回调用意图；实际执行在应用侧
 * - Spring AI 通过 @Description 注解描述函数功能，帮助模型理解
 * - 流程：用户提问 -> 模型决定调用工具 -> 应用执行 -> 结果返回给模型 -> 模型生成最终回复
 *
 * 这是一个模拟天气查询工具（真实场景可接入天气API）
 */
@Component
@Description("获取指定城市的当前天气信息")  // 告诉模型这个函数是干什么的
public class WeatherTool implements Function<WeatherTool.Request, WeatherTool.Response> {

    /**
     * 请求参数 - 模型会按此结构填充参数
     */
    @JsonClassDescription("天气查询请求")
    public record Request(
            @JsonProperty(required = true)
            @JsonPropertyDescription("城市名称，如：北京、上海、深圳")
            String city,

            @JsonPropertyDescription("温度单位：celsius（摄氏度）或 fahrenheit（华氏度），默认 celsius")
            String unit
    ) {}

    /**
     * 返回结果 - 模型收到此结果后生成用户友好的回复
     */
    public record Response(
            String city,
            double temperature,
            String unit,
            String condition,
            int humidity
    ) {}

    @Override
    public Response apply(Request request) {
        // 模拟天气数据（真实场景调用天气 API）
        String unit = request.unit() != null ? request.unit() : "celsius";
        return switch (request.city()) {
            case "北京" -> new Response("北京", 15.5, unit, "晴天", 45);
            case "上海" -> new Response("上海", 20.0, unit, "多云", 70);
            case "深圳" -> new Response("深圳", 28.0, unit, "小雨", 85);
            case "成都" -> new Response("成都", 18.0, unit, "阴天", 75);
            default -> new Response(request.city(), 20.0, unit, "晴天", 60);
        };
    }
}