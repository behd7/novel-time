package com.novel.infra.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novel.core.review.domain.LikeTargetType;
import com.novel.core.review.port.LikeRepository;
import com.novel.infra.persistence.entity.LikeEntity;
import com.novel.infra.persistence.mapper.LikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** 点赞仓储实现。取消点赞是物理删除，否则唯一键会挡住"再次点赞"。 */
@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeMapper likeMapper;

    @Override
    public boolean exists(LikeTargetType type, long targetId, long userId) {
        return likeMapper.exists(Wrappers.<LikeEntity>lambdaQuery()
                .eq(LikeEntity::getUserId, userId)
                .eq(LikeEntity::getTargetType, type.name())
                .eq(LikeEntity::getTargetId, targetId));
    }

    @Override
    public void add(LikeTargetType type, long targetId, long userId) {
        LikeEntity entity = new LikeEntity();
        entity.setUserId(userId);
        entity.setTargetType(type.name());
        entity.setTargetId(targetId);
        likeMapper.insert(entity);
    }

    @Override
    public boolean remove(LikeTargetType type, long targetId, long userId) {
        return likeMapper.delete(Wrappers.<LikeEntity>lambdaQuery()
                .eq(LikeEntity::getUserId, userId)
                .eq(LikeEntity::getTargetType, type.name())
                .eq(LikeEntity::getTargetId, targetId)) > 0;
    }

    @Override
    public Set<Long> likedTargetIds(LikeTargetType type, Collection<Long> targetIds, long userId) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(likeMapper.selectObjs(Wrappers.<LikeEntity>lambdaQuery()
                        .select(LikeEntity::getTargetId)
                        .eq(LikeEntity::getUserId, userId)
                        .eq(LikeEntity::getTargetType, type.name())
                        .in(LikeEntity::getTargetId, targetIds))
                .stream()
                .map(obj -> ((Number) obj).longValue())
                .toList());
    }
}
