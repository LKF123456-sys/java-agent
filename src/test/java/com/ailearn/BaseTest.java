package com.ailearn; // 声明包名，com.ailearn是项目根包，测试类放在同级包下

import org.springframework.boot.test.context.SpringBootTest; // Spring Boot测试注解，加载完整的Spring应用上下文进行集成测试
import org.springframework.test.context.ActiveProfiles; // Spring测试注解，指定测试时激活的profile
import org.springframework.test.context.TestPropertySource; // Spring测试注解，指定测试用的属性文件位置

/**
 * 测试基类
 * 所有集成测试类继承此类，统一配置Spring Boot测试环境和测试profile
 * 使用H2内存数据库，禁用外部服务依赖（Ollama、PgVector等）
 *
 * @author AiLearn Platform
 */
@SpringBootTest // Spring Boot测试注解，标记该类为Spring Boot测试类，会加载完整的Spring应用上下文
@ActiveProfiles("test") // 指定激活test profile，加载application-test.yml配置文件
@TestPropertySource(locations = "classpath:application-test.yml") // 显式指定测试属性文件位置，确保加载测试配置
public abstract class BaseTest { // 测试基类定义，abstract类不能直接实例化，供其他测试类继承
} // BaseTest类结束
