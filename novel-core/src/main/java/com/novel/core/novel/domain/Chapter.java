package com.novel.core.novel.domain;

import java.time.LocalDateTime;

/**
 * 章节（目录项，不含正文）。
 *
 * <p>{@code seq} 从 1 开始连续。这个约定很关键：有了它，"第几章"就是可计算的，
 * 上下章不用查相邻记录，直接 {@code seq-1} / {@code seq+1} 即可，目录翻页也能用 offset 算。
 * 代价是删中间章必须重排，但小说平台基本不删章。
 */
public record Chapter(
        Long id,
        Long novelId,
        int seq,
        String title,
        int wordCount,
        boolean free,
        LocalDateTime createdAt
) {
}
