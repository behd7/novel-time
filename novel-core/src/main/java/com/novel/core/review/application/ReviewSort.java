package com.novel.core.review.application;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;

/**
 * 书评列表排序。
 *
 * <p>只做两种，都能走索引：最新按创建时间倒序，最热按"点赞数 + 时间衰减"排。
 * 最热没有直接用 {@code like_count DESC}，否则一条十年前被点赞的老帖会永远压在顶上。
 */
public enum ReviewSort {

    LATEST,
    HOT;

    public static ReviewSort from(String value) {
        if (value == null || value.isBlank()) {
            return HOT;
        }
        for (ReviewSort sort : values()) {
            if (sort.name().equalsIgnoreCase(value)) {
                return sort;
            }
        }
        throw new BizException(ResultCode.BAD_REQUEST, "不支持的排序方式: " + value);
    }
}
