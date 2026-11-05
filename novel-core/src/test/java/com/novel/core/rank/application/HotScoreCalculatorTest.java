package com.novel.core.rank.application;

import com.novel.core.rank.domain.RankPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

/**
 * 热度公式的边界。
 *
 * <p>公式本身只有一行，但每一处都在防一种"夜里三点才会发现"的事故：
 * 计数为负时开出 NaN、时钟回拨时算出负数天数再开方、总榜除以 0。
 * 这些值一旦写进 {@code DECIMAL} 列会变成 0 或者直接抛异常，而榜单是定时任务，
 * 出错的现场往往在第二天才被人注意到。
 *
 * <p>另一个重点是"对数平滑"到底平滑在哪。线性公式下 1000 次阅读和 2000 次阅读差 1000 分，
 * 对数下只差 log10(2) ≈ 0.301——所以这里断言的是"翻倍带来的增量恒在 0.28~0.31 之间"，
 * 而不是"增量越来越小"（那是个常见但错误的直觉）。
 */
class HotScoreCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);
    private static final LocalDateTime LATEST = LocalDateTime.of(2026, 11, 4, 12, 0);

    /** 阅读 1 / 收藏 3 / 书评 2，衰减指数 0.5，偏移 2 天。 */
    private final HotScoreCalculator calculator = new HotScoreCalculator(1, 3, 2, 0.5, 2.0);

    @Test
    @DisplayName("同一本书，行为越多分越高")
    void moreBehavior_scoresHigher() {
        double low = calculator.score(RankPeriod.DAILY, 10, LATEST, NOW);
        double mid = calculator.score(RankPeriod.DAILY, 100, LATEST, NOW);
        double high = calculator.score(RankPeriod.DAILY, 1000, LATEST, NOW);

        assertThat(mid).isGreaterThan(low);
        assertThat(high).isGreaterThan(mid);
    }

    @Test
    @DisplayName("对数平滑：翻一倍只多约 0.3 分，与基数无关")
    void logSmoothing() {
        // 线性公式下这三次翻倍会分别多出 10 / 100 / 1000 分，页面分数会彻底失真
        assertThat(delta(10, 20)).isCloseTo(0.2809, offset(0.0001));
        assertThat(delta(100, 200)).isCloseTo(0.2989, offset(0.0001));
        assertThat(delta(1000, 2000)).isCloseTo(0.3008, offset(0.0001));
    }

    @Test
    @DisplayName("周期榜不衰减：同样行为量下，三个月没更新的书与刚更新的书同分")
    void periodRank_ignoresFreshness() {
        double stale = calculator.score(RankPeriod.DAILY, 500, NOW.minusDays(90), NOW);
        double fresh = calculator.score(RankPeriod.DAILY, 500, NOW, NOW);

        assertThat(stale).isEqualTo(fresh);
    }

    @Test
    @DisplayName("总榜叠时间衰减：同样行为量，更新得早的书排在后面")
    void allRank_decaysWithAge() {
        double stale = calculator.score(RankPeriod.ALL, 500, NOW.minusDays(90), NOW);
        double fresh = calculator.score(RankPeriod.ALL, 500, NOW, NOW);

        assertThat(stale).isLessThan(fresh);
        // log10(501) / sqrt(0 + 2) = 2.6998 / 1.4142
        assertThat(fresh).isCloseTo(1.9091, offset(0.0001));
        // log10(501) / sqrt(90 + 2) = 2.6998 / 9.5917
        assertThat(stale).isCloseTo(0.2815, offset(0.0001));
    }

    @Test
    @DisplayName("衰减指数为 0 时，总榜退化成与周期榜同一个公式")
    void zeroExponent_matchesPeriodFormula() {
        HotScoreCalculator noDecay = new HotScoreCalculator(1, 3, 2, 0, 2.0);

        assertThat(noDecay.score(RankPeriod.ALL, 500, NOW.minusDays(90), NOW))
                .isEqualTo(noDecay.score(RankPeriod.DAILY, 500, NOW.minusDays(90), NOW));
    }

    @Test
    @DisplayName("没有行为就是 0 分，不产生 NaN")
    void zeroRaw_scoresZero() {
        for (RankPeriod period : RankPeriod.values()) {
            assertThat(calculator.score(period, 0, LATEST, NOW)).isZero();
        }
    }

    @Test
    @DisplayName("负数行为量（取消收藏）夹到 0，不开出 NaN")
    void negativeRaw_clamped() {
        double score = calculator.score(RankPeriod.DAILY, -50, LATEST, NOW);

        assertThat(score).isNotNaN();
        assertThat(score).isZero();
    }

    @Test
    @DisplayName("更新时间晚于当前时间（时钟回拨）不产生 NaN")
    void futureUpdate_doesNotProduceNaN() {
        double score = calculator.score(RankPeriod.ALL, 500, NOW.plusDays(10), NOW);

        assertThat(score).isNotNaN();
        // 夹到 0 天后与新书同分
        assertThat(score).isEqualTo(calculator.score(RankPeriod.ALL, 500, NOW, NOW));
    }

    @Test
    @DisplayName("从未更新过的书按当前时间算，不抛空指针")
    void nullUpdateTreatedAsNow() {
        double score = calculator.score(RankPeriod.ALL, 500, null, NOW);

        assertThat(score).isEqualTo(calculator.score(RankPeriod.ALL, 500, NOW, NOW));
    }

    @Test
    @DisplayName("raw 按权重把三个计数合成一个数")
    void raw_appliesWeights() {
        // 10×1 + 5×3 + 3×2 = 31
        assertThat(calculator.raw(10, 5, 3)).isEqualTo(31);
        assertThat(calculator.raw(0, 0, 0)).isZero();
        // 负数计数不能把加权和拉成负数
        assertThat(calculator.raw(-1, 0, 0)).isZero();
    }

    @Test
    @DisplayName("衰减偏移必须为正，否则年龄为 0 时除以 0")
    void invalidOffset_rejected() {
        assertThatThrownBy(() -> new HotScoreCalculator(1, 3, 2, 0.5, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("偏移");
        assertThatThrownBy(() -> new HotScoreCalculator(1, 3, 2, 0.5, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("衰减指数为负直接拒绝：那会让老书越老越高")
    void negativeExponent_rejected() {
        assertThatThrownBy(() -> new HotScoreCalculator(1, 3, 2, -0.5, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 行为量翻倍带来的分数增量。 */
    private double delta(long from, long to) {
        return calculator.score(RankPeriod.DAILY, to, LATEST, NOW)
                - calculator.score(RankPeriod.DAILY, from, LATEST, NOW);
    }
}
