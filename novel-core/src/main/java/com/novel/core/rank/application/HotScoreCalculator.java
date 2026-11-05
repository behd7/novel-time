package com.novel.core.rank.application;

import com.novel.core.rank.domain.RankPeriod;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 热度计算器：把"这段时间攒了多少行为"换算成一个可排序的分数。
 *
 * <p>公式只有两行：
 * <pre>
 *   周期榜（日 / 周 / 月） score = log10(1 + raw)
 *   总榜                  score = log10(1 + raw) / (距最近更新天数 + 偏移)^指数
 * </pre>
 * 对数的作用是压缩量级：翻一倍永远只多约 {@code log10(2) ≈ 0.301} 分，
 * 于是一本被首页推荐过的书不会因为多了一个零就永久霸榜。
 *
 * <p>为什么周期榜不叠时间衰减：日榜的窗口只有一天，窗口本身就是时效。
 * 再乘一次衰减等于对"今天更新的书"双重加权，结果是一天之内谁更新谁第一，
 * 而"今天被读得最多"这个真正的信号反而被压掉了。
 *
 * <p>为什么总榜要叠：总榜读的是累计列，一本五年前完结的经典会一直压在新书头上。
 * 用"距最近更新天数"而不是"距上架天数"，是因为连载中的书本来就会一直更新，
 * 我们想奖励的是"还在被人读"，不是"注册得早"。
 *
 * <p>这个类不碰任何 IO，所以上面每一条都能用纯单测钉住。权重与衰减参数由外部注入，
 * core 不认识配置文件。
 */
public final class HotScoreCalculator {

    private static final double MINUTES_PER_DAY = 24 * 60.0;

    private final int viewWeight;
    private final int favoriteWeight;
    private final int reviewWeight;
    private final double decayExponent;
    private final double decayOffsetDays;

    /**
     * @param decayExponent   衰减指数，0 表示不衰减
     * @param decayOffsetDays 衰减偏移（天），必须为正：年龄为 0 时它是分母，不能是 0
     */
    public HotScoreCalculator(int viewWeight, int favoriteWeight, int reviewWeight,
                              double decayExponent, double decayOffsetDays) {
        if (decayExponent < 0) {
            // 负指数会让分母随年龄变小，等于奖励老书，和公式的目的正好相反
            throw new IllegalArgumentException("衰减指数不能为负");
        }
        if (decayOffsetDays <= 0) {
            throw new IllegalArgumentException("衰减偏移必须为正数，否则年龄为 0 时会除以 0");
        }
        this.viewWeight = viewWeight;
        this.favoriteWeight = favoriteWeight;
        this.reviewWeight = reviewWeight;
        this.decayExponent = decayExponent;
        this.decayOffsetDays = decayOffsetDays;
    }

    /**
     * 把三个计数按权重合成一个原始行为量。
     *
     * <p>用同一套权重同时服务两条数据源：周期榜的输入是 ZSET 里已经加过权的分数，
     * 总榜的输入是 {@code novel} 表的三个累计列，走的就是这个方法。
     * 两处若各写一份权重，改配置时必然漏掉一处。
     *
     * <p>每个计数先夹到 0：取消收藏会把计数减到负数，而负的加权和开不了对数。
     */
    public long raw(long viewCount, long favoriteCount, long reviewCount) {
        return Math.max(0, viewCount) * viewWeight
                + Math.max(0, favoriteCount) * favoriteWeight
                + Math.max(0, reviewCount) * reviewWeight;
    }

    /**
     * 算分。
     *
     * @param raw            原始行为量（ZSET 分数或 {@link #raw} 的结果），负值按 0 处理
     * @param latestUpdateAt 最近更新时间，{@code null} 视为"刚刚更新"
     */
    public double score(RankPeriod period, long raw, LocalDateTime latestUpdateAt, LocalDateTime now) {
        double base = Math.log10(1 + Math.max(0, raw));
        if (!period.decaying() || decayExponent == 0) {
            return base;
        }
        double age = ageDays(latestUpdateAt, now);
        return base / Math.pow(age + decayOffsetDays, decayExponent);
    }

    /**
     * 距最近更新的天数。
     *
     * <p>夹到 0 而不是直接取差值：时钟回拨、或者数据里出现了未来的 {@code latest_update_at}，
     * 都会算出负数，再开方就是 NaN。NaN 写进 {@code DECIMAL} 列会静默变成 0，
     * 一本书就这么从榜上消失了，而且没有任何报错。
     */
    private double ageDays(LocalDateTime latestUpdateAt, LocalDateTime now) {
        if (latestUpdateAt == null) {
            // 还没导入章节的新书：按"刚刚"算，它的行为量本来就是 0，不会被顶上去
            return 0;
        }
        long minutes = Duration.between(latestUpdateAt, now).toMinutes();
        return Math.max(0, minutes) / MINUTES_PER_DAY;
    }
}
