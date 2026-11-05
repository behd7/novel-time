package com.novel.core.novel.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 小说聚合根（只读视图形态）。
 *
 * <p>这里的 {@code wordCount} / {@code chapterCount} / {@code latestChapterId} 都是**冗余字段**，
 * 由导入时一次算好写死。为什么不实时 {@code count(*)}：分类列表一页 20 本书，
 * 每本都去数一遍章节，就是 20 次聚合查询，而章节数是几乎不变的数据。
 *
 * <p>{@code ratingSum} 一并带出来是给评分对账用的：判断"聚合值有没有变"必须比总分和人数，
 * 拿四舍五入后的平均分去比会漏掉"两个人各改一分、平均分不变"这类修正。
 *
 * <p>另外两个字段各有用途：{@code hotScore} 是热度分（M5 之前恒为 0，搜索的"最热"排序
 * 先按它排、取不到有效值时落到次级阅读数）；{@code version} 是搜索索引的同步版本号，
 * 随内容变更自增，消费端据此丢弃迟到的旧消息——全量重建也要用它，
 * 否则重建写进去的文档版本和增量写的对不上。
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
        long ratingSum,
        int ratingCount,
        BigDecimal avgRating,
        long viewCount,
        int favoriteCount,
        int reviewCount,
        BigDecimal hotScore,
        long version,
        LocalDateTime createdAt
) {

    /** 0 连载中 / 1 已完结。 */
    public static final int STATUS_ONGOING = 0;
    public static final int STATUS_FINISHED = 1;

    public boolean isFinished() {
        return status == STATUS_FINISHED;
    }
}
