package com.novel.infra.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novel.core.review.port.RatingRepository;
import com.novel.infra.persistence.entity.RatingEntity;
import com.novel.infra.persistence.mapper.RatingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 评分明细仓储实现。 */
@Repository
@RequiredArgsConstructor
public class RatingRepositoryImpl implements RatingRepository {

    private final RatingMapper ratingMapper;

    @Override
    public Optional<Integer> findScore(long novelId, long userId) {
        RatingEntity entity = ratingMapper.selectOne(Wrappers.<RatingEntity>lambdaQuery()
                .eq(RatingEntity::getNovelId, novelId)
                .eq(RatingEntity::getUserId, userId)
                .last("LIMIT 1"));
        return Optional.ofNullable(entity).map(RatingEntity::getScore);
    }

    /**
     * 存在则改分，不存在则插入。
     *
     * <p>这里多查一次是为了拿到"改分"这条路径 —— 但真正的并发安全不靠它，
     * 靠的是 {@code uk_novel_user}：两个请求同时插入时，后到的会抛
     * {@code DuplicateKeyException}，由调用方退化成改分。
     */
    @Override
    public void save(long novelId, long userId, int score) {
        RatingEntity existing = ratingMapper.selectOne(Wrappers.<RatingEntity>lambdaQuery()
                .eq(RatingEntity::getNovelId, novelId)
                .eq(RatingEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (existing == null) {
            RatingEntity entity = new RatingEntity();
            entity.setNovelId(novelId);
            entity.setUserId(userId);
            entity.setScore(score);
            ratingMapper.insert(entity);
        } else {
            existing.setScore(score);
            ratingMapper.updateById(existing);
        }
    }

    @Override
    public void delete(long novelId, long userId) {
        ratingMapper.delete(Wrappers.<RatingEntity>lambdaQuery()
                .eq(RatingEntity::getNovelId, novelId)
                .eq(RatingEntity::getUserId, userId));
    }

    @Override
    public Map<Integer, Long> distribution(long novelId) {
        Map<Integer, Long> result = new HashMap<>();
        for (Map<String, Object> row : ratingMapper.distribution(novelId)) {
            result.put(toInt(row.get("score")), toLong(row.get("cnt")));
        }
        return result;
    }

    @Override
    public Aggregate aggregate(long novelId) {
        Map<String, Object> row = ratingMapper.aggregate(novelId);
        return new Aggregate(toLong(row.get("total")), toInt(row.get("cnt")));
    }

    @Override
    public List<Long> pageNovelIdsWithRating(long lastNovelId, int limit) {
        return ratingMapper.pageNovelIds(lastNovelId, limit);
    }

    private int toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
