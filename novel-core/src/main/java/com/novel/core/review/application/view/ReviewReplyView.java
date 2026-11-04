package com.novel.core.review.application.view;

import java.time.LocalDateTime;

/**
 * 楼中楼视图。
 *
 * @param parentReplyId  被回复的那条回复，直接回复主楼时为 {@code null}
 * @param parentNickname 被回复者昵称，界面显示成"回复 @某某"
 */
public record ReviewReplyView(
        long id,
        long userId,
        String nickname,
        String avatarKey,
        String content,
        Long parentReplyId,
        String parentNickname,
        int likeCount,
        boolean liked,
        LocalDateTime createdAt
) {
}
