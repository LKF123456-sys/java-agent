package com.ailearn.memory; // 声明包名，memory包存放聊天记忆相关类

import com.ailearn.entity.ChatMessage; // 聊天消息实体
import com.ailearn.mapper.ChatMessageMapper; // 消息表Mapper
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus条件构造器
import lombok.RequiredArgsConstructor; // Lombok构造器注入
import lombok.extern.slf4j.Slf4j; // Lombok日志
import org.springframework.ai.chat.messages.AssistantMessage; // AI助手消息
import org.springframework.ai.chat.messages.Message; // Spring AI消息基类
import org.springframework.ai.chat.messages.SystemMessage; // 系统消息
import org.springframework.ai.chat.messages.UserMessage; // 用户消息
import org.springframework.ai.chat.memory.ChatMemory; // Spring AI记忆接口
import org.springframework.stereotype.Component; // Spring组件注解

import java.util.ArrayList; // 动态数组
import java.util.List; // 列表接口

/**
 * 数据库持久化聊天记忆实现
 * 实现Spring AI的ChatMemory接口，将对话历史持久化到MySQL数据库
 * 替代默认的InMemoryChatMemory，实现对话历史的持久化存储和跨会话共享
 *
 * 支持的消息类型：
 * - USER: 用户消息
 * - ASSISTANT: AI助手回复
 * - SYSTEM: 系统消息
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log
@Component // 注册为Spring Bean
@RequiredArgsConstructor // 为final字段生成构造器
public class DatabaseChatMemory implements ChatMemory { // 实现Spring AI记忆接口

    /**
     * 默认保留的最近历史消息对数（一对=一问一答）
     * 限制为20条消息（10轮对话），避免上下文过长导致Ollama模型报错
     */
    private static final int DEFAULT_MAX_HISTORY_MESSAGES = 20; // 默认取最近20条

    /**
     * 聊天消息数据访问接口
     * 用于chat_message表的CRUD操作
     */
    private final ChatMessageMapper chatMessageMapper; // 由Spring注入

    /**
     * 向指定会话添加多条消息（Spring AI 1.0.0标准接口方法）
     * 将一批消息批量保存到数据库中
     *
     * @param conversationId 会话ID字符串（会自动解析为Long类型的数据库ID）
     * @param messages       要添加的消息列表
     */
    @Override // 接口方法实现
    public void add(String conversationId, List<Message> messages) { // 添加消息到记忆
        log.debug("添加消息到会话记忆: conversationId={}, messageCount={}", // 调试日志
                conversationId, messages.size());
        Long convId = parseConversationId(conversationId); // 解析会话ID字符串为Long
        if (convId == null) { // 解析失败
            log.warn("无法解析会话ID，跳过消息保存: conversationId={}", conversationId); // 警告日志
            return; // 直接返回，不保存
        }

        for (Message message : messages) { // 遍历每条消息
            saveSingleMessage(convId, message); // 逐条保存
        }
        log.debug("消息保存完成: conversationId={}", conversationId); // 调试日志
    }

    /**
     * 获取指定会话的最近N条消息
     * 默认获取最近20条消息（10轮对话），防止上下文过长导致模型调用失败
     *
     * @param conversationId 会话ID字符串
     * @return List<Message> 按时间正序排列的消息列表（最早的消息在前）
     */
    @Override // 接口方法实现
    public List<Message> get(String conversationId) { // 获取记忆
        return get(conversationId, DEFAULT_MAX_HISTORY_MESSAGES); // 委托带数量重载
    }

    /**
     * 获取指定会话最近的N条消息
     * 从数据库查询最近的消息，按时间正序返回（保持对话的时间顺序）
     *
     * @param conversationId 会话ID字符串
     * @param lastN          获取最近的N条消息，如果为0或负数则获取全部
     * @return List<Message> 按时间正序排列的最近N条消息列表
     */
    public List<Message> get(String conversationId, int lastN) { // 带数量的获取
        log.debug("获取会话消息: conversationId={}, lastN={}", conversationId, lastN); // 调试日志
        Long convId = parseConversationId(conversationId); // 解析会话ID
        if (convId == null) { // 解析失败
            log.warn("无法解析会话ID，返回空消息列表: conversationId={}", conversationId); // 警告日志
            return new ArrayList<>(); // 返回空列表
        }

        // 构建查询条件，按ID倒序（先获取最新的消息）
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>(); // 条件构造器
        wrapper.eq(ChatMessage::getConversationId, convId) // 条件：属于该会话
                .orderByDesc(ChatMessage::getId); // 按ID倒序（最新在前）

        // 如果指定了lastN且不是最大值，使用LIMIT限制返回数量
        if (lastN > 0 && lastN < Integer.MAX_VALUE) { // 需要限制数量
            wrapper.last("LIMIT " + lastN); // 追加LIMIT子句
        }

        List<ChatMessage> dbMessages = chatMessageMapper.selectList(wrapper); // 执行查询
        log.debug("从数据库查询到{}条消息: conversationId={}", dbMessages.size(), conversationId); // 调试日志

        // 将数据库消息实体转换为Spring AI的Message对象
        List<Message> result = new ArrayList<>(); // 结果列表

        // 注意：因为查询是按ID倒序的，所以需要反转列表以恢复时间正序
        for (int i = dbMessages.size() - 1; i >= 0; i--) { // 倒序遍历实现反转
            ChatMessage dbMsg = dbMessages.get(i); // 取第i条
            Message aiMsg = convertToAiMessage(dbMsg); // 转换为Spring AI消息
            if (aiMsg != null) { // 转换成功
                result.add(aiMsg); // 加入结果
            }
        }

        log.debug("返回{}条消息: conversationId={}", result.size(), conversationId); // 调试日志
        return result; // 返回正序消息列表
    }

    /**
     * 清除指定会话的所有消息记忆
     * 删除数据库中该会话的所有聊天记录
     *
     * @param conversationId 会话ID字符串
     */
    @Override // 接口方法实现
    public void clear(String conversationId) { // 清除记忆
        log.info("清除会话记忆: conversationId={}", conversationId); // 业务日志
        Long convId = parseConversationId(conversationId); // 解析会话ID
        if (convId == null) { // 解析失败
            log.warn("无法解析会话ID，跳过清除: conversationId={}", conversationId); // 警告日志
            return; // 直接返回
        }

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>(); // 条件构造器
        wrapper.eq(ChatMessage::getConversationId, convId); // 条件：属于该会话
        int deletedCount = chatMessageMapper.delete(wrapper); // 批量删除

        log.info("会话记忆已清除: conversationId={}, deletedCount={}", conversationId, deletedCount); // 业务日志
    }

    /**
     * 解析会话ID字符串为Long类型
     * 支持多种格式的会话ID：
     * 1. 纯数字格式："56" → 56
     * 2. 带前缀格式："agent_56"、"chat_123"、"task_456" → 提取前缀后的数字部分
     * 3. 包含数字的字符串：提取末尾的连续数字部分
     * 解析失败返回null（表示不查询数据库，而不是查询-1的错误数据）
     *
     * @param conversationId 会话ID字符串
     * @return Long 解析后的会话ID，解析失败返回null
     */
    private Long parseConversationId(String conversationId) { // 私有解析方法
        if (conversationId == null || conversationId.isBlank()) { // 空值
            log.warn("会话ID为空"); // 警告日志
            return null; // 返回null
        }
        try { // 尝试纯数字解析
            return Long.parseLong(conversationId); // 直接转Long
        } catch (NumberFormatException e) { // 不是纯数字
            // 尝试提取前缀后的数字部分（如 "agent_56" → 56）
            int underscoreIdx = conversationId.lastIndexOf('_'); // 找最后一个下划线
            if (underscoreIdx >= 0 && underscoreIdx < conversationId.length() - 1) { // 有下划线且后面有内容
                String numPart = conversationId.substring(underscoreIdx + 1); // 取下划线后部分
                try { // 尝试解析
                    return Long.parseLong(numPart); // 转Long
                } catch (NumberFormatException e2) { // 仍失败
                    // 继续尝试其他方式
                }
            }
            // 尝试提取字符串末尾的连续数字
            StringBuilder numBuilder = new StringBuilder(); // 数字构建器
            for (int i = conversationId.length() - 1; i >= 0; i--) { // 从末尾向前扫描
                char c = conversationId.charAt(i); // 取字符
                if (Character.isDigit(c)) { // 是数字
                    numBuilder.insert(0, c); // 插到构建器头部
                } else if (numBuilder.length() > 0) { // 非数字且已收集到数字
                    break; // 遇到非数字停止
                }
            }
            if (numBuilder.length() > 0) { // 收集到数字
                try { // 尝试解析
                    return Long.parseLong(numBuilder.toString()); // 转Long
                } catch (NumberFormatException ignored) { // 忽略
                }
            }
            log.warn("会话ID格式错误，无法解析为Long: conversationId={}", conversationId); // 警告日志
            return null; // 返回null
        }
    }

    /**
     * 保存单条消息到数据库
     * 根据消息类型设置对应的role字段
     *
     * @param convId  会话ID（Long类型）
     * @param message Spring AI消息对象
     */
    private void saveSingleMessage(Long convId, Message message) { // 私有保存方法
        try { // 尝试保存
            ChatMessage entity = new ChatMessage(); // 创建实体
            entity.setConversationId(convId); // 设置会话ID
            // 将消息类型转换为小写字符串存储（user/assistant/system）
            entity.setRole(message.getMessageType().name().toLowerCase()); // 取消息类型枚举名转小写
            entity.setContent(message.getText()); // 设置消息文本
            chatMessageMapper.insert(entity); // 插入数据库
            log.trace("消息已保存: conversationId={}, role={}", convId, entity.getRole()); // trace日志
        } catch (Exception e) { // 保存异常
            log.error("保存消息到数据库失败: conversationId={}, role={}, error={}", // 错误日志
                    convId, message.getMessageType(), e.getMessage(), e);
            // 消息保存失败不抛出异常，避免影响主对话流程
        }
    }

    /**
     * 将数据库消息实体转换为Spring AI的Message对象
     * 根据role字段创建对应的消息类型实例
     *
     * @param dbMsg 数据库中的聊天消息实体
     * @return Message Spring AI消息对象，如果类型不识别返回null
     */
    private Message convertToAiMessage(ChatMessage dbMsg) { // 私有转换方法
        String role = dbMsg.getRole(); // 取角色
        String content = dbMsg.getContent(); // 取内容

        if (role == null || content == null) { // 角色或内容为空
            log.warn("消息内容或角色为空，跳过转换: messageId={}", dbMsg.getId()); // 警告日志
            return null; // 返回null
        }

        return switch (role.toLowerCase()) { // switch表达式按角色分支
            case "user" -> new UserMessage(content); // 用户消息
            case "assistant" -> new AssistantMessage(content); // 助手消息
            case "system" -> new SystemMessage(content); // 系统消息
            default -> { // 未知角色
                log.warn("未知的消息角色类型: role={}, messageId={}", role, dbMsg.getId()); // 警告日志
                yield null; // 返回null
            }
        };
    }
} // DatabaseChatMemory类结束
