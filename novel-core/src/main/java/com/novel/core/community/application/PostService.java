package com.novel.core.community.application;

import com.novel.common.exception.BizException;
import com.novel.common.result.PageResult;
import com.novel.common.result.ResultCode;
import com.novel.core.community.application.view.PostCommentView;
import com.novel.core.community.application.view.PostDetailView;
import com.novel.core.community.application.view.PostRefView;
import com.novel.core.community.application.view.PostView;
import com.novel.core.community.domain.Post;
import com.novel.core.community.domain.PostComment;
import com.novel.core.community.domain.PostRef;
import com.novel.core.community.port.PostRepository;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.review.domain.LikeTargetType;
import com.novel.core.review.port.LikeRepository;
import com.novel.core.user.domain.UserProfile;
import com.novel.core.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 社区应用服务：帖子、结构化引用、两层评论与点赞。
 *
 * <p>点赞直接复用统一点赞表对应的端口，没有新建一套：帖子、评论、书评的"一人一次"
 * 本来就该由同一张表的唯一键保证。计数全部走数据库原子更新，不引入 Redis 和 MQ——
 * 判断"我点没点过"必须查明细表，明细已在库里，计数再放缓存只会多一处可能对不上的地方。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_CONTENT_LENGTH = 10000;
    private static final int MAX_COMMENT_LENGTH = 1000;
    private static final int MAX_REFS = 5;
    private static final int SUMMARY_LENGTH = 120;

    /** 正文里的引用占位符，编号即 {@code refNovelIds} 的下标。 */
    private static final Pattern REF_PLACEHOLDER = Pattern.compile("\\[\\[ref:(\\d+)]]");

    private final PostRepository postRepository;
    private final NovelRepository novelRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;

    // ------------------------------------------------------------------ 帖子

    public PageResult<PostView> list(PostQuery query, Long currentUserId) {
        PageResult<Post> posts = postRepository.page(query);
        if (posts.getList().isEmpty()) {
            return PageResult.of(List.of(), posts.getTotal(), posts.getPage(), posts.getSize());
        }

        Map<Long, UserProfile> profiles = loadProfiles(
                posts.getList().stream().map(Post::userId).toList());
        Set<Long> likedIds = currentUserId == null ? Set.of()
                : likeRepository.likedTargetIds(LikeTargetType.POST,
                posts.getList().stream().map(Post::id).toList(), currentUserId);

        List<PostView> views = posts.getList().stream()
                .map(post -> toView(post, profiles.get(post.userId()), likedIds.contains(post.id())))
                .toList();
        return PageResult.of(views, posts.getTotal(), posts.getPage(), posts.getSize());
    }

    /** 详情会累加一次浏览数；浏览数不进热帖公式，避免刷阅读量影响排序。 */
    public PostDetailView detail(long postId, Long currentUserId) {
        requirePost(postId);
        postRepository.addViewCount(postId);
        // 重新读一次，否则返回的 viewCount 还是自增前的旧值
        return assembleDetail(requirePost(postId), currentUserId);
    }

    /**
     * 发帖。
     *
     * <p>引用关系与帖子在同一个事务里落库：引用编号对不上正文时，宁可整条失败，
     * 也不要留下一条"点开卡片是空的"的帖子。引用的书批量查一次，缺一本就整条拒绝。
     */
    @Transactional
    public PostDetailView create(long userId, String title, String content,
                                 String coverKey, List<Long> refNovelIds) {
        String safeTitle = requireText(title, MAX_TITLE_LENGTH, "标题");
        String safeContent = requireText(content, MAX_CONTENT_LENGTH, "正文");

        List<Long> refIds = refNovelIds == null ? List.of() : refNovelIds;
        if (refIds.size() > MAX_REFS) {
            throw new IllegalArgumentException("最多引用 " + MAX_REFS + " 本小说");
        }
        validatePlaceholders(safeContent, refIds.size());
        if (!refIds.isEmpty()) {
            List<Long> distinct = refIds.stream().distinct().toList();
            if (novelRepository.findByIds(distinct).size() != distinct.size()) {
                throw new BizException(ResultCode.NOVEL_NOT_FOUND);
            }
        }

        long postId = postRepository.create(new Post(null, userId, safeTitle, safeContent,
                coverKey, 0, 0, 0, LocalDateTime.now()));
        if (!refIds.isEmpty()) {
            postRepository.createRefs(postId, buildRefs(postId, refIds));
        }
        userRepository.addPostCount(userId, 1);
        log.info("新增帖子 postId={} userId={} refs={}", postId, userId, refIds.size());

        return assembleDetail(requirePost(postId), userId);
    }

    @Transactional
    public void delete(long postId, long userId) {
        Post post = requirePost(postId);
        if (post.userId() != userId) {
            throw new BizException(ResultCode.POST_NO_PERMISSION);
        }
        postRepository.softDelete(postId);
        userRepository.addPostCount(userId, -1);
        log.info("删除帖子 postId={} userId={}", postId, userId);
    }

    @Transactional
    public void like(long postId, long userId, boolean like) {
        Post post = requirePost(postId);
        changeLike(LikeTargetType.POST, postId, userId, like, delta -> {
            postRepository.addLikeCount(postId, delta);
            userRepository.addLikeReceived(post.userId(), delta);
        });
    }

    // ------------------------------------------------------------------ 评论

    public PageResult<PostCommentView> listComments(long postId, int page, int size, Long currentUserId) {
        requirePost(postId);

        PageResult<PostComment> comments = postRepository.pageComments(postId, page, size);
        if (comments.getList().isEmpty()) {
            return PageResult.of(List.of(), comments.getTotal(), comments.getPage(), comments.getSize());
        }

        // 被回复的评论可能在本页之外，按 id 单独补一次
        Set<Long> parentIds = comments.getList().stream()
                .map(PostComment::parentCommentId)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
        Map<Long, PostComment> parents = parentIds.isEmpty() ? Map.of()
                : postRepository.findCommentsByIds(parentIds).stream()
                .collect(Collectors.toMap(PostComment::id, Function.identity()));

        List<Long> userIds = new ArrayList<>(
                comments.getList().stream().map(PostComment::userId).toList());
        parents.values().forEach(parent -> userIds.add(parent.userId()));
        Map<Long, UserProfile> profiles = loadProfiles(userIds);

        Set<Long> likedIds = currentUserId == null ? Set.of()
                : likeRepository.likedTargetIds(LikeTargetType.POST_COMMENT,
                comments.getList().stream().map(PostComment::id).toList(), currentUserId);

        List<PostCommentView> views = comments.getList().stream().map(comment -> {
            PostComment parent = comment.parentCommentId() > 0
                    ? parents.get(comment.parentCommentId()) : null;
            UserProfile author = profiles.get(comment.userId());
            UserProfile parentAuthor = parent == null ? null : profiles.get(parent.userId());
            return new PostCommentView(comment.id(), comment.userId(),
                    author == null ? "" : author.nickname(),
                    author == null ? null : author.avatarKey(),
                    comment.content(),
                    parent == null ? null : parent.id(),
                    parentAuthor == null ? null : parentAuthor.nickname(),
                    comment.likeCount(), likedIds.contains(comment.id()), comment.createdAt());
        }).toList();

        return PageResult.of(views, comments.getTotal(), comments.getPage(), comments.getSize());
    }

    @Transactional
    public PostCommentView createComment(long postId, long userId, String content, Long parentCommentId) {
        String text = requireText(content, MAX_COMMENT_LENGTH, "评论");
        requirePost(postId);

        long parentId = 0L;
        long rootId = 0L;
        if (parentCommentId != null && parentCommentId > 0) {
            PostComment parent = postRepository.findCommentById(parentCommentId)
                    .orElseThrow(() -> new BizException(ResultCode.POST_COMMENT_NOT_FOUND));
            if (parent.postId() != postId) {
                throw new BizException(ResultCode.BAD_REQUEST, "父评论不属于这个帖子");
            }
            parentId = parent.id();
            // 无论嵌套多深都归并到第一层的 rootId 下，界面只渲染两层
            rootId = parent.rootId() == 0 ? parent.id() : parent.rootId();
        }

        long commentId = postRepository.createComment(new PostComment(null, postId, userId,
                rootId, parentId, text, 0, LocalDateTime.now()));
        postRepository.addCommentCount(postId, 1);
        log.info("新增帖子评论 commentId={} postId={} userId={}", commentId, postId, userId);
        return loadCommentView(commentId, userId);
    }

    @Transactional
    public void deleteComment(long commentId, long userId) {
        PostComment comment = postRepository.findCommentById(commentId)
                .orElseThrow(() -> new BizException(ResultCode.POST_COMMENT_NOT_FOUND));
        if (comment.userId() != userId) {
            throw new BizException(ResultCode.POST_NO_PERMISSION);
        }
        postRepository.softDeleteComment(commentId);
        postRepository.addCommentCount(comment.postId(), -1);
        log.info("删除帖子评论 commentId={} userId={}", commentId, userId);
    }

    @Transactional
    public void likeComment(long commentId, long userId, boolean like) {
        PostComment comment = postRepository.findCommentById(commentId)
                .orElseThrow(() -> new BizException(ResultCode.POST_COMMENT_NOT_FOUND));
        changeLike(LikeTargetType.POST_COMMENT, commentId, userId, like, delta -> {
            postRepository.addCommentLikeCount(commentId, delta);
            userRepository.addLikeReceived(comment.userId(), delta);
        });
    }

    // ------------------------------------------------------------------ 组装

    private PostDetailView assembleDetail(Post post, Long currentUserId) {
        UserProfile profile = userRepository.findProfile(post.userId());
        boolean liked = currentUserId != null
                && likeRepository.exists(LikeTargetType.POST, post.id(), currentUserId);
        return new PostDetailView(post.id(), post.userId(),
                profile == null ? "" : profile.nickname(),
                profile == null ? null : profile.avatarKey(),
                post.title(), post.content(), post.coverKey(), post.viewCount(),
                post.likeCount(), post.commentCount(), liked, loadRefs(post.id()), post.createdAt());
    }

    /**
     * 引用卡片批量取：一页最多 5 个引用，一次 {@code IN} 查完，
     * 缺书时跳过而不是报错——帖子本身仍然可读。
     */
    private List<PostRefView> loadRefs(long postId) {
        List<PostRef> refs = postRepository.findRefs(postId);
        if (refs.isEmpty()) {
            return List.of();
        }
        Map<Long, Novel> novels = novelRepository.findByIds(
                        refs.stream().map(PostRef::novelId).toList())
                .stream().collect(Collectors.toMap(Novel::id, Function.identity(), (a, b) -> a));
        return refs.stream()
                .filter(ref -> novels.containsKey(ref.novelId()))
                .map(ref -> {
                    Novel novel = novels.get(ref.novelId());
                    return new PostRefView(ref.refText(), novel.id(), novel.title(),
                            novel.authorName(), novel.coverKey(), novel.avgRating());
                }).toList();
    }

    private PostCommentView loadCommentView(long commentId, Long currentUserId) {
        PostComment comment = postRepository.findCommentById(commentId)
                .orElseThrow(() -> new BizException(ResultCode.POST_COMMENT_NOT_FOUND));
        UserProfile profile = userRepository.findProfile(comment.userId());
        PostComment parent = comment.parentCommentId() > 0
                ? postRepository.findCommentById(comment.parentCommentId()).orElse(null) : null;
        String parentNickname = parent == null ? null
                : userRepository.findProfile(parent.userId()).nickname();
        boolean liked = currentUserId != null
                && likeRepository.exists(LikeTargetType.POST_COMMENT, commentId, currentUserId);
        return new PostCommentView(comment.id(), comment.userId(), profile.nickname(), profile.avatarKey(),
                comment.content(), parent == null ? null : parent.id(), parentNickname,
                comment.likeCount(), liked, comment.createdAt());
    }

    private PostView toView(Post post, UserProfile profile, boolean liked) {
        return new PostView(post.id(), post.userId(),
                profile == null ? "" : profile.nickname(),
                profile == null ? null : profile.avatarKey(),
                post.title(), summary(post.content()), post.coverKey(), post.viewCount(),
                post.likeCount(), post.commentCount(), liked, post.createdAt());
    }

    private Post requirePost(long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BizException(ResultCode.POST_NOT_FOUND));
    }

    private List<PostRef> buildRefs(long postId, List<Long> novelIds) {
        List<PostRef> refs = new ArrayList<>(novelIds.size());
        for (int i = 0; i < novelIds.size(); i++) {
            refs.add(new PostRef(null, postId, novelIds.get(i), i, "[[ref:" + i + "]]"));
        }
        return refs;
    }

    /**
     * 占位符编号不能超过引用列表长度。
     *
     * <p>只做这一条：引用有没有被正文用到、顺序对不对，属于内容质量问题，
     * 不该在参数校验里替作者决定。
     */
    private void validatePlaceholders(String content, int refCount) {
        Matcher matcher = REF_PLACEHOLDER.matcher(content);
        while (matcher.find()) {
            int index;
            try {
                index = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("引用占位符不合法: " + matcher.group());
            }
            if (index >= refCount) {
                throw new IllegalArgumentException("引用占位符 " + matcher.group() + " 超出引用列表");
            }
        }
    }

    private String summary(String content) {
        String text = REF_PLACEHOLDER.matcher(content).replaceAll("").trim();
        return text.length() <= SUMMARY_LENGTH ? text : text.substring(0, SUMMARY_LENGTH) + "…";
    }

    /**
     * 点赞/取消。
     *
     * <p>和书评点赞同一套做法：先查再写有并发窗口，所以真正插入成功后才加计数，
     * 撞唯一键的那次直接忽略——最多让一个请求白跑，不会把计数加两次。
     */
    private void changeLike(LikeTargetType type, long targetId, long userId,
                            boolean like, IntConsumer counter) {
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

    private Map<Long, UserProfile> loadProfiles(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserProfile> map = new HashMap<>();
        userRepository.findProfiles(userIds).forEach(profile -> map.put(profile.userId(), profile));
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
