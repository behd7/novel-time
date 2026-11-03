package com.novel.core.novel.domain;

/**
 * 分类。固定两层：{@code parentId == 0} 为顶级。
 *
 * <p>不做无限层级是产品决策不是技术偷懒：分类一旦超过两层，用户就找不到了，
 * 更细的粒度交给标签（{@code novel_tag}）。
 */
public record Category(Long id, String name, Long parentId, int sortNo) {

    public boolean isTopLevel() {
        return parentId == null || parentId == 0L;
    }
}
