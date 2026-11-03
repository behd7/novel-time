package com.novel.core.novel.port;

import com.novel.core.novel.domain.Category;

import java.util.List;

/**
 * 阅读内容缓存端口。
 *
 * <p>只缓存两样东西，其余一律不缓存：
 * <ul>
 *   <li><b>章节正文</b>——单章可能几十 KB，读接口 QPS 最高，缓存收益最大；</li>
 *   <li><b>分类树</b>——几乎不变，但每次首页都要查，典型的"读多写零"。</li>
 * </ul>
 *
 * <p>刻意**不缓存**小说详情：那是一次主键查询，1ms 就回来了，
 * 为它引入缓存反而要多维护一套失效逻辑，一旦漏删就会出现"改了封面半天不更新"的怪现象。
 * 缓存的收益必须大于一致性成本，否则就是负优化。
 */
public interface NovelCache {

    String getChapterText(long chapterId);

    void putChapterText(long chapterId, String text);

    void evictChapterText(long chapterId);

    List<Category> getCategories();

    void putCategories(List<Category> categories);

    void evictCategories();
}
