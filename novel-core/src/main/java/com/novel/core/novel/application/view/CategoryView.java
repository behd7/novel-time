package com.novel.core.novel.application.view;

/** 分类项。前端按 {@code parentId} 自己拼树，后端不返回嵌套结构。 */
public record CategoryView(long id, String name, Long parentId, int sortNo) {
}
