package com.novel.core.reading.domain;

import java.time.LocalDateTime;

/**
 * 书架条目：收藏 + 阅读进度合二为一。
 *
 * <p>很多项目把"收藏"和"阅读进度"拆两张表，结果书架列表要 join 一次才能显示"读到第几章"，
 * 而这两件事的生命周期本来就是一致的（取消收藏自然丢掉进度），所以放一张表。
 */
public record BookshelfItem(
        long userId,
        long novelId,
        Long groupId,
        int lastChapterSeq,
        LocalDateTime lastReadAt,
        LocalDateTime createdAt
) {
}
