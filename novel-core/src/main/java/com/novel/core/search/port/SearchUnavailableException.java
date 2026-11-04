package com.novel.core.search.port;

/**
 * 搜索引擎当前不可用（连不上、超时、索引缺失……）。
 *
 * <p>为什么不直接让底层异常冒出去：core 不该知道用的是 ES 还是别的东西，
 * 更不该让 {@code ElasticsearchException} 出现在应用服务的 catch 里。
 * 适配器负责把各种技术异常收敛成这一个语义，应用服务再决定给用户看什么。
 *
 * <p>它是 {@link RuntimeException}：搜索失败不该强迫每一层都声明 throws，
 * 也绝不会被误当成"业务校验不通过"（那是 BizException 的活）。
 */
public class SearchUnavailableException extends RuntimeException {

    public SearchUnavailableException(String message) {
        super(message);
    }

    public SearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
