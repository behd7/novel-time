package com.novel.core.community.application.view;

import java.time.LocalDateTime;

/**
 * 帖子列表视图。
 *
 * <p>只带摘要不带正文：列表一页 20 条，把整篇帖子都塞进来没有任何意义。
 * {@code liked} 由服务端按当前登录人批量算好，未登录恒为 false。
 */
public record PostView(
        long id,
        long userId,
        String nickname,
        String avatarKey,
        String title,
        String summary,
        String coverKey,
        long viewCount,
        int likeCount,
        int commentCount,
        boolean liked,
        LocalDateTime createdAt
) {
}
