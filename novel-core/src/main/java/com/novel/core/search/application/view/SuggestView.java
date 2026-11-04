package com.novel.core.search.application.view;

/** 搜索建议项，只够渲染下拉列表。 */
public record SuggestView(long novelId, String title, String authorName) {
}
