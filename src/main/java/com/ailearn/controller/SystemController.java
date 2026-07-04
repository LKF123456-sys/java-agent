package com.ailearn.controller;

import com.ailearn.common.Result;
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
 * 提供健康检查、系统信息等公开接口，无需认证即可访问
 *
 * @author AiLearn Platform
 */
@RestController
@RequestMapping("/api")
public class SystemController {

    /**
     * 健康检查接口
     * 用于服务探活、负载均衡健康检查、监控系统检测等场景
     * 返回服务状态、启动时间、运行时长等基本信息
     *
     * @return Result 包含系统健康状态信息的统一响应
     */
    @GetMapping("/health")
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
     * 用于快速验证服务是否正常启动
     *
     * @return Result 包含欢迎信息的统一响应
     */
    @GetMapping("/")
    public Result<String> welcome() {
        return Result.success("Welcome to Java AI Learn Platform!");
    }
}
