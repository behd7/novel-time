package com.novel.core.community.application.view;

import java.time.LocalDateTime;

/**
 * 帖子评论视图。
 *
 * <p>{@code rootId} 是两层归并的依据：直接评论帖子为 0，回复一律归到第一层评论的 id。
 * {@code parentCommentId} 为空表示直接评论帖子；有值表示楼中楼，同时带出被回复者的昵称。
 * 父评论被删后 {@code parentDeleted=true}、昵称返回 null，前端显示"该评论已删除"，
 * 但 {@code parentCommentId} 与 {@code rootId} 仍然保留，回复不会退化成顶级评论。
 */
public record PostCommentView(
        long id,
        long userId,
        String nickname,
        String avatarKey,
        String content,
        long rootId,
        Long parentCommentId,
        String parentNickname,
        boolean parentDeleted,
        int likeCount,
        boolean liked,
        LocalDateTime createdAt
) {
}
