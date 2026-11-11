package com.novel.core.review.application.view;

import java.time.LocalDateTime;

/**
 * 个人主页里的书评条目。
 *
 * <p>比 {@link ReviewView} 多带 {@code novelId / novelTitle}：书评是按书存的，
 * "我的书评"列表必须告诉读者这条评的是哪本书，否则一排正文无法定位。
 */
public record UserReviewView(
        long id,
        long novelId,
        String novelTitle,
        long userId,
        String nickname,
        String avatarKey,
        String content,
        Integer score,
        int likeCount,
        int replyCount,
        boolean liked,
        LocalDateTime createdAt
) {
}
