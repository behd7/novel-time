package com.novel.core.search.application;

/**
 * 搜索结果的排序方式。
 *
 * <p>和列表接口的 {@code NovelSort} 是两套：那边排的是数据库里的行，这边排的是索引里的文档，
 * 而且多出一个"相关度"——它只有检索引擎才给得出来，数据库那边根本没有对应物。
 *
 * <p>合法值写死在这里，非法值抛 {@link IllegalArgumentException}。
 * 不抛业务异常，是因为"传了个不存在的排序名"重试一百次也不会变好，属于协议错误，
 * 该走 400 而不是 200 + 业务码。
 */
public enum SearchSort {

    /** 相关度（默认） */
    RELEVANCE,
    /** 最近更新 */
    LATEST,
    /** 最热 */
    HOT,
    /** 评分最高 */
    RATING,
    /** 字数最多 */
    WORD_COUNT;

    public static SearchSort from(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        // 前端两种写法都认：wordCount / word_count
        String normalized = value.replace("_", "").trim();
        for (SearchSort sort : values()) {
            if (sort.name().replace("_", "").equalsIgnoreCase(normalized)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("不支持的搜索排序方式: " + value);
    }
}
