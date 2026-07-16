package com.ailearn.memory;

import com.ailearn.entity.ChatMessage;
import com.ailearn.mapper.ChatMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseChatMemory implements ChatMemory {

    /**
     * 默认保留的最近历史消息对数（一对=一问一答）
     * 限制为20条消息（10轮对话），避免上下文过长导致Ollama模型报错
     */
    private static final int DEFAULT_MAX_HISTORY_MESSAGES = 20;

    /**
     * 聊天消息数据访问接口
     * 用于chat_message表的CRUD操作
     */
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 向指定会话添加多条消息（Spring AI 1.0.0标准接口方法）
     * 将一批消息批量保存到数据库中
     *
     * @param conversationId 会话ID字符串（会自动解析为Long类型的数据库ID）
     * @param messages       要添加的消息列表
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        log.debug("添加消息到会话记忆: conversationId={}, messageCount={}",
                conversationId, messages.size());
        Long convId = parseConversationId(conversationId);
        if (convId == null) {
            log.warn("无法解析会话ID，跳过消息保存: conversationId={}", conversationId);
            return;
        }

        for (Message message : messages) {
            saveSingleMessage(convId, message);
        }
        log.debug("消息保存完成: conversationId={}", conversationId);
    }

    /**
     * 获取指定会话的最近N条消息
     * 默认获取最近20条消息（10轮对话），防止上下文过长导致模型调用失败
     *
     * @param conversationId 会话ID字符串
     * @return List&lt;Message&gt; 按时间正序排列的消息列表（最早的消息在前）
     */
    @Override
    public List<Message> get(String conversationId) {
        return get(conversationId, DEFAULT_MAX_HISTORY_MESSAGES);
    }

    /**
     * 获取指定会话最近的N条消息
     * 从数据库查询最近的消息，按时间正序返回（保持对话的时间顺序）
     *
     * @param conversationId 会话ID字符串
     * @param lastN          获取最近的N条消息，如果为0或负数则获取全部
     * @return List&lt;Message&gt; 按时间正序排列的最近N条消息列表
     */
    public List<Message> get(String conversationId, int lastN) {
        log.debug("获取会话消息: conversationId={}, lastN={}", conversationId, lastN);
        Long convId = parseConversationId(conversationId);
        if (convId == null) {
            log.warn("无法解析会话ID，返回空消息列表: conversationId={}", conversationId);
            return new ArrayList<>();
        }

        // 构建查询条件，按ID倒序（先获取最新的消息）
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, convId)
                .orderByDesc(ChatMessage::getId);

        // 如果指定了lastN且不是最大值，使用LIMIT限制返回数量
        if (lastN > 0 && lastN < Integer.MAX_VALUE) {
            wrapper.last("LIMIT " + lastN);
        }

        List<ChatMessage> dbMessages = chatMessageMapper.selectList(wrapper);
        log.debug("从数据库查询到{}条消息: conversationId={}", dbMessages.size(), conversationId);

        // 将数据库消息实体转换为Spring AI的Message对象
        List<Message> result = new ArrayList<>();

        // 注意：因为查询是按ID倒序的，所以需要反转列表以恢复时间正序
        for (int i = dbMessages.size() - 1; i >= 0; i--) {
            ChatMessage dbMsg = dbMessages.get(i);
            Message aiMsg = convertToAiMessage(dbMsg);
            if (aiMsg != null) {
                result.add(aiMsg);
            }
        }

        log.debug("返回{}条消息: conversationId={}", result.size(), conversationId);
        return result;
    }

    /**
     * 清除指定会话的所有消息记忆
     * 删除数据库中该会话的所有聊天记录
     *
     * @param conversationId 会话ID字符串
     */
    @Override
    public void clear(String conversationId) {
        log.info("清除会话记忆: conversationId={}", conversationId);
        Long convId = parseConversationId(conversationId);
        if (convId == null) {
            log.warn("无法解析会话ID，跳过清除: conversationId={}", conversationId);
            return;
        }

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, convId);
        int deletedCount = chatMessageMapper.delete(wrapper);

        log.info("会话记忆已清除: conversationId={}, deletedCount={}", conversationId, deletedCount);
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
    private Long parseConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            log.warn("会话ID为空");
            return null;
        }
        try {
            return Long.parseLong(conversationId);
        } catch (NumberFormatException e) {
            // 尝试提取前缀后的数字部分（如 "agent_56" → 56）
            int underscoreIdx = conversationId.lastIndexOf('_');
            if (underscoreIdx >= 0 && underscoreIdx < conversationId.length() - 1) {
                String numPart = conversationId.substring(underscoreIdx + 1);
                try {
                    return Long.parseLong(numPart);
                } catch (NumberFormatException e2) {
                    // 继续尝试其他方式
                }
            }
            // 尝试提取字符串末尾的连续数字
            StringBuilder numBuilder = new StringBuilder();
            for (int i = conversationId.length() - 1; i >= 0; i--) {
                char c = conversationId.charAt(i);
                if (Character.isDigit(c)) {
                    numBuilder.insert(0, c);
                } else if (numBuilder.length() > 0) {
                    break;
                }
            }
            if (numBuilder.length() > 0) {
                try {
                    return Long.parseLong(numBuilder.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            log.warn("会话ID格式错误，无法解析为Long: conversationId={}", conversationId);
            return null;
        }
    }

    /**
     * 保存单条消息到数据库
     * 根据消息类型设置对应的role字段
     *
     * @param convId  会话ID（Long类型）
     * @param message Spring AI消息对象
     */
    private void saveSingleMessage(Long convId, Message message) {
        try {
            ChatMessage entity = new ChatMessage();
            entity.setConversationId(convId);
            // 将消息类型转换为小写字符串存储（user/assistant/system）
            entity.setRole(message.getMessageType().name().toLowerCase());
            entity.setContent(message.getText());
            chatMessageMapper.insert(entity);
            log.trace("消息已保存: conversationId={}, role={}", convId, entity.getRole());
        } catch (Exception e) {
            log.error("保存消息到数据库失败: conversationId={}, role={}, error={}",
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
    private Message convertToAiMessage(ChatMessage dbMsg) {
        String role = dbMsg.getRole();
        String content = dbMsg.getContent();

        if (role == null || content == null) {
            log.warn("消息内容或角色为空，跳过转换: messageId={}", dbMsg.getId());
            return null;
        }

        return switch (role.toLowerCase()) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> {
                log.warn("未知的消息角色类型: role={}, messageId={}", role, dbMsg.getId());
                yield null;
            }
        };
    }
}
