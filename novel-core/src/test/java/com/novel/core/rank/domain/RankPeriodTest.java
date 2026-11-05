package com.novel.core.rank.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 榜单周期的归属规则。
 *
 * <p>这个枚举同时回答三个问题：ZSET 的桶叫什么、快照的 {@code rank_date} 落在哪天、
 * 要不要叠时间衰减。三件事必须由同一份规则推导，否则结算时会出现
 * "桶按本周算、日期写成今天"的错位，而错位在数据上是看不出来的——只会表现为
 * 某周的榜单少了几天的数据。
 *
 * <p>最容易写错的是 ISO 周的跨年：元旦可能属于上一年的第 53 周，
 * 也可能把上一年的 12 月 29 日算进新一年的第 1 周。这里逐条钉死。
 */
class RankPeriodTest {

    private static final LocalDate THURSDAY = LocalDate.of(2026, 11, 5);

    @Test
    @DisplayName("周期名不区分大小写，两端空白被裁掉")
    void from_normalizes() {
        assertThat(RankPeriod.from("daily")).isEqualTo(RankPeriod.DAILY);
        assertThat(RankPeriod.from(" WEEKLY ")).isEqualTo(RankPeriod.WEEKLY);
        assertThat(RankPeriod.from("All")).isEqualTo(RankPeriod.ALL);
    }

    @Test
    @DisplayName("未知周期抛参数异常：走 400，不是 200 + 业务码")
    void from_unknown_rejected() {
        assertThatThrownBy(() -> RankPeriod.from("hourly"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的榜单周期");
    }

    @Test
    @DisplayName("空周期直接拒绝：period 是必填参数，不悄悄给默认值")
    void from_blank_rejected() {
        assertThatThrownBy(() -> RankPeriod.from(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> RankPeriod.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("日榜：rank_date 就是当天，桶按 yyyyMMdd")
    void daily() {
        assertThat(RankPeriod.DAILY.rankDate(THURSDAY)).isEqualTo(THURSDAY);
        assertThat(RankPeriod.DAILY.bucket(THURSDAY)).isEqualTo("20261105");
    }

    @Test
    @DisplayName("周榜：周中任意一天都归到本周一，同一周共用一个桶")
    void weekly_anchorsToMonday() {
        LocalDate sunday = LocalDate.of(2026, 11, 8);
        LocalDate nextMonday = LocalDate.of(2026, 11, 9);

        assertThat(RankPeriod.WEEKLY.rankDate(THURSDAY)).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(RankPeriod.WEEKLY.rankDate(sunday)).isEqualTo(LocalDate.of(2026, 11, 2));

        assertThat(RankPeriod.WEEKLY.bucket(THURSDAY)).isEqualTo("2026W45");
        assertThat(RankPeriod.WEEKLY.bucket(sunday)).isEqualTo("2026W45");
        assertThat(RankPeriod.WEEKLY.bucket(nextMonday)).isEqualTo("2026W46");
    }

    @Test
    @DisplayName("周榜跨年：元旦可能属于上一年最后一周，上一年年末也可能属于新年第一周")
    void weekly_crossesYear() {
        // 2027-01-01 是周五，所在周的周四是 2026-12-31，按 ISO 规则属于 2026 年第 53 周
        assertThat(RankPeriod.WEEKLY.bucket(LocalDate.of(2027, 1, 1))).isEqualTo("2026W53");
        assertThat(RankPeriod.WEEKLY.rankDate(LocalDate.of(2027, 1, 1))).isEqualTo(LocalDate.of(2026, 12, 28));

        // 反过来，2025-12-31 所在的周跨进了 2026 年，属于 2026 年第 1 周
        assertThat(RankPeriod.WEEKLY.bucket(LocalDate.of(2025, 12, 31))).isEqualTo("2026W01");
        assertThat(RankPeriod.WEEKLY.rankDate(LocalDate.of(2025, 12, 31))).isEqualTo(LocalDate.of(2025, 12, 29));
    }

    @Test
    @DisplayName("月榜：归到当月 1 号，桶按 yyyyMM")
    void monthly() {
        assertThat(RankPeriod.MONTHLY.rankDate(THURSDAY)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(RankPeriod.MONTHLY.bucket(THURSDAY)).isEqualTo("202611");
        assertThat(RankPeriod.MONTHLY.bucket(LocalDate.of(2026, 12, 31))).isEqualTo("202612");
    }

    @Test
    @DisplayName("总榜：固定哨兵日期与固定桶，与传入日期无关")
    void all_isConstant() {
        assertThat(RankPeriod.ALL.rankDate(THURSDAY)).isEqualTo(LocalDate.of(1970, 1, 1));
        assertThat(RankPeriod.ALL.rankDate(LocalDate.of(2020, 1, 1))).isEqualTo(LocalDate.of(1970, 1, 1));
        assertThat(RankPeriod.ALL.bucket(THURSDAY)).isEqualTo("all");
    }

    @Test
    @DisplayName("只有总榜叠时间衰减：周期榜的窗口本身就是时效")
    void onlyAllDecays() {
        assertThat(RankPeriod.ALL.decaying()).isTrue();
        assertThat(RankPeriod.DAILY.decaying()).isFalse();
        assertThat(RankPeriod.WEEKLY.decaying()).isFalse();
        assertThat(RankPeriod.MONTHLY.decaying()).isFalse();
    }

    @Test
    @DisplayName("桶与日期互为因果：结算时按 rank_date 去重不会串桶")
    void bucketOfRankDateMatchesBucketOfDay() {
        LocalDate day = LocalDate.of(2025, 12, 1);
        for (int i = 0; i < 730; i++, day = day.plusDays(1)) {
            for (RankPeriod period : RankPeriod.values()) {
                assertThat(period.bucket(period.rankDate(day)))
                        .as("%s 在 %s 的桶", period, day)
                        .isEqualTo(period.bucket(day));
            }
        }
    }
}
