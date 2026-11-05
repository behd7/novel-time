package com.novel.core.community.domain;

import java.time.LocalDateTime;

/**
 * 帖子评论。
 *
 * <p>和书评回复同构：只存 {@code rootId} 与 {@code parentCommentId}，界面渲染两层。
 * 无论嵌套多深都归并到第一层，查询就不需要递归。
 */
public record PostComment(
        Long id,
        Long postId,
        Long userId,
        long rootId,
        long parentCommentId,
        String content,
        int likeCount,
        LocalDateTime createdAt
) {
}
