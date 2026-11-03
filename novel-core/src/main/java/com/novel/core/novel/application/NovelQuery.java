package com.novel.core.novel.application;

import com.novel.common.result.PageQuery;

/**
 * 小说列表查询条件。
 *
 * <p>继承 {@link PageQuery} 以复用页码语义，但在构造时把越界的 {@code page/size}
 * **夹到合法区间**而不是抛 400：列表接口被前端传个 {@code size=1000} 是很常见的，
 * 直接拒绝会让页面白屏，夹到上限则用户几乎无感。真正的"不许超 50"由这里和
 * MyBatis-Plus 分页插件的 {@code maxLimit} 双保险。
 */
public class NovelQuery extends PageQuery {

    private final Long categoryId;
    private final Integer status;
    private final NovelSort sort;

    public NovelQuery(Long categoryId, Integer status, NovelSort sort, int page, int size) {
        setPage(Math.max(page, 1));
        setSize(Math.min(Math.max(size, 1), PageQuery.MAX_SIZE));
        this.categoryId = categoryId;
        this.status = status;
        this.sort = sort == null ? NovelSort.LATEST : sort;
    }

    public Long categoryId() {
        return categoryId;
    }

    public Integer status() {
        return status;
    }

    public NovelSort sort() {
        return sort;
    }
}
