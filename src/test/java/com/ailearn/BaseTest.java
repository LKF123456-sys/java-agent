package com.ailearn;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 测试基类
 * 所有集成测试类继承此类，统一配置Spring Boot测试环境和测试profile
 * 使用H2内存数据库，禁用外部服务依赖（Ollama、PgVector等）
 *
 * @author AiLearn Platform
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml")
public abstract class BaseTest {
}
