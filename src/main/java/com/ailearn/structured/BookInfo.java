package com.ailearn.structured;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 图书信息结构化输出类
 * 用于从非结构化文本中提取图书的关键信息，
 * Spring AI会自动将此类的字段和@JsonPropertyDescription注解转换为JSON Schema，
 * 指导大语言模型按指定格式输出结构化数据。
 *
 * @param title     书名，图书的完整标题
 * @param author    作者，图书的作者姓名，多个作者用逗号分隔
 * @param isbn      ISBN编号，图书的国际标准书号（13位或10位）
 * @param publisher 出版社，图书的出版单位
 * @param publishYear 出版年份，图书首次出版的年份（4位数字）
 * @param genre     类型/分类，图书的题材分类，如：小说、科技、历史、哲学等
 * @param pages     页数，图书的总页数
 * @param price     价格，图书的定价（单位：元）
 * @param summary   内容简介，图书的内容概要和核心主题介绍
 * @param tags      标签，用于描述图书特征的关键词列表
 * @param rating    评分，图书的综合评分（0-10分，保留1位小数）
 * @author AiLearn Platform
 */
public record BookInfo(
        @JsonPropertyDescription("书名，图书的完整标题")
        String title,

        @JsonPropertyDescription("作者，图书的作者姓名，多个作者用逗号分隔")
        String author,

        @JsonPropertyDescription("ISBN编号，图书的国际标准书号（13位或10位）")
        String isbn,

        @JsonPropertyDescription("出版社，图书的出版单位名称")
        String publisher,

        @JsonPropertyDescription("出版年份，图书首次出版的年份（4位数字）")
        int publishYear,

        @JsonPropertyDescription("类型/分类，图书的题材分类，如：小说、科技、历史、哲学、经济、计算机等")
        String genre,

        @JsonPropertyDescription("页数，图书的总页数")
        int pages,

        @JsonPropertyDescription("价格，图书的定价（单位：元人民币）")
        double price,

        @JsonPropertyDescription("内容简介，图书的内容概要、核心主题和主要观点介绍，100-300字")
        String summary,

        @JsonPropertyDescription("标签，用于描述图书特征、主题、受众的关键词列表，如：经典、入门、畅销等")
        List<String> tags,

        @JsonPropertyDescription("评分，图书的综合评分，范围0.0-10.0，保留1位小数，基于读者评价和专业书评")
        double rating
) {}
