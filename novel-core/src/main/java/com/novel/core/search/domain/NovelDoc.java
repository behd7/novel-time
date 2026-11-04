package com.novel.core.search.domain;

/**
 * 写进搜索索引的一本书。
 *
 * <p>它是"搜索结果要展示什么"的快照，不是数据库实体的镜像：{@code ratingSum}、
 * {@code hotScore} 这类内部聚合没有进来的必要，而分类名这种要展示、又不该在查询时
 * join 的字段反而要冗余一份。
 *
 * <p>时间字段用字符串而不是 {@code LocalDateTime}：索引里存的就是应用侧统一格式的
 * {@code yyyy-MM-dd HH:mm:ss}，直接透传省掉一层时区/格式转换，也避免序列化器
 * 悄悄按时间戳写进去导致 ES 拒绝建文档。
 */
public record NovelDoc(
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
        double avgRating,
        int ratingCount,
        long viewCount,
        double hotScore,
        String latestUpdateAt,
        String createdAt
) {
}
