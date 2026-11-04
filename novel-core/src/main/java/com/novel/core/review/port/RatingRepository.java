package com.novel.core.review.port;

import java.util.Map;
import java.util.Optional;

/**
 * 评分仓储端口。
 *
 * <p>注意这里**没有** "重新计算平均分" 的方法：平均分是 {@code novel} 表上的冗余字段，
 * 由 {@link com.novel.core.novel.port.NovelRepository#addRating} 原子维护。
 * 评分表只负责"谁打了多少分"这一件事，聚合结果不重复存两份。
 */
public interface RatingRepository {

    Optional<Integer> findScore(long novelId, long userId);

    /** 插入或覆盖，正式的唯一性由 {@code uk_novel_user} 保证。 */
    void save(long novelId, long userId, int score);

    void delete(long novelId, long userId);

    /** 星级分布：score → 人数。用于详情页的评分直方图。 */
    Map<Integer, Long> distribution(long novelId);

    /** 从明细重算某个聚合值，供对账任务使用。 */
    Aggregate aggregate(long novelId);

    /** 分批扫描有评分的小说 id，供对账任务遍历，避免一次性把全表读进内存。 */
    java.util.List<Long> pageNovelIdsWithRating(long lastNovelId, int limit);

    record Aggregate(long sum, int count) {
    }
}
