package com.novel.core.rank.application;

import com.novel.core.novel.application.NovelService;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.ChapterRepository;
import com.novel.core.novel.port.NovelCache;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.novel.port.NovelViewCounter;
import com.novel.core.rank.domain.RankAction;
import com.novel.core.rank.port.RankScoreStore;
import com.novel.core.reading.application.BookshelfService;
import com.novel.core.reading.port.BookshelfRepository;
import com.novel.core.review.application.RatingService;
import com.novel.core.review.application.ReviewService;
import com.novel.core.review.domain.Review;
import com.novel.core.review.port.LikeRepository;
import com.novel.core.review.port.ReviewRepository;
import com.novel.core.user.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 记账点：哪些行为会被算进榜单，以及在哪些情况下<b>不该</b>算。
 *
 * <p>后半句更重要。收藏和书评的加分会跟着业务事务一起回滚不了（Redis 不回滚），
 * 所以只能在"确定发生了"的地方记——幂等分支提前 return 的那条路径必须不记账，
 * 否则一个接口被前端轮询几次，热度就自己涨上去了。
 */
class RankRecordingTest {

    private final RankScoreStore scoreStore = mock(RankScoreStore.class);
    private final NovelRepository novelRepository = mock(NovelRepository.class);
    private final ChapterRepository chapterRepository = mock(ChapterRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    @Test
    @DisplayName("打开详情页记一次阅读，带上 viewerKey 供去重")
    void detail_recordsView() {
        NovelCache cache = mock(NovelCache.class);
        NovelViewCounter viewCounter = mock(NovelViewCounter.class);
        when(novelRepository.findById(1L)).thenReturn(Optional.of(novel(1)));
        when(viewCounter.pendingDelta(1L)).thenReturn(5L);

        new NovelService(novelRepository, chapterRepository, cache, viewCounter, scoreStore)
                .detail(1L, "u:9");

        verify(scoreStore).recordView("u:9", 1L);
        // 原有的阅读数累加不能被这次改造弄丢
        verify(viewCounter).increment(1L);
    }

    @Test
    @DisplayName("加入书架加一次收藏分")
    void bookshelfAdd_recordsFavorite() {
        BookshelfRepository shelf = mock(BookshelfRepository.class);
        when(novelRepository.findById(1L)).thenReturn(Optional.of(novel(1)));
        when(shelf.exists(9L, 1L)).thenReturn(false);

        new BookshelfService(shelf, novelRepository, chapterRepository, userRepository, scoreStore)
                .add(9L, 1L);

        verify(scoreStore).recordAction(1L, RankAction.FAVORITE, 1);
    }

    @Test
    @DisplayName("重复加入书架不重复加分：幂等分支提前返回")
    void bookshelfAdd_alreadyExists_recordsNothing() {
        BookshelfRepository shelf = mock(BookshelfRepository.class);
        when(novelRepository.findById(1L)).thenReturn(Optional.of(novel(1)));
        when(shelf.exists(9L, 1L)).thenReturn(true);

        new BookshelfService(shelf, novelRepository, chapterRepository, userRepository, scoreStore)
                .add(9L, 1L);

        verify(scoreStore, never()).recordAction(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("取消收藏扣一次收藏分：只加不减的话，收藏→取消→再收藏就是免费刷分")
    void bookshelfRemove_recordsNegative() {
        BookshelfRepository shelf = mock(BookshelfRepository.class);
        when(shelf.exists(9L, 1L)).thenReturn(true);

        new BookshelfService(shelf, novelRepository, chapterRepository, userRepository, scoreStore)
                .remove(9L, 1L);

        verify(scoreStore).recordAction(1L, RankAction.FAVORITE, -1);
    }

    @Test
    @DisplayName("发书评加一次书评分")
    void reviewCreate_recordsReview() {
        ReviewRepository reviews = mock(ReviewRepository.class);
        RatingService rating = mock(RatingService.class);
        when(novelRepository.findById(1L)).thenReturn(Optional.of(novel(1)));
        when(reviews.create(any())).thenReturn(7L);
        when(reviews.findById(7L)).thenReturn(Optional.of(
                new Review(7L, 1L, 9L, "好", 5, 0, 0, LocalDateTime.of(2026, 11, 5, 10, 0))));

        service(reviews, rating).create(1L, 9L, "好", 5);

        verify(scoreStore).recordAction(1L, RankAction.REVIEW, 1);
    }

    @Test
    @DisplayName("删书评扣一次书评分")
    void reviewDelete_recordsNegative() {
        ReviewRepository reviews = mock(ReviewRepository.class);
        RatingService rating = mock(RatingService.class);
        when(reviews.findById(7L)).thenReturn(Optional.of(
                new Review(7L, 1L, 9L, "好", null, 2, 0, LocalDateTime.of(2026, 11, 5, 10, 0))));

        service(reviews, rating).delete(7L, 9L);

        verify(scoreStore).recordAction(1L, RankAction.REVIEW, -1);
    }

    @Test
    @DisplayName("发书评同步维护 novel.review_count：总榜靠这一列算书评权重")
    void reviewCreate_updatesNovelReviewCount() {
        ReviewRepository reviews = mock(ReviewRepository.class);
        RatingService rating = mock(RatingService.class);
        when(novelRepository.findById(1L)).thenReturn(Optional.of(novel(1)));
        when(reviews.create(any())).thenReturn(7L);
        when(reviews.findById(7L)).thenReturn(Optional.of(
                new Review(7L, 1L, 9L, "好", 5, 0, 0, LocalDateTime.of(2026, 11, 5, 10, 0))));

        service(reviews, rating).create(1L, 9L, "好", 5);

        verify(novelRepository).addReviewCount(1L, 1);
    }

    @Test
    @DisplayName("删书评把 novel.review_count 扣回去")
    void reviewDelete_decrementsNovelReviewCount() {
        ReviewRepository reviews = mock(ReviewRepository.class);
        RatingService rating = mock(RatingService.class);
        when(reviews.findById(7L)).thenReturn(Optional.of(
                new Review(7L, 1L, 9L, "好", null, 2, 0, LocalDateTime.of(2026, 11, 5, 10, 0))));

        service(reviews, rating).delete(7L, 9L);

        verify(novelRepository).addReviewCount(1L, -1);
    }

    private ReviewService service(ReviewRepository reviews, RatingService rating) {
        return new ReviewService(reviews, novelRepository, userRepository,
                mock(LikeRepository.class), rating, scoreStore);
    }

    private static Novel novel(long id) {
        return new Novel(id, "书" + id, "作者", null, 1L, Novel.STATUS_ONGOING, "简介", 1000, 10, null,
                LocalDateTime.of(2026, 11, 4, 12, 0), 0, 0, BigDecimal.ZERO,
                100, 5, 2, BigDecimal.ZERO, 0, LocalDateTime.of(2026, 10, 1, 0, 0));
    }
}
