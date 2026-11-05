package com.novel.core.community.port;

import com.novel.common.result.PageResult;
import com.novel.core.community.application.PostQuery;
import com.novel.core.community.domain.Post;
import com.novel.core.community.domain.PostComment;
import com.novel.core.community.domain.PostRef;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 帖子、引用与评论仓储端口。
 *
 * <p>三者放同一个端口，理由和书评域一样：详情页要一次取齐帖子与引用，
 * 评论列表要先确认帖子存在。拆成三个端口，调用方每次都要来回查。
 * 点赞不在这里，复用书评域已有的 {@code LikeRepository}（统一点赞表）。
 */
public interface PostRepository {

    long create(Post post);

    void createRefs(long postId, List<PostRef> refs);

    Optional<Post> findById(long postId);

    PageResult<Post> page(PostQuery query);

    List<PostRef> findRefs(long postId);

    /** 软删除：内容还在，查询会过滤掉。楼中楼可能还引用着主楼，不能物理删。 */
    void softDelete(long postId);

    void addViewCount(long postId);

    void addLikeCount(long postId, int delta);

    void addCommentCount(long postId, int delta);

    // -------- 评论 --------

    long createComment(PostComment comment);

    Optional<PostComment> findCommentById(long commentId);

    List<PostComment> findCommentsByIds(Collection<Long> commentIds);

    PageResult<PostComment> pageComments(long postId, int page, int size);

    void softDeleteComment(long commentId);

    void addCommentLikeCount(long commentId, int delta);
}
