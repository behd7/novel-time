package com.novel.core.rank.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * 榜单周期。
 *
 * <p>一个枚举同时回答三个问题，且三个答案必须来自同一份规则：
 * <ul>
 *   <li>{@link #rankDate} —— 快照的 {@code rank_date} 落在哪天；</li>
 *   <li>{@link #bucket} —— 实时层 ZSET 的桶叫什么；</li>
 *   <li>{@link #decaying} —— 要不要叠时间衰减。</li>
 * </ul>
 * 三者分开实现最容易出现的错误是"桶按本周算、日期写成今天"——数据上完全看不出来，
 * 只表现为某周的榜少了几天的行为。
 *
 * <p>周按 <b>ISO 8601</b> 算，不是"周日起算"：周一是一周第一天，且一周归属于
 * 包含本周四的那一年。于是元旦可能落在上一年的第 53 周，上年年末也可能落进新年第 1 周。
 * 这不是笔误，是 {@code novel_rank.rank_date} 与 ZSET 桶必须对齐的前提。
 */
public enum RankPeriod {

    /** 日榜：当天滚动，每小时刷新，次日冻结为历史。 */
    DAILY,
    /** 周榜：本周一 00:00 起算。 */
    WEEKLY,
    /** 月榜：本月 1 号起算。 */
    MONTHLY,
    /** 总榜：全时段，读的是 {@code novel} 表的累计列，不依赖 ZSET。 */
    ALL;

    /**
     * 总榜的哨兵日期。
     *
     * <p>{@code novel_rank} 的唯一键是 {@code (period, rank_date, novel_id)}，
     * 总榜也必须给一个日期才能落进同一张表；用一个永远不可能与真实日期冲突的值，
     * 比"用今天的日期"更安全——后者会让总榜每天多出一份快照。
     */
    public static final LocalDate ALL_RANK_DATE = LocalDate.of(1970, 1, 1);

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final WeekFields ISO_WEEKS = WeekFields.ISO;

    /**
     * 解析查询参数里的周期名。
     *
     * <p>空值与非法值都抛 {@link IllegalArgumentException}，走 400 而不是 200 + 业务码：
     * 传了个不存在的周期，重试一次也不会变好，属于协议错误。这里刻意不设默认值——
     * {@code period} 是必填参数，悄悄当成日榜会让前端把参数写错很久都发现不了。
     */
    public static RankPeriod from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("榜单周期不能为空");
        }
        String normalized = value.trim();
        for (RankPeriod period : values()) {
            if (period.name().equalsIgnoreCase(normalized)) {
                return period;
            }
        }
        throw new IllegalArgumentException("不支持的榜单周期: " + value);
    }

    /** 是否叠时间衰减。只有总榜需要：周期榜的窗口本身就是时效。 */
    public boolean decaying() {
        return this == ALL;
    }

    /** 该日期所属周期的起点，用作 {@code rank_date}。 */
    public LocalDate rankDate(LocalDate day) {
        return switch (this) {
            case DAILY -> day;
            case WEEKLY -> day.with(ISO_WEEKS.dayOfWeek(), 1);
            case MONTHLY -> day.withDayOfMonth(1);
            case ALL -> ALL_RANK_DATE;
        };
    }

    /**
     * 该日期所属周期的桶名，用作 ZSET key 的后缀。
     *
     * <p>必须先归到周期起点再格式化：直接拿当天日期格式化，同一周的两天会得到两个桶。
     */
    public String bucket(LocalDate day) {
        LocalDate anchor = rankDate(day);
        return switch (this) {
            case DAILY -> anchor.format(DAY_FORMAT);
            case WEEKLY -> String.format(Locale.ROOT, "%04dW%02d",
                    anchor.get(ISO_WEEKS.weekBasedYear()),
                    anchor.get(ISO_WEEKS.weekOfWeekBasedYear()));
            case MONTHLY -> String.format(Locale.ROOT, "%04d%02d", anchor.getYear(), anchor.getMonthValue());
            case ALL -> "all";
        };
    }
}
