package com.novel.core.review.application;

import com.novel.common.exception.BizException;
import com.novel.common.result.PageResult;
import com.novel.common.result.ResultCode;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.rank.domain.RankAction;
import com.novel.core.rank.port.RankScoreStore;
import com.novel.core.review.application.view.ReviewReplyView;
import com.novel.core.review.application.view.ReviewView;
import com.novel.core.review.domain.LikeTargetType;
import com.novel.core.review.domain.Review;
import com.novel.core.review.domain.ReviewReply;
import com.novel.core.review.port.LikeRepository;
import com.novel.core.review.port.ReviewRepository;
import com.novel.core.user.domain.UserProfile;
import com.novel.core.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * 书评与楼中楼应用服务。
 *
 * <p>两类高频查询都在这里做了批量化处理：
 * 一是作者昵称（一次 {@code IN} 查完一页的人），二是"我点没点过赞"
 * （一次 {@code IN} 查完一页的目标）。逐条查的话，20 条评论就是 40 次往返。
 *
 * <p>点赞计数直接做数据库原子更新，没有走缓存。理由是"我点没点过"必须查明细表，
 * 既然明细已经在库里，计数再放缓存只会多一处可能对不上的地方。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_REVIEW_LENGTH = 2000;
    private static final int MAX_REPLY_LENGTH = 1000;

    private final ReviewRepository reviewRepository;
    private final NovelRepository novelRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final RatingService ratingService;
    private final RankScoreStore rankScoreStore;

    // ------------------------------------------------------------------ 书评

    public PageResult<ReviewView> list(long novelId, ReviewSort sort, int page, int size, Long currentUserId) {
        PageResult<Review> reviews = reviewRepository.pageByNovel(novelId, sort, page, size);
        if (reviews.getList().isEmpty()) {
            // 越界页保留真实总数，否则前端会把"翻过头了"当成"一条评论都没有"
            return PageResult.of(List.of(), reviews.getTotal(), reviews.getPage(), reviews.getSize());
        }

        Map<Long, UserProfile> profiles = loadProfiles(
                reviews.getList().stream().map(Review::userId).toList());
        Set<Long> likedIds = currentUserId == null ? Set.of()
                : likeRepository.likedTargetIds(LikeTargetType.REVIEW,
                reviews.getList().stream().map(Review::id).toList(), currentUserId);

        List<ReviewView> views = reviews.getList().stream()
                .map(review -> toView(review, profiles.get(review.userId()), likedIds.contains(review.id())))
                .toList();
        return PageResult.of(views, reviews.getTotal(), reviews.getPage(), reviews.getSize());
    }

    /**
     * 发书评。
     *
     * <p>如果同时带了星级，会一并记入评分——这样"评分"只有一个来源，
     * 不会出现"书评区显示 5 星、评分区还是没打分"的错位。
     */
    @Transactional
    public ReviewView create(long novelId, long userId, String content, Integer score) {
        String text = requireText(content, MAX_REVIEW_LENGTH, "书评");
        novelRepository.findById(novelId)
                .orElseThrow(() -> new BizException(ResultCode.NOVEL_NOT_FOUND));

        ratingService.rateQuietly(novelId, userId, score);

        long reviewId = reviewRepository.create(new Review(null, novelId, userId, text, score,
                0, 0, LocalDateTime.now()));
        userRepository.addReviewCount(userId, 1);
        rankScoreStore.recordAction(novelId, RankAction.REVIEW, 1);
        log.info("新增书评 reviewId={} novelId={} userId={}", reviewId, novelId, userId);

        return loadReviewView(reviewId, userId);
    }

    @Transactional
    public void delete(long reviewId, long userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BizException(ResultCode.REVIEW_NOT_FOUND));
        if (review.userId() != userId) {
            throw new BizException(ResultCode.REVIEW_NO_PERMISSION);
        }
        reviewRepository.softDelete(reviewId);
        userRepository.addReviewCount(userId, -1);
        rankScoreStore.recordAction(review.novelId(), RankAction.REVIEW, -1);
        log.info("删除书评 reviewId={} userId={}", reviewId, userId);
    }

    // ------------------------------------------------------------------ 楼中楼

    public PageResult<ReviewReplyView> listReplies(long reviewId, int page, int size, Long currentUserId) {
        reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BizException(ResultCode.REVIEW_NOT_FOUND));

        PageResult<ReviewReply> replies = reviewRepository.pageReplies(reviewId, page, size);
        if (replies.getList().isEmpty()) {
            return PageResult.of(List.of(), replies.getTotal(), replies.getPage(), replies.getSize());
        }

        // 被回复的那条可能是本页之外的，按 id 单独取一次
        Set<Long> parentIds = replies.getList().stream()
                .map(ReviewReply::parentReplyId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, ReviewReply> parents = parentIds.isEmpty() ? Map.of()
                : reviewRepository.findRepliesByIds(parentIds).stream()
                .collect(Collectors.toMap(ReviewReply::id, Function.identity()));

        List<Long> userIds = new java.util.ArrayList<>(
                replies.getList().stream().map(ReviewReply::userId).toList());
        parents.values().forEach(parent -> userIds.add(parent.userId()));
        Map<Long, UserProfile> profiles = loadProfiles(userIds);

        Set<Long> likedIds = currentUserId == null ? Set.of()
                : likeRepository.likedTargetIds(LikeTargetType.REPLY,
                replies.getList().stream().map(ReviewReply::id).toList(), currentUserId);

        List<ReviewReplyView> views = replies.getList().stream().map(reply -> {
            ReviewReply parent = reply.parentReplyId() > 0 ? parents.get(reply.parentReplyId()) : null;
            UserProfile author = profiles.get(reply.userId());
            UserProfile parentAuthor = parent == null ? null : profiles.get(parent.userId());
            return new ReviewReplyView(reply.id(), reply.userId(),
                    author == null ? "" : author.nickname(),
                    author == null ? null : author.avatarKey(),
                    reply.content(),
                    parent == null ? null : parent.id(),
                    parentAuthor == null ? null : parentAuthor.nickname(),
                    reply.likeCount(), likedIds.contains(reply.id()), reply.createdAt());
        }).toList();

        return PageResult.of(views, replies.getTotal(), replies.getPage(), replies.getSize());
    }

    @Transactional
    public ReviewReplyView createReply(long reviewId, long userId, String content, Long parentReplyId) {
        String text = requireText(content, MAX_REPLY_LENGTH, "回复");
        reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BizException(ResultCode.REVIEW_NOT_FOUND));

        long rootId = 0L;
        long parentId = 0L;
        if (parentReplyId != null && parentReplyId > 0) {
            ReviewReply parent = reviewRepository.findReplyById(parentReplyId)
                    .orElseThrow(() -> new BizException(ResultCode.REVIEW_NOT_FOUND));
            if (parent.reviewId() != reviewId) {
                throw new BizException(ResultCode.BAD_REQUEST, "父回复不属于这条书评");
            }
            parentId = parent.id();
            // 无论嵌套多深，都归并到第一层的 rootId 下，界面只渲染两层
            rootId = parent.rootId() == 0 ? parent.id() : parent.rootId();
        }

        long replyId = reviewRepository.createReply(new ReviewReply(null, reviewId, userId,
                rootId, parentId, text, 0, LocalDateTime.now()));
        reviewRepository.addReplyCount(reviewId, 1);
        return loadReplyView(replyId, userId);
    }

    @Transactional
    public void deleteReply(long replyId, long userId) {
        ReviewReply reply = reviewRepository.findReplyById(replyId)
                .orElseThrow(() -> new BizException(ResultCode.REVIEW_NOT_FOUND));
        if (reply.userId() != userId) {
            throw new BizException(ResultCode.REVIEW_NO_PERMISSION);
        }
        reviewRepository.softDeleteReply(replyId);
        reviewRepository.addReplyCount(reply.reviewId(), -1);
    }

    // ------------------------------------------------------------------ 点赞

    @Transactional
    public void likeReview(long reviewId, long userId, boolean like) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BizException(ResultCode.REVIEW_NOT_FOUND));
        changeLike(LikeTargetType.REVIEW, reviewId, userId, like, delta -> {
            reviewRepository.addLikeCount(reviewId, delta);
            userRepository.addLikeReceived(review.userId(), delta);
        });
    }

    @Transactional
    public void likeReply(long replyId, long userId, boolean like) {
        ReviewReply reply = reviewRepository.findReplyById(replyId)
                .orElseThrow(() -> new BizException(ResultCode.REVIEW_NOT_FOUND));
        changeLike(LikeTargetType.REPLY, replyId, userId, like, delta -> {
            reviewRepository.addReplyLikeCount(replyId, delta);
            userRepository.addLikeReceived(reply.userId(), delta);
        });
    }

    /**
     * 点赞/取消。
     *
     * <p>先查再写会有并发窗口：两个请求同时点赞，都查到"没点过"，然后一个成功一个撞唯一键。
     * 所以是真插入成功之后才加计数，撞键的那次直接忽略——最多让一个请求白跑，不会把计数加两次。
     */
    private void changeLike(LikeTargetType type, long targetId, long userId, boolean like, IntConsumer counter) {
        boolean existed = likeRepository.exists(type, targetId, userId);
        if (like && !existed) {
            try {
                likeRepository.add(type, targetId, userId);
                counter.accept(1);
            } catch (DuplicateKeyException ignored) {
                // 并发重复点赞，另一个请求已经加过计数
            }
        } else if (!like && existed) {
            likeRepository.remove(type, targetId, userId);
            counter.accept(-1);
        }
    }

    // ------------------------------------------------------------------ 组装

    private ReviewView loadReviewView(long reviewId, Long currentUserId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BizException(ResultCode.REVIEW_NOT_FOUND));
        UserProfile profile = userRepository.findProfile(review.userId());
        boolean liked = currentUserId != null
                && likeRepository.exists(LikeTargetType.REVIEW, reviewId, currentUserId);
        return toView(review, profile, liked);
    }

    private ReviewReplyView loadReplyView(long replyId, Long currentUserId) {
        ReviewReply reply = reviewRepository.findReplyById(replyId)
                .orElseThrow(() -> new BizException(ResultCode.REVIEW_NOT_FOUND));
        UserProfile profile = userRepository.findProfile(reply.userId());
        ReviewReply parent = reply.parentReplyId() > 0
                ? reviewRepository.findReplyById(reply.parentReplyId()).orElse(null)
                : null;
        String parentNickname = parent == null ? null : userRepository.findProfile(parent.userId()).nickname();
        boolean liked = currentUserId != null
                && likeRepository.exists(LikeTargetType.REPLY, replyId, currentUserId);
        return new ReviewReplyView(reply.id(), reply.userId(), profile.nickname(), profile.avatarKey(),
                reply.content(), parent == null ? null : parent.id(), parentNickname,
                reply.likeCount(), liked, reply.createdAt());
    }

    private ReviewView toView(Review review, UserProfile profile, boolean liked) {
        return new ReviewView(review.id(), review.userId(),
                profile == null ? "" : profile.nickname(),
                profile == null ? null : profile.avatarKey(),
                review.content(), review.score(),
                review.likeCount(), review.replyCount(), liked, review.createdAt());
    }

    private Map<Long, UserProfile> loadProfiles(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserProfile> map = new HashMap<>();
        List<UserProfile> profiles = userRepository.findProfiles(userIds);
        profiles.forEach(profile -> map.put(profile.userId(), profile));
        return map;
    }

    private String requireText(String content, int maxLength, String field) {
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, field + "内容不能为空");
        }
        String text = content.trim();
        if (text.length() > maxLength) {
            throw new BizException(ResultCode.BAD_REQUEST, field + "不能超过 " + maxLength + " 字");
        }
        return text;
    }
}
