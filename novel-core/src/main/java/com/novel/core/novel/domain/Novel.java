package com.novel.core.novel.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 小说聚合根（只读视图形态）。
 *
 * <p>这里的 {@code wordCount} / {@code chapterCount} / {@code latestChapterId} 都是**冗余字段**，
 * 由导入时一次算好写死。为什么不实时 {@code count(*)}：分类列表一页 20 本书，
 * 每本都去数一遍章节，就是 20 次聚合查询，而章节数是几乎不变的数据。
 */
public record Novel(
        Long id,
        String title,
        String authorName,
        String coverKey,
        Long categoryId,
        int status,
        String intro,
        int wordCount,
        int chapterCount,
        Long latestChapterId,
        LocalDateTime latestUpdateAt,
        int ratingCount,
        BigDecimal avgRating,
        long viewCount,
        int favoriteCount,
        int reviewCount,
        LocalDateTime createdAt
) {

    /** 0 连载中 / 1 已完结。 */
    public static final int STATUS_ONGOING = 0;
    public static final int STATUS_FINISHED = 1;

    public boolean isFinished() {
        return status == STATUS_FINISHED;
    }
}
