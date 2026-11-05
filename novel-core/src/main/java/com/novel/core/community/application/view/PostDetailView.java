package com.novel.core.community.application.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子详情视图。
 *
 * <p>比列表多两样东西：完整正文，以及按 {@code refText} 可以替换成卡片的小说列表。
 * 正文里保留占位符而不是已替换的文本，前端才能按自己的组件渲染。
 */
public record PostDetailView(
        long id,
        long userId,
        String nickname,
        String avatarKey,
        String title,
        String content,
        String coverKey,
        long viewCount,
        int likeCount,
        int commentCount,
        boolean liked,
        List<PostRefView> refs,
        LocalDateTime createdAt
) {
}
