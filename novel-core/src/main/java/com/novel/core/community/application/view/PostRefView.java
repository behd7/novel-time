package com.novel.core.community.application.view;

import java.math.BigDecimal;

/**
 * 引用小说卡片。
 *
 * <p>后端只给结构化数据，不拼 HTML：正文里的 {@code refText} 由前端替换成卡片。
 * 这样卡片样式、XSS 转义都不必耦合进接口。
 */
public record PostRefView(
        String refText,
        long novelId,
        String title,
        String authorName,
        String coverKey,
        BigDecimal avgRating
) {
}
