package com.novel.core.rank.application;

import com.novel.common.result.PageQuery;
import com.novel.core.rank.domain.RankPeriod;

/**
 * 榜单读取入参。
 *
 * <p>和搜索一样把越界的 {@code page/size} 夹到合法区间而不是抛错：前端复用列表组件时
 * 传 {@code size=100} 很常见，为这个让页面白屏不值得。但这里没有"深度分页窗口"的限制——
 * 榜单读的是几百行的快照，翻到第 10 页也只是一次内存切片。
 */
public class RankQuery extends PageQuery {

    private final RankPeriod period;

    public RankQuery(RankPeriod period, int page, int size) {
        if (period == null) {
            throw new IllegalArgumentException("榜单周期不能为空");
        }
        setPage(Math.max(page, 1));
        setSize(Math.min(Math.max(size, 1), PageQuery.MAX_SIZE));
        this.period = period;
    }

    public RankPeriod period() {
        return period;
    }
}
