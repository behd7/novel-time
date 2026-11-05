package com.novel.core.community.application.view;

import java.time.LocalDateTime;

/**
 * 帖子评论视图。
 *
 * <p>{@code parentCommentId} 为空表示直接评论帖子；有值表示楼中楼，
 * 同时带出被回复者的昵称，前端不必再为每条回复发一次用户查询。
 */
public record PostCommentView(
        long id,
        long userId,
        String nickname,
        String avatarKey,
        String content,
        Long parentCommentId,
        String parentNickname,
        int likeCount,
        boolean liked,
        LocalDateTime createdAt
) {
}
