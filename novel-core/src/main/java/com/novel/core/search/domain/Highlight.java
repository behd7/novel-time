package com.novel.core.search.domain;

/**
 * 高亮片段。
 *
 * <p>每个字段都是"已经拼好标签的字符串"，由搜索引擎生成；为 null 表示该字段没有命中片段。
 * 让引擎负责切片而不是后端自己 {@code substring}，是因为中文分词后的命中位置在后端算不准：
 * 关键词被 IK 切成两个词时，"该从第几个字开始截"只有建索引的那一方知道。
 */
public record Highlight(String title, String authorName, String intro) {

    public static final Highlight EMPTY = new Highlight(null, null, null);
}
