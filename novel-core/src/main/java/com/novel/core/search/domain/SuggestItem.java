package com.novel.core.search.domain;

/** 一条搜索建议：只够填下拉框，不带简介正文。 */
public record SuggestItem(long novelId, String title, String authorName) {
}
