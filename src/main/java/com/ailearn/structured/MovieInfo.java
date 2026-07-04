package com.ailearn.structured;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 电影信息结构化输出类
 * 用于从非结构化文本中提取电影的关键信息，
 * Spring AI会自动将此类的字段和@JsonPropertyDescription注解转换为JSON Schema，
 * 指导大语言模型按指定格式输出结构化数据。
 *
 * @param title        电影名称，电影的中文/英文正式名称
 * @param director     导演，电影的主要导演姓名
 * @param actors       主演，主要演员名单列表
 * @param genre        类型/类型，电影的题材分类，如：科幻、动作、爱情、喜剧、恐怖、剧情等
 * @param year         上映年份，电影首次公映的年份（4位数字）
 * @param duration     片长，电影的时长（单位：分钟）
 * @param country      制片国家/地区，电影的出品国家或地区
 * @param language     语言，电影的主要对白语言
 * @param rating       评分，电影的综合评分（0-10分，保留1位小数）
 * @param boxOffice    票房，电影的全球总票房（单位：美元）
 * @param description  剧情简介，电影的故事情节概要（100-300字）
 * @param tags         标签，描述电影特征、风格、受众的关键词列表
 * @author AiLearn Platform
 */
public record MovieInfo(
        @JsonPropertyDescription("电影名称，电影的中文正式名称，如有外文名可在括号内标注")
        String title,

        @JsonPropertyDescription("导演，电影的主要导演姓名，多位导演用逗号分隔")
        String director,

        @JsonPropertyDescription("主演，主要演员名单列表，按戏份重要性排序，列出3-5位主要演员")
        List<String> actors,

        @JsonPropertyDescription("类型/类型，电影的题材分类，可多选，如：科幻、动作、爱情、喜剧、恐怖、剧情、动画、纪录片等")
        List<String> genre,

        @JsonPropertyDescription("上映年份，电影首次公映的年份（4位数字）")
        int year,

        @JsonPropertyDescription("片长，电影的时长（单位：分钟）")
        int duration,

        @JsonPropertyDescription("制片国家/地区，电影的主要出品国家或地区")
        String country,

        @JsonPropertyDescription("语言，电影的主要对白语言")
        String language,

        @JsonPropertyDescription("评分，电影的综合评分，范围0.0-10.0，保留1位小数，参考IMDB、豆瓣等主流平台评分")
        double rating,

        @JsonPropertyDescription("票房，电影的全球总票房（单位：美元），如无法获取可填0")
        long boxOffice,

        @JsonPropertyDescription("剧情简介，电影的故事情节概要，100-300字，不包含关键剧透")
        String description,

        @JsonPropertyDescription("标签，描述电影特征、风格、受众的关键词列表，如：经典、治愈、悬疑、高智商、视觉盛宴等")
        List<String> tags
) {}
