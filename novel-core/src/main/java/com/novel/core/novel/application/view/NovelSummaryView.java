package com.novel.core.novel.application.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 列表页用的小说摘要。刻意不含正文与目录，避免列表接口变重。 */
public record NovelSummaryView(
        long id,
        String title,
        String authorName,
        String coverKey,
        Long categoryId,
        int status,
        String intro,
        int wordCount,
        int chapterCount,
        BigDecimal avgRating,
        int ratingCount,
        long viewCount,
        int favoriteCount,
        LocalDateTime latestUpdateAt
) {
}
