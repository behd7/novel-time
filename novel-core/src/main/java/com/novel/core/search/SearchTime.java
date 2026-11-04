package com.novel.core.search;

import java.time.format.DateTimeFormatter;

/**
 * 搜索索引里的时间格式。
 *
 * <p>索引里存字符串而不是时间戳，是为了让 {@code _source} 和接口返回的格式完全一致，
 * 少一层转换就少一处"写入一个格式、读出另一个格式"的暗坑。
 * 格式必须与 ES 映射里的 {@code format} 一字不差，否则建文档时会被直接拒绝。
 */
public final class SearchTime {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 与 {@link #FORMATTER} 对应，写进 ES 的 date 映射。 */
    public static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    private SearchTime() {
    }
}
