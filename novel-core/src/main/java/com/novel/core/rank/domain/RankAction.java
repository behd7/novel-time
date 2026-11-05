package com.novel.core.rank.domain;

/**
 * 会被计入榜单的"低频"行为。
 *
 * <p>阅读不在这里：它要走去重（同一人同一天同一本书只算一次），形状和这两个不一样。
 * 收藏与书评则天然幂等——调用方先查过存在性，所以端口只需要加减。
 */
public enum RankAction {

    FAVORITE,
    REVIEW
}
