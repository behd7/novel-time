package com.novel.core.support;

import com.novel.core.review.domain.LikeTargetType;
import com.novel.core.review.port.LikeRepository;
import org.springframework.dao.DuplicateKeyException;

import java.util.function.IntConsumer;

/**
 * 点赞/取消的共用实现。
 *
 * <p>书评、回复、帖子、评论四种对象共用一张 {@code like_record}，幂等规则也只该有一份：
 * 点赞靠唯一键兜住并发，取消必须确认真的删掉一行才回退计数——两个并发 DELETE 都能查到
 * "已点赞"，后一个删 0 行，如果不看影响行数就再扣一次，计数会被多扣。
 */
public final class LikeCounter {

    private LikeCounter() {
    }

    public static void change(LikeRepository likeRepository, LikeTargetType type, long targetId,
                              long userId, boolean like, IntConsumer counter) {
        boolean existed = likeRepository.exists(type, targetId, userId);
        if (like && !existed) {
            try {
                likeRepository.add(type, targetId, userId);
                counter.accept(1);
            } catch (DuplicateKeyException ignored) {
                // 并发重复点赞，另一个请求已经加过计数
            }
        } else if (!like && existed && likeRepository.remove(type, targetId, userId)) {
            counter.accept(-1);
        }
    }
}
