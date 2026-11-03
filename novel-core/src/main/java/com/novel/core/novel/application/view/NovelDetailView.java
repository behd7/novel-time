package com.novel.core.novel.application.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 详情页视图。
 *
 * <p>比列表多了"最后一章是第几章、叫什么"，是为了让前端能直接渲染「继续阅读」按钮，
 * 而不必再拉一次目录去数总共有几章。
 */
public record NovelDetailView(
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
        Integer latestChapterSeq,
        String latestChapterTitle,
        LocalDateTime latestUpdateAt
) {
}
