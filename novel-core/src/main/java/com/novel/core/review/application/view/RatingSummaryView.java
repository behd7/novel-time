package com.novel.core.review.application.view;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 评分汇总。
 *
 * @param avgRating    平均分，保留两位
 * @param ratingCount  参与人数
 * @param myScore      当前用户的评分，未登录或没打分为 {@code null}
 * @param distribution 星级分布，键 1..5、值为人数；没有的星级不出现
 */
public record RatingSummaryView(
        BigDecimal avgRating,
        int ratingCount,
        Integer myScore,
        Map<Integer, Long> distribution
) {
}
