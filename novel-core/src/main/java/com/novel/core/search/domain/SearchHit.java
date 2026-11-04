package com.novel.core.search.domain;

/**
 * 一条搜索命中：文档本身 + 引擎算出的高亮片段。
 *
 * <p>刻意不把相关度分数放进来——它只在调试排序时有用，暴露给前端只会诱导
 * "按分数做业务分支"这种把检索引擎当数据库用的写法。
 */
public record SearchHit(NovelDoc doc, Highlight highlight) {
}
