package com.ailearn.tools;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 计算器工具 - 演示多工具协作
 * 模型在需要精确计算时会调用此工具（避免 LLM 计算错误）
 */
@Component
@Description("执行基础数学运算：加减乘除")
public class CalculatorTool implements Function<CalculatorTool.Request, CalculatorTool.Response> {

    public record Request(
            @JsonPropertyDescription("第一个数字") double a,
            @JsonPropertyDescription("运算符：+、-、*、/") String operator,
            @JsonPropertyDescription("第二个数字") double b
    ) {}

    public record Response(double result, String expression) {}

    @Override
    public Response apply(Request request) {
        double result = switch (request.operator()) {
            case "+" -> request.a() + request.b();
            case "-" -> request.a() - request.b();
            case "*" -> request.a() * request.b();
            case "/" -> {
                if (request.b() == 0) throw new IllegalArgumentException("除数不能为零");
                yield request.a() / request.b();
            }
            default -> throw new IllegalArgumentException("不支持的运算符: " + request.operator());
        };
        String expr = request.a() + " " + request.operator() + " " + request.b() + " = " + result;
        return new Response(result, expr);
    }
}