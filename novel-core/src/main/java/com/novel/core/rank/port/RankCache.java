package com.novel.core.rank.port;

import com.novel.core.rank.application.view.RankItemView;
import com.novel.core.rank.domain.RankPeriod;

import java.time.LocalDate;
import java.util.List;

/**
 * 读取层缓存。
 *
 * <p>缓存的 key 必须带上周期日期：日榜的 key 里含 {@code 20261105}，跨天自然失配，
 * 不需要任何失效逻辑。这是"榜单缓存"能做得这么简单的原因——
 * 绝大多数缓存出问题，都是因为 key 里少了一个维度。
 *
 * <p>{@link #evict} 由结算任务在替换快照后调用，保证"刷新了但榜单还是旧的"不会超过一次刷新的间隔。
 */
public interface RankCache {

    /** 未命中返回 {@code null}，不是空列表——空列表会被当成"确实没有榜"而不再回源。 */
    List<RankItemView> get(RankPeriod period, LocalDate rankDate);

    void put(RankPeriod period, LocalDate rankDate, List<RankItemView> items);

    void evict(RankPeriod period, LocalDate rankDate);
}
