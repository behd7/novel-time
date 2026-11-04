package com.novel.core.novel.application;

/**
 * 小说列表的排序方式。
 *
 * <p>只开放四个排序，且每个都对应一个已建好的索引（{@code idx_category_status_update} /
 * {@code idx_word_count} / {@code idx_hot}）。不提供"按任意字段任意方向排序"——
 * 那等于把 SQL 的 ORDER BY 交给前端拼，既走不了索引也带注入风险。
 * 具体映射在 infra 的仓储实现里做，core 只认这四个语义。
 */
public enum NovelSort {

    /** 最近更新（默认） */
    LATEST,
    /** 最新上架 */
    NEWEST,
    /** 字数最多 */
    WORD_COUNT,
    /** 热度最高（M5 起由榜单任务填充 hot_score，当前恒为 0） */
    HOT;

    public static NovelSort from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        // 前端习惯写 wordCount，枚举名是 WORD_COUNT，把下划线抹掉后比较，两种写法都认
        String normalized = value.replace("_", "").trim();
        for (NovelSort sort : values()) {
            if (sort.name().replace("_", "").equalsIgnoreCase(normalized)) {
                return sort;
            }
        }
        // 抛 IllegalArgumentException 而不是业务异常：取值不合法属于协议错误，
        // 全局处理器会把业务异常映射成 HTTP 200 + 业务码，那样按状态码分支的客户端会当成成功。
        throw new IllegalArgumentException("不支持的排序方式: " + value);
    }
}
