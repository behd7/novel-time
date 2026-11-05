package com.novel.infra.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.common.result.PageResult;
import com.novel.core.community.application.PostQuery;
import com.novel.core.community.application.PostSort;
import com.novel.core.community.domain.Post;
import com.novel.core.community.domain.PostComment;
import com.novel.core.community.domain.PostRef;
import com.novel.core.community.port.PostRepository;
import com.novel.infra.persistence.entity.PostCommentEntity;
import com.novel.infra.persistence.entity.PostEntity;
import com.novel.infra.persistence.entity.PostRefEntity;
import com.novel.infra.persistence.mapper.PostCommentMapper;
import com.novel.infra.persistence.mapper.PostMapper;
import com.novel.infra.persistence.mapper.PostRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 帖子、引用与评论仓储实现。 */
@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    /**
     * 热帖排序：点赞权重 3、评论权重 5，再按"发帖小时数 + 2"做 1.2 次幂衰减。
     *
     * <p>为什么不像榜单那样定时回写 {@code hot_score}：只刷最近 N 天会让老帖停止衰减，
     * 全表刷又是每轮一次写放大；帖子量级下读时算一次更干净。加 2 是给刚发的帖子
     * 一个非零分母，避免除零。
     *
     * <p>拼接的是固定 SQL，不是用户输入，所以用 {@code last} 是安全的。
     */
    private static final String HOT_ORDER = "ORDER BY (like_count * 3 + comment_count * 5) "
            + "/ POW(TIMESTAMPDIFF(HOUR, created_at, NOW()) + 2, 1.2) DESC, id DESC";

    private final PostMapper postMapper;
    private final PostRefMapper postRefMapper;
    private final PostCommentMapper postCommentMapper;

    // ------------------------------------------------------------------ 帖子

    @Override
    public long create(Post post) {
        PostEntity entity = new PostEntity();
        entity.setUserId(post.userId());
        entity.setTitle(post.title());
        entity.setContent(post.content());
        entity.setCoverKey(post.coverKey());
        entity.setViewCount(0L);
        entity.setLikeCount(0);
        entity.setCommentCount(0);
        entity.setStatus(0);
        postMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void createRefs(long postId, List<PostRef> refs) {
        for (PostRef ref : refs) {
            PostRefEntity entity = new PostRefEntity();
            entity.setPostId(postId);
            entity.setNovelId(ref.novelId());
            entity.setSortNo(ref.sortNo());
            entity.setRefText(ref.refText());
            postRefMapper.insert(entity);
        }
    }

    @Override
    public Optional<Post> findById(long postId) {
        PostEntity entity = postMapper.selectOne(Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getId, postId)
                .eq(PostEntity::getStatus, 0));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public PageResult<Post> page(PostQuery query) {
        LambdaQueryWrapper<PostEntity> wrapper = Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getStatus, 0);
        if (query.novelId() != null) {
            // 只查 id 的子查询，反查"哪些帖子提到了这本书"
            wrapper.apply("id IN (SELECT post_id FROM post_novel_ref WHERE novel_id = {0})",
                    query.novelId());
        }
        if (query.sort() == PostSort.LATEST) {
            wrapper.orderByDesc(PostEntity::getCreatedAt).orderByDesc(PostEntity::getId);
        } else {
            wrapper.last(HOT_ORDER);
        }

        Page<PostEntity> page = postMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<Post> posts = page.getRecords().stream().map(this::toDomain).toList();
        return PageResult.of(posts, page.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    public List<PostRef> findRefs(long postId) {
        return postRefMapper.selectList(Wrappers.<PostRefEntity>lambdaQuery()
                        .eq(PostRefEntity::getPostId, postId)
                        .orderByAsc(PostRefEntity::getSortNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean softDelete(long postId) {
        // 实体上有 @TableLogic，deleteById 会变成 UPDATE deleted = 1
        return postMapper.deleteById(postId) > 0;
    }

    @Override
    public boolean addViewCount(long postId) {
        return postMapper.addViewCount(postId) > 0;
    }

    @Override
    public void addLikeCount(long postId, int delta) {
        postMapper.addLikeCount(postId, delta);
    }

    @Override
    public void addCommentCount(long postId, int delta) {
        postMapper.addCommentCount(postId, delta);
    }

    // ------------------------------------------------------------------ 评论

    @Override
    public long createComment(PostComment comment) {
        PostCommentEntity entity = new PostCommentEntity();
        entity.setPostId(comment.postId());
        entity.setUserId(comment.userId());
        entity.setRootId(comment.rootId());
        entity.setParentCommentId(comment.parentCommentId());
        entity.setContent(comment.content());
        entity.setLikeCount(0);
        entity.setStatus(0);
        postCommentMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public Optional<PostComment> findCommentById(long commentId) {
        PostCommentEntity entity = postCommentMapper.selectOne(Wrappers.<PostCommentEntity>lambdaQuery()
                .eq(PostCommentEntity::getId, commentId)
                .eq(PostCommentEntity::getStatus, 0));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public List<PostComment> findCommentsByIds(Collection<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return List.of();
        }
        return postCommentMapper.selectList(Wrappers.<PostCommentEntity>lambdaQuery()
                        .in(PostCommentEntity::getId, commentIds)
                        .eq(PostCommentEntity::getStatus, 0))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<PostComment> pageComments(long postId, int pageNum, int size) {
        Page<PostCommentEntity> page = postCommentMapper.selectPage(new Page<>(pageNum, size),
                Wrappers.<PostCommentEntity>lambdaQuery()
                        .eq(PostCommentEntity::getPostId, postId)
                        .eq(PostCommentEntity::getStatus, 0)
                        .orderByAsc(PostCommentEntity::getId));
        List<PostComment> comments = page.getRecords().stream().map(this::toDomain).toList();
        return PageResult.of(comments, page.getTotal(), pageNum, size);
    }

    @Override
    public boolean softDeleteComment(long commentId) {
        return postCommentMapper.deleteById(commentId) > 0;
    }

    @Override
    public void addCommentLikeCount(long commentId, int delta) {
        postCommentMapper.addLikeCount(commentId, delta);
    }

    // ------------------------------------------------------------------ 映射

    private Post toDomain(PostEntity e) {
        return new Post(e.getId(), e.getUserId(), e.getTitle(), e.getContent(), e.getCoverKey(),
                nullSafe(e.getViewCount()), nullSafe(e.getLikeCount()), nullSafe(e.getCommentCount()),
                e.getCreatedAt());
    }

    private PostRef toDomain(PostRefEntity e) {
        return new PostRef(e.getId(), e.getPostId(), e.getNovelId(),
                e.getSortNo() == null ? 0 : e.getSortNo(), e.getRefText());
    }

    private PostComment toDomain(PostCommentEntity e) {
        return new PostComment(e.getId(), e.getPostId(), e.getUserId(),
                nullSafe(e.getRootId()), nullSafe(e.getParentCommentId()),
                e.getContent(), nullSafe(e.getLikeCount()), e.getCreatedAt());
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
