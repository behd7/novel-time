package com.novel.core.reading.application.view;

import java.time.LocalDateTime;

/**
 * 书架条目视图。
 *
 * @param hasUpdate   书架里最有用的一列：{@code 已更新到第 N 章 > 我读到第 M 章} 就为 true，
 *                    前端据此打红点。用章节数比大小即可，因为 {@code seq} 是连续的。
 * @param totalChapters 总章数，配合 {@code lastChapterSeq} 也能算出读了多少进度
 */
public record BookshelfView(
        long novelId,
        String title,
        String authorName,
        String coverKey,
        Long categoryId,
        int totalChapters,
        int lastChapterSeq,
        boolean hasUpdate,
        LocalDateTime lastReadAt,
        LocalDateTime addedAt
) {
}
