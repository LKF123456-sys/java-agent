package com.ailearn.controller; // 声明包名，controller包存放所有REST控制器类

import com.ailearn.common.Result; // 统一响应结果封装类，用于标准化API返回格式
import io.swagger.v3.oas.annotations.Operation; // OpenAPI注解，用于描述API操作的摘要和详细信息
import io.swagger.v3.oas.annotations.tags.Tag; // OpenAPI注解，用于对API进行分组和描述
import org.springframework.web.bind.annotation.GetMapping; // Spring MVC注解，映射HTTP GET请求
import org.springframework.web.bind.annotation.RequestMapping; // Spring MVC注解，指定控制器根路径
import org.springframework.web.bind.annotation.RestController; // Spring MVC注解，标记REST控制器

import java.lang.management.ManagementFactory; // Java管理扩展类，用于获取JVM管理Bean（如RuntimeMXBean）
import java.lang.management.RuntimeMXBean; // Java管理扩展接口，提供JVM运行时信息（启动时间、运行时长、JVM名称版本等）
import java.time.LocalDateTime; // Java时间API类，表示本地日期时间（不带时区）
import java.time.format.DateTimeFormatter; // Java时间API类，用于日期时间格式化和解析
import java.util.HashMap; // Java集合类，基于哈希表的Map实现，用于存储键值对
import java.util.Map; // Java标准库类，键值对映射接口

/**
 * 系统基础控制器
 * 提供健康检查、服务信息等公开接口，所有接口无需认证即可访问
 * 主要用于服务探活、负载均衡健康检查、监控系统检测、快速验证服务可用性等场景
 *
 * @author AiLearn Platform
 */
@RestController // Spring MVC注解，标记该类为REST控制器
@RequestMapping("/api") // Spring MVC注解，指定根路径为/api
@Tag(name = "系统信息", description = "系统健康检查和服务信息接口，无需认证") // OpenAPI注解，API分组
public class SystemController { // 系统信息控制器类定义

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
    @GetMapping("/health") // Spring MVC注解，映射HTTP GET请求到/health路径，完整路径GET /api/health
    @Operation(summary = "健康检查", description = "返回服务运行状态、启动时间、JVM信息、内存使用情况等详细系统状态，用于K8s/Docker探活和负载均衡健康检查") // OpenAPI注解描述接口
    public Result<Map<String, Object>> health() { // 健康检查接口方法定义，返回Result包装的系统信息Map
        Map<String, Object> healthInfo = new HashMap<>(); // 创建HashMap存储健康信息
        healthInfo.put("status", "UP"); // 放入服务状态，固定为"UP"表示服务正常运行
        healthInfo.put("service", "java-ai-learn"); // 放入服务名称标识
        healthInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)); // 放入当前时间戳，使用ISO-8601格式格式化

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean(); // 通过ManagementFactory获取RuntimeMXBean实例，用于访问JVM运行时信息
        healthInfo.put("startTime", runtimeMXBean.getStartTime()); // 放入JVM启动时间（毫秒时间戳）
        healthInfo.put("uptime", runtimeMXBean.getUptime()); // 放入JVM已运行时长（毫秒）
        healthInfo.put("vmName", runtimeMXBean.getVmName()); // 放入JVM名称（如OpenJDK 64-Bit Server VM）
        healthInfo.put("vmVersion", runtimeMXBean.getVmVersion()); // 放入JVM版本信息

        Runtime runtime = Runtime.getRuntime(); // 获取当前JVM的Runtime实例，用于访问内存等运行时信息
        Map<String, Object> memory = new HashMap<>(); // 创建HashMap存储内存信息
        memory.put("max", runtime.maxMemory()); // 放入JVM最大可用内存（-Xmx参数配置，单位字节）
        memory.put("total", runtime.totalMemory()); // 放入JVM当前已分配内存（单位字节）
        memory.put("free", runtime.freeMemory()); // 放入JVM当前空闲内存（单位字节）
        memory.put("used", runtime.totalMemory() - runtime.freeMemory()); // 计算并放入已使用内存（已分配减空闲）
        healthInfo.put("memory", memory); // 将内存信息Map放入健康信息中

        return Result.success(healthInfo); // 返回成功响应，包装所有健康状态信息
    } // health方法结束

    /**
     * 根路径欢迎接口
     * 用于快速验证服务是否正常启动，返回欢迎信息
     * 接口路径：GET /api/
     * 权限：permitAll（无需认证即可访问）
     *
     * @return Result&lt;String&gt; 包含欢迎信息的统一响应
     */
    @GetMapping("/") // Spring MVC注解，映射HTTP GET请求到/路径，完整路径GET /api/
    @Operation(summary = "欢迎页", description = "快速验证服务是否正常启动，返回欢迎信息") // OpenAPI注解描述接口
    public Result<String> welcome() { // 欢迎页接口方法定义，返回Result包装的字符串
        return Result.success("Welcome to Java AI Learn Platform!"); // 返回成功响应，包含欢迎信息字符串
    } // welcome方法结束
} // SystemController类结束
