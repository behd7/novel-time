package com.novel.core.community.domain;

import java.time.LocalDateTime;

/**
 * 社区帖子。
 *
 * <p>{@code content} 里的引用处存的是占位符 {@code [[ref:0]]}，真实的小说关系在
 * {@link PostRef} 里。这样小说改名、换封面时卡片自动跟着变，正文里也不会有可伪造的外链。
 */
public record Post(
        Long id,
        Long userId,
        String title,
        String content,
        String coverKey,
        long viewCount,
        int likeCount,
        int commentCount,
        LocalDateTime createdAt
) {
}
