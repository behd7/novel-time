package com.novel.core.rank.domain;

/**
 * 榜单快照里的一行。
 *
 * <p>{@code rankNo} 是从 1 开始的连续名次，不是分数排名——并列时按 novelId 升序拆开，
 * 因为分页要靠它做稳定的游标，允许并列会让"第 2 页"出现重复或漏项。
 */
public record RankEntry(long novelId, int rankNo, double score) {
}
