package com.novel.core.review.domain;

import java.time.LocalDateTime;

/**
 * 书评（书级评论区的主楼）。
 *
 * <p>{@code score} 可空：用户可以只留言不打分，也可以发评论的同时顺手打星。
 * 真正决定平均分的是 {@code novel_rating} 表，不是这里的字段——
 * 否则同一份评分会出现两个来源，迟早对不上。
 */
public record Review(
        Long id,
        Long novelId,
        Long userId,
        String content,
        Integer score,
        int likeCount,
        int replyCount,
        LocalDateTime createdAt
) {
}
