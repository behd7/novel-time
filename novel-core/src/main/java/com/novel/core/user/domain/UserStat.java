package com.novel.core.user.domain;

/**
 * 用户统计。
 *
 * <p>独立成表而不是实时 {@code count(*)}：个人主页是高频读接口，
 * 每次点开都去数一遍书架和帖子，数据量上来后一定会成为慢查询。
 * 这些值由后续的事件消费者（{@code q.novel.stat.agg}）增量维护。
 */
public record UserStat(Long userId, int bookshelfCount, int reviewCount, int postCount, int likeReceived) {

    public static UserStat empty(Long userId) {
        return new UserStat(userId, 0, 0, 0, 0);
    }
}
