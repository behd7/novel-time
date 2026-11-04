package com.novel.core.review.application;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.review.port.RatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评分聚合的单元测试。
 *
 * <p>这里的重点不是"能打分"，而是**增量算得对不对**：
 * 首次打分人数 +1，改分人数不变只补差值，同分不动聚合值，取消要把两个值都退回去。
 * 这四种情况算错任意一个，页面上就会出现"3 个人打分平均分 4.9"这种假数据，
 * 而且只有真去点几遍才会发现。
 */
@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    private static final long NOVEL_ID = 1L;
    private static final long USER_ID = 100L;

    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private NovelRepository novelRepository;

    private RatingService ratingService;

    private final Novel novel = new Novel(NOVEL_ID, "青云志", "佚名", null, 1L, 0, "简介",
            900, 5, 10L, LocalDateTime.now(), 0, BigDecimal.ZERO, 0L, 0, 0, LocalDateTime.now());

    @BeforeEach
    void setUp() {
        ratingService = new RatingService(ratingRepository, novelRepository);
    }

    @Test
    @DisplayName("首次打分：人数 +1，总分加分数")
    void firstRating_incrementsCountAndSum() {
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));
        when(ratingRepository.findScore(NOVEL_ID, USER_ID)).thenReturn(Optional.empty());
        when(ratingRepository.distribution(NOVEL_ID)).thenReturn(Map.of(5, 1L));

        ratingService.rate(NOVEL_ID, USER_ID, 5);

        verify(ratingRepository).save(NOVEL_ID, USER_ID, 5);
        verify(novelRepository).addRating(NOVEL_ID, 5, 1);
    }

    @Test
    @DisplayName("改分：只补总分差值，人数不变")
    void changingScore_onlyAdjustsSum() {
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));
        when(ratingRepository.findScore(NOVEL_ID, USER_ID)).thenReturn(Optional.of(3));
        when(ratingRepository.distribution(NOVEL_ID)).thenReturn(Map.of(5, 1L));

        ratingService.rate(NOVEL_ID, USER_ID, 5);

        verify(ratingRepository).save(NOVEL_ID, USER_ID, 5);
        // 3 星改 5 星，总分 +2，人数 +0
        verify(novelRepository).addRating(NOVEL_ID, 2, 0);
    }

    @Test
    @DisplayName("改成同一个分数：什么都不用动")
    void sameScore_isNoop() {
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));
        when(ratingRepository.findScore(NOVEL_ID, USER_ID)).thenReturn(Optional.of(4));
        when(ratingRepository.distribution(NOVEL_ID)).thenReturn(Map.of(4, 1L));

        ratingService.rate(NOVEL_ID, USER_ID, 4);

        verify(ratingRepository, never()).save(anyLong(), anyLong(), anyInt());
        verify(novelRepository, never()).addRating(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("取消评分：总分与人数都退回去")
    void removingRating_revertsBothValues() {
        when(ratingRepository.findScore(NOVEL_ID, USER_ID)).thenReturn(Optional.of(4));
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));
        when(ratingRepository.distribution(NOVEL_ID)).thenReturn(Map.of());

        ratingService.remove(NOVEL_ID, USER_ID);

        verify(ratingRepository).delete(NOVEL_ID, USER_ID);
        verify(novelRepository).addRating(NOVEL_ID, -4, -1);
    }

    @Test
    @DisplayName("没有打过分也能取消（幂等）")
    void removingWithoutRating_isNoop() {
        when(ratingRepository.findScore(NOVEL_ID, USER_ID)).thenReturn(Optional.empty());
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));
        when(ratingRepository.distribution(NOVEL_ID)).thenReturn(Map.of());

        ratingService.remove(NOVEL_ID, USER_ID);

        verify(ratingRepository, never()).delete(anyLong(), anyLong());
        verify(novelRepository, never()).addRating(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("0 星或 6 星被拒，且不落库")
    void invalidScore_rejected() {
        BizException tooLow = catchThrowableOfType(
                () -> ratingService.rate(NOVEL_ID, USER_ID, 0), BizException.class);
        BizException tooHigh = catchThrowableOfType(
                () -> ratingService.rate(NOVEL_ID, USER_ID, 6), BizException.class);

        assertThat(tooLow.getResultCode()).isEqualTo(ResultCode.BAD_REQUEST);
        assertThat(tooHigh.getResultCode()).isEqualTo(ResultCode.BAD_REQUEST);
        verify(ratingRepository, never()).save(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("给不存在的小说打分：10201")
    void ratingMissingNovel_rejected() {
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.empty());

        BizException ex = catchThrowableOfType(
                () -> ratingService.rate(NOVEL_ID, USER_ID, 5), BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.NOVEL_NOT_FOUND);
    }
}
