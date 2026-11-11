package com.novel.infra.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.common.result.PageResult;
import com.novel.core.review.application.ReviewSort;
import com.novel.core.review.domain.Review;
import com.novel.core.review.domain.ReviewReply;
import com.novel.core.review.port.ReviewRepository;
import com.novel.infra.persistence.entity.ReviewEntity;
import com.novel.infra.persistence.entity.ReviewReplyEntity;
import com.novel.infra.persistence.mapper.ReviewMapper;
import com.novel.infra.persistence.mapper.ReviewReplyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 书评与楼中楼仓储实现。 */
@Repository
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepository {

    /**
     * 热度排序：点赞权重 10、回复权重 5，再按"发帖小时数 + 2"做 1.2 次幂衰减。
     *
     * <p>为什么要衰减：只按点赞数排，一条三年前被点赞的老评会永远挂在第一页，
     * 新书评永远出不了头。加时间衰减后老评会自然下沉，但仍保留"沉底要很久"的缓冲。
     *
     * <p>拼接固定 SQL 而不是用户输入，所以用 {@code last} 是安全的；
     * 换成前端传字段名拼进去才叫注入。
     */
    private static final String HOT_ORDER = "ORDER BY (like_count * 10 + reply_count * 5) "
            + "/ POW(TIMESTAMPDIFF(HOUR, created_at, NOW()) + 2, 1.2) DESC, id DESC";

    private final ReviewMapper reviewMapper;
    private final ReviewReplyMapper reviewReplyMapper;

    @Override
    public long create(Review review) {
        ReviewEntity entity = new ReviewEntity();
        entity.setNovelId(review.novelId());
        entity.setUserId(review.userId());
        entity.setContent(review.content());
        entity.setScore(review.score());
        entity.setLikeCount(0);
        entity.setReplyCount(0);
        entity.setStatus(0);
        reviewMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public Optional<Review> findById(long reviewId) {
        return Optional.ofNullable(reviewMapper.selectById(reviewId)).map(this::toDomain);
    }

    @Override
    public PageResult<Review> pageByNovel(long novelId, ReviewSort sort, int pageNum, int size) {
        LambdaQueryWrapper<ReviewEntity> wrapper = Wrappers.<ReviewEntity>lambdaQuery()
                .eq(ReviewEntity::getNovelId, novelId);
        if (sort == ReviewSort.LATEST) {
            wrapper.orderByDesc(ReviewEntity::getCreatedAt);
        } else {
            wrapper.last(HOT_ORDER);
        }

        Page<ReviewEntity> page = reviewMapper.selectPage(new Page<>(pageNum, size), wrapper);
        List<Review> reviews = page.getRecords().stream().map(this::toDomain).toList();
        return PageResult.of(reviews, page.getTotal(), pageNum, size);
    }

    @Override
    public PageResult<Review> pageByUser(long userId, int pageNum, int size) {
        Page<ReviewEntity> page = reviewMapper.selectPage(new Page<>(pageNum, size),
                Wrappers.<ReviewEntity>lambdaQuery()
                        .eq(ReviewEntity::getUserId, userId)
                        .orderByDesc(ReviewEntity::getCreatedAt)
                        .orderByDesc(ReviewEntity::getId));
        List<Review> reviews = page.getRecords().stream().map(this::toDomain).toList();
        return PageResult.of(reviews, page.getTotal(), pageNum, size);
    }

    @Override
    public boolean softDelete(long reviewId) {
        // 实体上有 @TableLogic，deleteById 会变成 UPDATE deleted = 1
        return reviewMapper.deleteById(reviewId) > 0;
    }

    @Override
    public void addLikeCount(long reviewId, int delta) {
        reviewMapper.addLikeCount(reviewId, delta);
    }

    @Override
    public void addReplyCount(long reviewId, int delta) {
        reviewMapper.addReplyCount(reviewId, delta);
    }

    // ------------------------------------------------------------------ 楼中楼

    @Override
    public long createReply(ReviewReply reply) {
        ReviewReplyEntity entity = new ReviewReplyEntity();
        entity.setReviewId(reply.reviewId());
        entity.setRootId(reply.rootId());
        entity.setParentReplyId(reply.parentReplyId());
        entity.setUserId(reply.userId());
        entity.setContent(reply.content());
        entity.setLikeCount(0);
        entity.setStatus(0);
        reviewReplyMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public Optional<ReviewReply> findReplyById(long replyId) {
        return Optional.ofNullable(reviewReplyMapper.selectById(replyId)).map(this::toDomain);
    }

    @Override
    public List<ReviewReply> findRepliesByIds(Collection<Long> replyIds) {
        if (replyIds == null || replyIds.isEmpty()) {
            return List.of();
        }
        return reviewReplyMapper.selectBatchIds(replyIds).stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<ReviewReply> pageReplies(long reviewId, int pageNum, int size) {
        Page<ReviewReplyEntity> page = reviewReplyMapper.selectPage(new Page<>(pageNum, size),
                Wrappers.<ReviewReplyEntity>lambdaQuery()
                        .eq(ReviewReplyEntity::getReviewId, reviewId)
                        .orderByAsc(ReviewReplyEntity::getId));
        List<ReviewReply> replies = page.getRecords().stream().map(this::toDomain).toList();
        return PageResult.of(replies, page.getTotal(), pageNum, size);
    }

    @Override
    public boolean softDeleteReply(long replyId) {
        return reviewReplyMapper.deleteById(replyId) > 0;
    }

    @Override
    public void addReplyLikeCount(long replyId, int delta) {
        reviewReplyMapper.addLikeCount(replyId, delta);
    }

    private Review toDomain(ReviewEntity e) {
        return new Review(e.getId(), e.getNovelId(), e.getUserId(), e.getContent(), e.getScore(),
                e.getLikeCount() == null ? 0 : e.getLikeCount(),
                e.getReplyCount() == null ? 0 : e.getReplyCount(),
                e.getCreatedAt());
    }

    private ReviewReply toDomain(ReviewReplyEntity e) {
        return new ReviewReply(e.getId(), e.getReviewId(), e.getUserId(),
                e.getRootId() == null ? 0L : e.getRootId(),
                e.getParentReplyId() == null ? 0L : e.getParentReplyId(),
                e.getContent(),
                e.getLikeCount() == null ? 0 : e.getLikeCount(),
                e.getCreatedAt());
    }
}
