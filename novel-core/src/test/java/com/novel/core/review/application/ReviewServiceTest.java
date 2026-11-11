package com.novel.core.review.application;

import com.novel.common.result.PageResult;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.rank.port.RankScoreStore;
import com.novel.core.review.application.view.UserReviewView;
import com.novel.core.review.domain.LikeTargetType;
import com.novel.core.review.domain.Review;
import com.novel.core.review.port.LikeRepository;
import com.novel.core.review.port.ReviewRepository;
import com.novel.core.user.domain.UserProfile;
import com.novel.core.user.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** 个人主页书评列表：书名、作者昵称与"我赞过"必须一次组装好。 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final long USER_ID = 5L;
    private static final long VIEWER_ID = 9L;
    private static final long REVIEW_ID = 7L;
    private static final long NOVEL_ID = 10L;

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private NovelRepository novelRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private RatingService ratingService;
    @Mock
    private RankScoreStore rankScoreStore;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    @DisplayName("按用户查书评：带书名声、昵称与当前用户点赞状态")
    void listByUser_assemblesNovelTitleAndLiked() {
        Review review = new Review(REVIEW_ID, NOVEL_ID, USER_ID, "写得好", 5, 2, 1,
                LocalDateTime.now());
        when(reviewRepository.pageByUser(USER_ID, 1, 10))
                .thenReturn(PageResult.of(List.of(review), 1, 1, 10));
        when(userRepository.findProfiles(List.of(USER_ID)))
                .thenReturn(List.of(new UserProfile(USER_ID, "书虫", null, "简介", 0, null)));
        when(novelRepository.findByIds(List.of(NOVEL_ID))).thenReturn(List.of(novel()));
        when(likeRepository.likedTargetIds(LikeTargetType.REVIEW, List.of(REVIEW_ID), VIEWER_ID))
                .thenReturn(Set.of(REVIEW_ID));

        PageResult<UserReviewView> page = reviewService.listByUser(USER_ID, 1, 10, VIEWER_ID);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList()).hasSize(1);
        UserReviewView view = page.getList().get(0);
        assertThat(view.novelTitle()).isEqualTo("剑来风雪");
        assertThat(view.nickname()).isEqualTo("书虫");
        assertThat(view.liked()).isTrue();
        assertThat(view.score()).isEqualTo(5);
    }

    private Novel novel() {
        return new Novel(NOVEL_ID, "剑来风雪", "温酒", null, 1L, 0, "简介",
                2756, 10, 1L, LocalDateTime.now(), 5L, 1, BigDecimal.valueOf(5),
                0L, 0, 0, BigDecimal.ZERO, 1L, LocalDateTime.now());
    }
}
