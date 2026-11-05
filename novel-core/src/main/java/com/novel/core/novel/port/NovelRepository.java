package com.novel.core.novel.port;

import com.novel.common.result.PageResult;
import com.novel.core.novel.application.NovelQuery;
import com.novel.core.novel.domain.Category;
import com.novel.core.novel.domain.Novel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 小说仓储端口。
 *
 * <p>注意 {@code updateStats} 这个方法的形状：它不是"保存整个小说对象"，
 * 而是"把导入时算好的统计值一次性写回"。这样调用方不需要先读一遍再改再存，
 * 也就不会出现"读-改-写"覆盖掉别人并发修改的计数。
 */
public interface NovelRepository {

    Optional<Novel> findById(long id);

    /** 按书名查重，导入工具用来避免"同一个 TXT 跑两遍就变两本书"。 */
    Optional<Novel> findByTitle(String title);

    /** 批量取，用于书架列表避免 N+1。 */
    List<Novel> findByIds(Collection<Long> ids);

    PageResult<Novel> page(NovelQuery query);

    List<Category> findAllCategories();

    Optional<Category> findCategoryByName(String name);

    /** 按 id 取单个分类。搜索索引要把分类名冗余进文档，逐本查一次主键比整表扫一遍便宜。 */
    Optional<Category> findCategoryById(long id);

    /**
     * 按 id 游标翻页，供索引全量重建使用。
     *
     * <p>为什么不用 {@link #page(NovelQuery)}：重建要遍历全表，而 offset 分页在深页时
     * 每翻一页都要扫过前面所有行；游标只认"上一批的最大 id"，代价恒定。
     */
    List<Novel> pageAfterId(long afterId, int limit);

    /** 新建小说，返回自增 id。统计字段此时全为 0。 */
    long create(NewNovel novel);

    /** 导入完成后回填统计字段。 */
    void updateStats(long novelId, int wordCount, int chapterCount,
                     long latestChapterId, LocalDateTime latestUpdateAt);

    /** 阅读数增量落库，由定时任务把 Redis 里攒的增量刷进来。 */
    void addViewCount(long novelId, long delta);

    /** 收藏数随书架增删调整。 */
    void addFavoriteCount(long novelId, int delta);

    /**
     * 原子地调整评分聚合值。
     *
     * @param sumDelta   总分增量（改分时是"新分 - 旧分"，可能是负数）
     * @param countDelta 人数增量（首次评分 +1，改分 0，取消 -1）
     */
    void addRating(long novelId, long sumDelta, int countDelta);

    /** 对账用：用明细重算出来的值直接覆盖。 */
    void setRatingStats(long novelId, long sum, int count);

    /**
     * 榜单结算回写热度分。
     *
     * <p>这是<b>唯一一条刻意不自增 {@code version} 的写路径</b>：热度分不是内容变更，
     * 它会随每次结算变化，但索引里的 {@code hotScore} 只在全量重建时刷新。
     * 一旦让它自增版本号，每次结算都得发一遍同步事件，搜索同步就成了榜单的从属，
     * 而重建本来就是全量的，等它刷新就够了。
     *
     * @param scores novelId → 热度分，空 map 应当直接返回
     */
    void updateHotScores(Map<Long, BigDecimal> scores);

    /**
     * 新建小说用的入参。
     *
     * <p>不复用 {@link Novel} 是因为它有一半字段是 id、统计、时间戳，
     * 创建时根本不存在。用一个专门的窄对象，能让"哪些字段必须由调用方给"一目了然。
     */
    record NewNovel(String title, String authorName, String coverKey, Long categoryId,
                    int status, String intro) {
    }
}
