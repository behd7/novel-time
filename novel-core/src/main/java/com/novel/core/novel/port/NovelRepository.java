package com.novel.core.novel.port;

import com.novel.common.result.PageResult;
import com.novel.core.novel.application.NovelQuery;
import com.novel.core.novel.domain.Category;
import com.novel.core.novel.domain.Novel;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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
     * 新建小说用的入参。
     *
     * <p>不复用 {@link Novel} 是因为它有一半字段是 id、统计、时间戳，
     * 创建时根本不存在。用一个专门的窄对象，能让"哪些字段必须由调用方给"一目了然。
     */
    record NewNovel(String title, String authorName, String coverKey, Long categoryId,
                    int status, String intro) {
    }
}
