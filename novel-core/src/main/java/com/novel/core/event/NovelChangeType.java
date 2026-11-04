package com.novel.core.event;

/**
 * 小说聚合的变更类型。
 *
 * <p>只有三种：新建、内容更新、下架/删除。刻意不细分"改标题""改简介"——
 * 索引的粒度就是整本书，消费端反正要回库读全量再整篇覆盖，
 * 把变更拆得太细只会让事件类型爆炸而没有任何收益。
 */
public enum NovelChangeType {

    CREATED,
    UPDATED,
    DELETED;

    /** 路由键后缀，统一小写，和下划线风格的 event_type 对齐。 */
    public String routingSuffix() {
        return name().toLowerCase();
    }
}
