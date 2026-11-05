package com.novel.core.review.port;

import com.novel.core.review.domain.LikeTargetType;

import java.util.Collection;
import java.util.Set;

/**
 * 点赞仓储端口。
 *
 * <p>查询"我点过哪些"必须是批量的：书评列表一页 20 条，
 * 如果逐条查，就是 20 次数据库往返，而它们本该是 1 次 {@code IN} 查询。
 */
public interface LikeRepository {

    boolean exists(LikeTargetType type, long targetId, long userId);

    /** 幂等：唯一键冲突直接忽略，不需要先查再插。 */
    void add(LikeTargetType type, long targetId, long userId);

    /** @return 是否真的删掉了一行；并发取消时第二个请求会返回 false，调用方不能重复回退计数。 */
    boolean remove(LikeTargetType type, long targetId, long userId);

    Set<Long> likedTargetIds(LikeTargetType type, Collection<Long> targetIds, long userId);
}
