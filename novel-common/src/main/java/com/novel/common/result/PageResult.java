package com.novel.common.result;

import lombok.Getter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页出参。
 *
 * <p>{@code hasNext} 由后端算好，省得前端自己拿 total 去猜（也避免 offset 分页下 total 变化导致的错位）。
 */
@Getter
public final class PageResult<T> implements Serializable {

    private final List<T> list;
    private final long total;
    private final int page;
    private final int size;
    private final boolean hasNext;

    private PageResult(List<T> list, long total, int page, int size, boolean hasNext) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.hasNext = hasNext;
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        List<T> safeList = list == null ? Collections.emptyList() : list;
        boolean hasNext = (long) page * size < total;
        return new PageResult<>(safeList, total, page, size, hasNext);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(Collections.emptyList(), 0L, page, size, false);
    }
}
