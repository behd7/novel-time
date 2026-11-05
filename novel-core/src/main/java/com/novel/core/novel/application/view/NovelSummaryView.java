package com.novel.core.novel.application.view;

import com.novel.core.novel.domain.Novel;

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

    /**
     * 从聚合根投影出摘要。
     *
     * <p>放在这里而不是各自 service 里各写一份：榜单卡片和列表卡片必须是同一份投影，
     * 两处各写一遍的结果是"榜单里有的字段，列表里没有"，而这种不一致没人会主动去修。
     */
    public static NovelSummaryView from(Novel novel) {
        return new NovelSummaryView(
                novel.id(), novel.title(), novel.authorName(), novel.coverKey(), novel.categoryId(),
                novel.status(), novel.intro(), novel.wordCount(), novel.chapterCount(),
                novel.avgRating(), novel.ratingCount(), novel.viewCount(), novel.favoriteCount(),
                novel.latestUpdateAt());
    }
}
