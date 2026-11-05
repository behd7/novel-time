package com.novel.core.rank.port;

import com.novel.core.rank.domain.RankAction;
import com.novel.core.rank.domain.RankPeriod;
import com.novel.core.rank.domain.ScoredNovel;

import java.time.LocalDate;
import java.util.List;

/**
 * 实时层：按周期桶累加行为量。
 *
 * <p>这里刻意<b>不走 MQ</b>。榜单是近似指标，丢一次行为不影响正确性，
 * 而阅读是最高频的写——每读一次详情页就往 outbox 写一行 MySQL，
 * 等于用"不丢事件"的代价去换一个本来就允许有误差的数字，不划算。
 * 反过来说，正因为不走 MQ，这个端口的方法<b>不在事务里</b>，
 * 调用方的事务回滚时这里已经加过的分会留下——对近似指标可以接受。
 *
 * <p>权重（阅读 1 / 收藏 3 / 书评 2）由实现侧决定，端口只表达"发生了什么"。
 * 权重和 {@code HotScoreCalculator} 用的必须是同一份配置，否则日榜和总榜的尺度会不一致。
 */
public interface RankScoreStore {

    /**
     * 记一次阅读。
     *
     * <p>{@code viewerKey} 是"谁在看"，登录用户用 {@code u:{id}}、匿名用 {@code ip:{addr}}。
     * 同一 viewer 同一天同一本书只计一次——没有这层去重，按 F5 就能刷榜。
     */
    void recordView(String viewerKey, long novelId);

    /**
     * 记一次收藏或书评。
     *
     * <p>{@code delta} 为 +1 或 -1，调用方保证幂等（先查存在性再调用），
     * 所以这里不需要去重。取消时记 -1 而不是不管：只加不减的话，
     * "收藏→取消→再收藏"就是一个免费的刷分循环。
     */
    void recordAction(long novelId, RankAction action, int delta);

    /** 取某个周期某个日期的前 N 名，按行为量降序。 */
    List<ScoredNovel> top(RankPeriod period, LocalDate rankDate, int limit);
}
