package com.novel.core.community.application;

import com.novel.common.result.PageQuery;

/**
 * 帖子列表查询条件。
 *
 * <p>和书评列表一样把越界的 {@code page/size} 夹到合法区间，不抛 400：
 * 前端传 {@code size=1000} 很常见，直接拒绝会让页面白屏。
 */
public class PostQuery extends PageQuery {

    private final PostSort sort;
    private final Long novelId;

    public PostQuery(PostSort sort, Long novelId, int page, int size) {
        setPage(Math.max(page, 1));
        setSize(Math.min(Math.max(size, 1), PageQuery.MAX_SIZE));
        this.sort = sort == null ? PostSort.HOT : sort;
        this.novelId = novelId;
    }

    public PostSort sort() {
        return sort;
    }

    /** 可选：反查"哪些帖子提到了这本书"，为空表示不筛。 */
    public Long novelId() {
        return novelId;
    }
}
