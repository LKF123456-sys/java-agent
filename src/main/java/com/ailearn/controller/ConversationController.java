package com.ailearn.controller; // 声明包名，controller包存放所有REST控制器类

import com.ailearn.common.Result; // 统一响应结果封装类，用于标准化API返回格式
import com.ailearn.entity.ChatMessage; // 聊天消息实体类，对应数据库中的chat_message表，存储单条聊天消息
import com.ailearn.entity.Conversation; // 会话实体类，对应数据库中的conversation表，存储会话基本信息
import com.ailearn.security.UserPrincipal; // 用户主体类，封装当前登录用户信息
import com.ailearn.service.ConversationService; // 会话服务类，提供会话CRUD、消息管理等业务逻辑
import io.swagger.v3.oas.annotations.Operation; // OpenAPI注解，用于描述API操作的摘要和详细信息
import io.swagger.v3.oas.annotations.Parameter; // OpenAPI注解，用于描述API参数信息
import io.swagger.v3.oas.annotations.tags.Tag; // OpenAPI注解，用于对API进行分组和描述
import lombok.RequiredArgsConstructor; // Lombok注解，自动生成包含所有final字段的构造函数
import lombok.extern.slf4j.Slf4j; // Lombok注解，自动生成SLF4J日志对象log
import org.springframework.security.core.Authentication; // Spring Security接口，表示当前用户的认证信息
import org.springframework.security.core.context.SecurityContextHolder; // Spring Security类，提供对SecurityContext的访问
import org.springframework.web.bind.annotation.*; // Spring Web注解，包含@RestController、@RequestMapping、@GetMapping等常用注解

import java.util.List; // Java标准库类，列表接口，用于存储有序集合
import java.util.Map; // Java标准库类，键值对映射接口，用于存储动态数据

@Slf4j // Lombok注解，自动注入SLF4J日志记录器
@RestController // Spring MVC注解，标记该类为REST控制器，方法返回值直接作为HTTP响应体
@RequestMapping("/api/conversations") // Spring MVC注解，指定该控制器的根路径为/api/conversations
@RequiredArgsConstructor // Lombok注解，为所有final字段生成构造函数，用于依赖注入
@Tag(name = "会话管理", description = "统一的会话CRUD接口") // OpenAPI注解，给该控制器的API分组
public class ConversationController { // 会话管理控制器类定义

    private final ConversationService conversationService; // 会话服务接口，通过构造函数注入，提供会话相关业务逻辑

    @GetMapping // Spring MVC注解，映射HTTP GET请求到根路径，完整路径为GET /api/conversations
    @Operation(summary = "获取会话列表", description = "获取当前用户的所有会话列表，可按类型筛选") // OpenAPI注解，描述该接口
    public Result<List<Conversation>> listConversations( // 获取会话列表接口方法定义，返回Result包装的Conversation列表
            @Parameter(description = "会话类型: chat/memory/rag/agent/multi-agent/structured") // OpenAPI注解，描述type参数
            @RequestParam(required = false, defaultValue = "chat") String type) { // @RequestParam绑定请求参数type，非必填，默认值为chat
        UserPrincipal user = getCurrentUser(); // 调用私有方法获取当前登录用户信息
        List<Conversation> conversations = conversationService.listConversations(user.getUserId(), type); // 调用会话服务查询当前用户指定类型的会话列表
        return Result.success(conversations); // 返回成功响应，包装会话列表
    } // listConversations方法结束

    @PostMapping // Spring MVC注解，映射HTTP POST请求到根路径，完整路径为POST /api/conversations
    @Operation(summary = "创建新会话", description = "创建一个新的聊天会话，指定标题和会话类型") // OpenAPI注解，描述创建会话接口
    public Result<Conversation> createConversation(@RequestBody Map<String, String> body) { // 创建新会话接口方法，@RequestBody将请求体解析为Map
        UserPrincipal user = getCurrentUser(); // 获取当前登录用户信息
        String title = body.getOrDefault("title", "新对话"); // 从请求体获取title参数，不存在时使用默认值"新对话"
        String type = body.getOrDefault("type", "chat"); // 从请求体获取type参数，不存在时使用默认值"chat"
        Conversation conversation = conversationService.createConversation(user.getUserId(), title, type); // 调用会话服务创建新会话
        return Result.success(conversation); // 返回成功响应，包装新创建的会话
    } // createConversation方法结束

    @DeleteMapping("/{id}") // Spring MVC注解，映射HTTP DELETE请求到/{id}路径，完整路径为DELETE /api/conversations/{id}
    @Operation(summary = "删除会话", description = "删除指定ID的会话及其所有消息") // OpenAPI注解，描述删除会话接口
    public Result<Void> deleteConversation( // 删除会话接口方法定义，返回Result包装的Void（无数据）
            @Parameter(description = "会话ID", required = true) // OpenAPI注解，描述id路径参数
            @PathVariable Long id) { // @PathVariable绑定URL路径中的id参数
        UserPrincipal user = getCurrentUser(); // 获取当前登录用户信息
        conversationService.deleteConversation(user.getUserId(), id); // 调用会话服务删除指定会话（会验证所有权）
        return Result.success(); // 返回成功响应（无数据）
    } // deleteConversation方法结束

    @GetMapping("/{id}/messages") // Spring MVC注解，映射HTTP GET请求到/{id}/messages路径，完整路径为GET /api/conversations/{id}/messages
    @Operation(summary = "获取会话消息历史", description = "获取指定会话的所有历史消息记录") // OpenAPI注解，描述获取会话消息接口
    public Result<List<ChatMessage>> getMessages( // 获取会话消息历史接口方法定义
            @Parameter(description = "会话ID", required = true) // OpenAPI注解，描述id路径参数
            @PathVariable Long id) { // @PathVariable绑定URL路径中的id参数
        UserPrincipal user = getCurrentUser(); // 获取当前登录用户信息
        List<ChatMessage> messages = conversationService.getMessages(user.getUserId(), id); // 调用会话服务获取指定会话的消息列表
        return Result.success(messages); // 返回成功响应，包装消息列表
    } // getMessages方法结束

    private UserPrincipal getCurrentUser() { // 私有工具方法，获取当前登录用户信息，供本类中其他方法复用
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // 从SecurityContextHolder获取认证对象
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) { // 判断认证对象不为空且principal类型正确
            return (UserPrincipal) authentication.getPrincipal(); // 强制转换并返回UserPrincipal对象
        } // if判断结束
        return null; // 认证信息不存在或类型不正确时返回null
    } // getCurrentUser方法结束
} // ConversationController类结束
