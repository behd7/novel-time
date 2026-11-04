package com.novel.core.search.application;

import com.novel.common.result.PageQuery;

/**
 * 搜索入参。
 *
 * <p>和列表查询一样把越界的 {@code page/size} 夹到合法区间（前端传 {@code size=1000} 很常见，
 * 直接 400 会让页面白屏），但**翻页深度**相反：超过窗口上限就明确拒绝。
 *
 * <p>为什么这里拒绝而不是像列表那样放行：列表翻到第 100 页是一次索引扫描，
 * 搜索引擎翻到第 100 页要把前面 2000 条都取出来排序再丢掉，代价随页码线性上涨。
 * 与其让这种请求把集群拖慢，不如回一句"请加筛选条件"——用户真正想找的东西，
 * 靠分类或更准的关键字一定比翻 20 页更快找到。
 */
public class SearchQuery extends PageQuery {

    /** 深度分页窗口：page * size 不允许超过它。 */
    public static final int MAX_WINDOW = 1000;

    /** 关键字长度上限，防止有人把整本小说贴进搜索框。 */
    public static final int MAX_KEYWORD_LENGTH = 50;

    private final String keyword;
    private final Long categoryId;
    private final Integer status;
    private final SearchSort sort;
    private final int window;

    public SearchQuery(String keyword, Long categoryId, Integer status, SearchSort sort, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("搜索关键字不能为空");
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("搜索关键字不能超过 " + MAX_KEYWORD_LENGTH + " 个字符");
        }

        int safeSize = Math.min(Math.max(size, 1), PageQuery.MAX_SIZE);
        int safePage = Math.max(page, 1);
        if ((long) safePage * safeSize > MAX_WINDOW) {
            throw new IllegalArgumentException(
                    "搜索结果最多翻到第 " + (MAX_WINDOW / safeSize) + " 页，请用分类或更精确的关键字缩小范围");
        }

        setPage(safePage);
        setSize(safeSize);
        this.keyword = trimmed;
        this.categoryId = categoryId;
        this.status = status;
        this.sort = sort == null ? SearchSort.RELEVANCE : sort;
        this.window = MAX_WINDOW;
    }

    public String keyword() {
        return keyword;
    }

    public Long categoryId() {
        return categoryId;
    }

    public Integer status() {
        return status;
    }

    public SearchSort sort() {
        return sort;
    }

    public int window() {
        return window;
    }
}
