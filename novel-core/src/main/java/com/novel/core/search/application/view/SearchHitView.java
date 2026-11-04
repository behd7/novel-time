package com.novel.core.search.application.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 搜索命中项。
 *
 * <p>比列表接口多了 {@code highlight}：高亮片段是引擎算出来的，不能靠前端拿原字段自己找关键字——
 * 中文分词后命中位置和字面匹配经常不是一回事（搜"修真"可能命中"修仙"的同义词扩展）。
 */
public record SearchHitView(
        long novelId,
        String title,
        String authorName,
        String coverKey,
        long categoryId,
        String categoryName,
        int status,
        String intro,
        int wordCount,
        int chapterCount,
        BigDecimal avgRating,
        int ratingCount,
        long viewCount,
        LocalDateTime latestUpdateAt,
        HighlightView highlight
) {

    /** 三个可高亮字段，未命中时为 null。 */
    public record HighlightView(String title, String authorName, String intro) {
    }
}
