package com.novel.core.community.application;

/**
 * 帖子列表排序。
 *
 * <p>只做两种：最新按创建时间倒序，最热按"点赞 + 评论 + 时间衰减"排。
 * 最热没有直接按点赞数倒序，否则一条早年爆款会永远压在新帖上面。
 */
public enum PostSort {

    LATEST,
    HOT;

    public static PostSort from(String value) {
        if (value == null || value.isBlank()) {
            return HOT;
        }
        for (PostSort sort : values()) {
            if (sort.name().equalsIgnoreCase(value)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("不支持的排序方式: " + value);
    }
}
