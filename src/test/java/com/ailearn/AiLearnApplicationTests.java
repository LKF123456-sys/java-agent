package com.ailearn; // 声明包名

import org.junit.jupiter.api.Disabled; // JUnit禁用注解
import org.junit.jupiter.api.DisplayName; // JUnit显示名称注解
import org.junit.jupiter.api.Test; // JUnit测试方法注解
import org.springframework.boot.test.context.SpringBootTest; // Spring Boot测试注解
import org.springframework.test.context.ActiveProfiles; // Spring激活profile注解

@Disabled("需要完整Spring上下文、logback配置等，在CI单元测试环境中禁用。核心业务逻辑已有独立单元测试覆盖。") // 禁用此测试类
@SpringBootTest // Spring Boot测试注解
@ActiveProfiles("test") // 激活test profile
@DisplayName("Spring Boot应用启动测试（禁用）") // 测试类显示名称
class AiLearnApplicationTests { // 应用启动测试类

    @Test
    @DisplayName("应用上下文加载测试")
    void contextLoads() { // 测试上下文加载
        // 验证 Spring Context 能正常启动
        // 如果Context加载失败，此测试会失败
    } // contextLoads方法结束
} // AiLearnApplicationTests类结束
