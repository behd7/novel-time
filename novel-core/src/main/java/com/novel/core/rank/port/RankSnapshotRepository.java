package com.novel.core.rank.port;

import com.novel.core.rank.domain.RankEntry;
import com.novel.core.rank.domain.RankPeriod;

import java.time.LocalDate;
import java.util.List;

/**
 * 归档层：把结算结果落成 {@code novel_rank} 快照。
 *
 * <p>写接口只有 {@link #replace} 一个，语义是<b>整份替换</b>，不是逐条 upsert。
 * 榜单每次结算名次会整体平移，增量 upsert 要额外处理"上一轮在榜、这一轮掉出榜"的行，
 * 而快照只有两三百行——删了重插最简单，也不会残留。
 */
public interface RankSnapshotRepository {

    /** 用 {@code entries} 整份替换某周期某日期的快照。 */
    void replace(RankPeriod period, LocalDate rankDate, List<RankEntry> entries);

    /** 读某周期某日期的前 N 名，按 {@code rank_no} 升序。 */
    List<RankEntry> top(RankPeriod period, LocalDate rankDate, int limit);
}
