package com.ailearn.controller;

import com.ailearn.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统基础控制器
 * 提供健康检查、服务信息等公开接口，所有接口无需认证即可访问
 * 主要用于服务探活、负载均衡健康检查、监控系统检测、快速验证服务可用性等场景
 *
 * @author AiLearn Platform
 */
@RestController
@RequestMapping("/api")
@Tag(name = "系统信息", description = "系统健康检查和服务信息接口，无需认证")
public class SystemController {

    /**
     * 健康检查接口
     * 返回服务运行状态、启动时间、JVM信息、内存使用情况等详细系统状态
     * 用于K8s/Docker容器探活、Nginx负载均衡健康检查、Prometheus监控等场景
     * 接口路径：GET /api/health
     * 权限：permitAll（无需认证即可访问）
     *
     * @return Result&lt;Map&lt;String, Object&gt;&gt; 包含系统健康状态信息的统一响应，具体字段：
     *         - status: String 服务状态，固定为"UP"表示正常运行
     *         - service: String 服务名称，固定为"java-ai-learn"
     *         - timestamp: String 当前时间戳（ISO-8601格式）
     *         - startTime: long JVM启动时间（毫秒时间戳）
     *         - uptime: long JVM已运行时长（毫秒）
     *         - vmName: String JVM名称
     *         - vmVersion: String JVM版本
     *         - memory: Map 内存使用信息，包含max（最大内存）、total（已分配内存）、free（空闲内存）、used（已使用内存），单位为字节
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "返回服务运行状态、启动时间、JVM信息、内存使用情况等详细系统状态，用于K8s/Docker探活和负载均衡健康检查")
    public Result<Map<String, Object>> health() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "UP");
        healthInfo.put("service", "java-ai-learn");
        healthInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        healthInfo.put("startTime", runtimeMXBean.getStartTime());
        healthInfo.put("uptime", runtimeMXBean.getUptime());
        healthInfo.put("vmName", runtimeMXBean.getVmName());
        healthInfo.put("vmVersion", runtimeMXBean.getVmVersion());

        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> memory = new HashMap<>();
        memory.put("max", runtime.maxMemory());
        memory.put("total", runtime.totalMemory());
        memory.put("free", runtime.freeMemory());
        memory.put("used", runtime.totalMemory() - runtime.freeMemory());
        healthInfo.put("memory", memory);

        return Result.success(healthInfo);
    }

    /**
     * 根路径欢迎接口
     * 用于快速验证服务是否正常启动，返回欢迎信息
     * 接口路径：GET /api/
     * 权限：permitAll（无需认证即可访问）
     *
     * @return Result&lt;String&gt; 包含欢迎信息的统一响应
     */
    @GetMapping("/")
    @Operation(summary = "欢迎页", description = "快速验证服务是否正常启动，返回欢迎信息")
    public Result<String> welcome() {
        return Result.success("Welcome to Java AI Learn Platform!");
    }
}
