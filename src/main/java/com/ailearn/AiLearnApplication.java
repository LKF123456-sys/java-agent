package com.ailearn; // 声明包名，com.ailearn是项目根包

import org.mybatis.spring.annotation.MapperScan; // MyBatis-Spring注解，用于扫描Mapper接口并自动注册到Spring容器
import org.springframework.boot.SpringApplication; // Spring Boot核心类，用于启动Spring应用程序
import org.springframework.boot.autoconfigure.SpringBootApplication; // Spring Boot自动配置注解，包含@Configuration、@EnableAutoConfiguration、@ComponentScan三个注解的组合

/**
 * 赛博AI平台（Cyber AI Platform）启动类
 * 基于Spring Boot 3.4.7和Spring AI 1.0.0构建的生产级AI应用平台
 *
 * <p>平台核心功能模块：
 * <ul>
 *   <li><b>用户认证</b>：JWT双Token认证机制，支持注册、登录、Token刷新</li>
 *   <li><b>智能聊天</b>：基础AI对话，支持同步和SSE流式输出</li>
 *   <li><b>记忆对话</b>：带持久化记忆的多轮对话，自动保存对话历史</li>
 *   <li><b>智能体（Agent）</b>：单Agent工具调用，支持天气查询、数学计算</li>
 *   <li><b>多智能体协作</b>：Planner/Researcher/Coder/Critic/Executor多Agent协作完成复杂任务</li>
 *   <li><b>RAG知识库</b>：文档上传、向量存储、检索增强问答，支持PDF/Word/Excel/PPT/图片OCR等多种格式</li>
 *   <li><b>结构化输出</b>：从非结构化文本中提取图书、电影等结构化信息</li>
 *   <li><b>MCP协议</b>：支持Model Context Protocol，暴露工具供MCP客户端调用</li>
 * </ul>
 *
 * <p>技术栈：
 * <ul>
 *   <li>Spring Boot 3.4.7 + Spring Security + Spring Validation</li>
 *   <li>Spring AI 1.0.0 + Ollama本地模型</li>
 *   <li>MyBatis-Plus 3.5.9 + MySQL/PostgreSQL(PgVector)</li>
 *   <li>Springdoc OpenAPI 2.8.6（Swagger文档）</li>
 *   <li>Resilience4j（限流熔断）</li>
 *   <li>JJWT 0.12.6（JWT令牌）</li>
 * </ul>
 *
 * @author AiLearn Platform
 * @version 0.0.3-SNAPSHOT
 */
@SpringBootApplication // 标记这是一个Spring Boot应用类，启用自动配置和组件扫描
@MapperScan("com.ailearn.mapper") // MyBatis注解，扫描com.ailearn.mapper包下的所有Mapper接口
public class AiLearnApplication { // 应用程序主类定义

    /**
     * 应用程序入口方法
     * 启动Spring Boot应用，初始化Spring应用上下文，自动配置所有组件
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) { // Java程序入口方法，main方法是所有Java应用的起点
        SpringApplication.run(AiLearnApplication.class, args); // 启动Spring Boot应用，传入主类和命令行参数
    } // main方法结束
} // AiLearnApplication类结束
