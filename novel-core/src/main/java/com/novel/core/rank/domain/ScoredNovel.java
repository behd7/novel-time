package com.novel.core.rank.domain;

/**
 * 实时层里的一个候选：某本书在某个周期桶里攒了多少行为量。
 *
 * <p>这里只有原始行为量，还没有分数——分数要等结算时结合更新时间去算，
 * 因为那一步需要 {@code novel} 表的数据，而实时层不该为了加分去查库。
 */
public record ScoredNovel(long novelId, long score) {
}
