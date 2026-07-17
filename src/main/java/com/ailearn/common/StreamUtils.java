package com.ailearn.common; // 声明包名，属于通用工具模块

import java.util.ArrayList; // 导入ArrayList动态数组，用于存储切分后的文本块
import java.util.List; // 导入List接口，作为ArrayList的父类型引用

/**
 * 流式文本处理工具类
 * 提供文本分块功能，将长文本按随机大小切分为多个小块，
 * 用于模拟SSE流式响应时的逐块发送效果
 *
 * @author AiLearn Platform
 */
public class StreamUtils { // 工具类定义，使用final语义（通过私有构造器防止实例化）

    /**
     * 私有构造器
     * 防止工具类被外部实例化，确保只能通过静态方法调用
     */
    private StreamUtils() { // 私有构造器，外部无法new StreamUtils()
    } // 构造器结束

    /**
     * 将文本按随机大小切分为多个块
     * 每个块的大小为2-4个字符（随机），用于模拟流式输出的打字机效果
     * 主要在测试或演示场景中模拟AI逐token输出的行为
     *
     * @param text 待切分的原始文本，如果为null或空字符串则返回空数组
     * @return String[] 切分后的文本块数组，每个元素为2-4个字符的子串
     */
    public static String[] splitIntoChunks(String text) { // 静态方法，接收原始文本参数
        if (text == null || text.isEmpty()) { // 空值保护：如果文本为null或空字符串
            return new String[0]; // 返回空数组而非null，避免调用方NPE风险
        } // 空值检查结束
        List<String> chunks = new ArrayList<>(); // 创建动态列表，用于收集切分后的文本块
        int i = 0; // 初始化当前切分位置索引，从文本开头（0）开始
        while (i < text.length()) { // 循环条件：当前索引未超出文本长度时继续切分
            int chunkSize = 2 + (int) (Math.random() * 3); // 随机生成块大小：2 + [0,2] = 2~4个字符
            int end = Math.min(i + chunkSize, text.length()); // 计算本次切分的结束位置，不超过文本末尾
            chunks.add(text.substring(i, end)); // 截取[i, end)范围的子串并添加到列表
            i = end; // 更新当前索引为结束位置，准备下一次切分
        } // while循环结束，文本已全部切分完毕
        return chunks.toArray(new String[0]); // 将ArrayList转换为String数组并返回
    } // splitIntoChunks方法结束
} // StreamUtils类结束
