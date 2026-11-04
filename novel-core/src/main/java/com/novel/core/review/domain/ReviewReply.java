package com.novel.core.review.domain;

import java.time.LocalDateTime;

/**
 * 书评下的回复。
 *
 * <p>只渲染两层：{@code rootId} 指向所属主楼回复（0 表示直接回复主楼），
 * {@code parentReplyId} 指向被回复的那条，用来显示"回复 @谁"。
 * 无论嵌套多深，界面上都拍平成两层——三层以上的缩进在手机屏幕上没法看。
 */
public record ReviewReply(
        Long id,
        Long reviewId,
        Long userId,
        long rootId,
        long parentReplyId,
        String content,
        int likeCount,
        LocalDateTime createdAt
) {
}
