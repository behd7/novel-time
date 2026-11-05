package com.novel.core.community.application;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import com.novel.core.community.domain.Post;
import com.novel.core.community.domain.PostComment;
import com.novel.core.community.port.PostRepository;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.review.domain.LikeTargetType;
import com.novel.core.review.port.LikeRepository;
import com.novel.core.user.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 社区计数回退的单元测试。
 *
 * <p>这些分支在顺序调用里看不出来：第二次取消点赞、第二次删除都会得到"更新 0 行"，
 * 但不该再扣一次计数。这里用 Mock 直接模拟"影响 0 行"，比并发集成测试更稳定。
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final long POST_ID = 1L;
    private static final long AUTHOR_ID = 10L;
    private static final long USER_ID = 20L;

    @Mock
    private PostRepository postRepository;
    @Mock
    private NovelRepository novelRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private PostService postService;

    @Test
    @DisplayName("详情自增浏览数影响 0 行：直接返回帖子不存在，不再查一次")
    void detail_whenViewCountAffectsNoRow_returnsNotFound() {
        when(postRepository.addViewCount(POST_ID)).thenReturn(false);

        BizException e = catchThrowableOfType(
                () -> postService.detail(POST_ID, null), BizException.class);

        assertThat(e.getResultCode()).isEqualTo(ResultCode.POST_NOT_FOUND);
        verify(postRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("并发取消点赞：第二次删除影响 0 行，计数只回退一次")
    void unlike_whenRemoveAffectsNoRow_decrementsOnce() {
        Post post = new Post(POST_ID, AUTHOR_ID, "标题", "正文", null, 0, 2, 0, LocalDateTime.now());
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(likeRepository.exists(LikeTargetType.POST, POST_ID, USER_ID)).thenReturn(true);
        when(likeRepository.remove(LikeTargetType.POST, POST_ID, USER_ID)).thenReturn(true, false);

        postService.like(POST_ID, USER_ID, false);
        postService.like(POST_ID, USER_ID, false);

        verify(postRepository, times(1)).addLikeCount(POST_ID, -1);
        verify(userRepository, times(1)).addLikeReceived(AUTHOR_ID, -1);
    }

    @Test
    @DisplayName("并发删帖：第二次软删影响 0 行，不再回退发帖数")
    void delete_whenSoftDeleteAffectsNoRow_doesNotDecrementPostCount() {
        Post post = new Post(POST_ID, AUTHOR_ID, "标题", "正文", null, 0, 0, 0, LocalDateTime.now());
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.softDelete(POST_ID)).thenReturn(false);

        postService.delete(POST_ID, AUTHOR_ID);

        verify(userRepository, never()).addPostCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("并发删评论：第二次软删影响 0 行，不再回退评论数")
    void deleteComment_whenSoftDeleteAffectsNoRow_doesNotDecrementCommentCount() {
        PostComment comment = new PostComment(5L, POST_ID, AUTHOR_ID, 0L, 0L,
                "评论", 0, LocalDateTime.now());
        when(postRepository.findCommentById(5L)).thenReturn(Optional.of(comment));
        when(postRepository.softDeleteComment(5L)).thenReturn(false);

        postService.deleteComment(5L, AUTHOR_ID);

        verify(postRepository, never()).addCommentCount(anyLong(), anyInt());
    }
}
