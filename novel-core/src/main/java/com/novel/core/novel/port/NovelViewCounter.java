package com.novel.core.novel.port;

/**
 * 阅读数累加器。
 *
 * <p>详情页每被打开一次就 {@code UPDATE novel SET view_count = view_count + 1} 是个坏主意：
 * 热门书的所有请求都会去争同一行的行锁。正确做法是先在 Redis 里攒增量，
 * 由定时任务批量合并回库 —— 于是读接口变成纯读，写压力被削成每分钟一次。
 *
 * <p>代价是库里看到的数据会滞后最多一个刷新周期，这在阅读数这种指标上完全可以接受。
 */
public interface NovelViewCounter {

    void increment(long novelId);

    /** 尚未落库的增量，用于详情页展示"实时值 = 库里的值 + 增量"。 */
    long pendingDelta(long novelId);
}
