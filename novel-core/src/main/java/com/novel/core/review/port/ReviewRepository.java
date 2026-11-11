package com.novel.core.review.port;

import com.novel.common.result.PageResult;
import com.novel.core.review.application.ReviewSort;
import com.novel.core.review.domain.Review;
import com.novel.core.review.domain.ReviewReply;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 书评与回复仓储端口。
 *
 * <p>书评和回复放同一个端口，因为它们的读路径是绑死的：书评列表要显示 {@code replyCount}，
 * 回复列表要先确认主楼存在。拆成两个端口反而每次都要来回查。
 */
public interface ReviewRepository {

    long create(Review review);

    Optional<Review> findById(long reviewId);

    PageResult<Review> pageByNovel(long novelId, ReviewSort sort, int page, int size);

    /** 个人主页的"我的书评"：按用户查，按创建时间倒序。 */
    PageResult<Review> pageByUser(long userId, int page, int size);

    /** 软删除：内容还在，但查询会过滤掉。保留是为了处理"楼中楼还在、主楼被删"的情况。 */
    boolean softDelete(long reviewId);

    void addLikeCount(long reviewId, int delta);

    void addReplyCount(long reviewId, int delta);

    // -------- 楼中楼 --------

    long createReply(ReviewReply reply);

    Optional<ReviewReply> findReplyById(long replyId);

    List<ReviewReply> findRepliesByIds(Collection<Long> replyIds);

    PageResult<ReviewReply> pageReplies(long reviewId, int page, int size);

    boolean softDeleteReply(long replyId);

    void addReplyLikeCount(long replyId, int delta);
}
