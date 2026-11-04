package com.novel.core.search.port;

import com.novel.common.result.PageResult;
import com.novel.core.search.application.SearchQuery;
import com.novel.core.search.domain.NovelDoc;
import com.novel.core.search.domain.SearchHit;
import com.novel.core.search.domain.SuggestItem;

import java.util.List;

/**
 * 搜索索引端口。
 *
 * <p>两个方向的能力放在同一个接口里，是因为它们共享同一份索引定义：
 * 换索引结构时必须同时改查询和写入，拆成两个接口只会让人改一半。
 *
 * <p>{@code upsert} / {@code delete} 都带 {@code version}，语义是"只有版本更大才生效"。
 * 这不是可选的优化，而是乱序消费的兜底：MQ 投递不保证顺序、消费者也可能并发，
 * 没有版本号时，一条迟到的旧事件会把新内容盖回去，而且不会有任何报错。
 */
public interface NovelSearchPort {

    PageResult<SearchHit> search(SearchQuery query);

    List<SuggestItem> suggest(String prefix, int limit);

    /** 整篇覆盖写；同一份数据重复写是无害的。 */
    void upsert(NovelDoc doc, long version);

    /** 删除文档；文档本来就不存在时静默成功。 */
    void delete(long novelId, long version);
}
