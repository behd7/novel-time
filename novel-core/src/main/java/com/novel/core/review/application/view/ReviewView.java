package com.novel.core.review.application.view;

import java.time.LocalDateTime;

/**
 * 书评视图。
 *
 * <p>{@code liked} 是"当前登录用户点没点过"，未登录时恒为 false。
 * 这个字段必须由服务端批量算出来——让前端对每条评论再发一次请求，20 条就是 20 个请求。
 */
public record ReviewView(
        long id,
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
