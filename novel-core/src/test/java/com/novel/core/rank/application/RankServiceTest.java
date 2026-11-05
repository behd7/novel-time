package com.novel.core.rank.application;

import com.novel.common.result.PageResult;
import com.novel.core.novel.application.view.NovelSummaryView;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.rank.application.view.RankItemView;
import com.novel.core.rank.domain.RankEntry;
import com.novel.core.rank.domain.RankPeriod;
import com.novel.core.rank.port.RankCache;
import com.novel.core.rank.port.RankSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 榜单读取。
 *
 * <p>读路径的逻辑都在三件事上：缓存命中就不该回源、回源只查一次 {@code IN}、
 * 分页在内存里切。其中"缓存命中不回源"最值得测——它一旦失效，
 * 榜单页的每个请求都会打到 MySQL，而这个问题在功能上完全看不出来，只会在压测时暴露。
 */
class RankServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final Clock CLOCK =
            Clock.fixed(LocalDateTime.of(2026, 11, 5, 12, 0).atZone(ZONE).toInstant(), ZONE);

    private final FakeSnapshotRepository snapshots = new FakeSnapshotRepository();
    private final FakeRankCache cache = new FakeRankCache();
    private final NovelRepository novels = mock(NovelRepository.class);
    private final RankService service = new RankService(snapshots, novels, cache, new RankSettings(10), CLOCK);

    @BeforeEach
    void stubEmptyDatabase() {
        when(novels.findByIds(anyCollection())).thenReturn(List.of());
    }

    @Test
    @DisplayName("缓存命中直接返回，不碰快照表")
    void cacheHit_skipsSnapshot() {
        RankItemView cached = new RankItemView(1, 2.5, summary(1, "甲"));
        cache.put(RankPeriod.DAILY, TODAY, List.of(cached));

        PageResult<RankItemView> page = service.list(new RankQuery(RankPeriod.DAILY, 1, 20));

        assertThat(page.getList()).containsExactly(cached);
        assertThat(snapshots.topCalls).isZero();
    }

    @Test
    @DisplayName("缓存未命中：读快照、拼卡片、回填缓存")
    void cacheMiss_readsSnapshotAndFillsCache() {
        snapshots.replace(RankPeriod.DAILY, TODAY,
                List.of(new RankEntry(1, 1, 2.5), new RankEntry(2, 2, 1.5)));
        givenNovels(novel(1, "甲"), novel(2, "乙"));

        PageResult<RankItemView> page = service.list(new RankQuery(RankPeriod.DAILY, 1, 20));

        assertThat(page.getList()).extracting(RankItemView::rankNo).containsExactly(1, 2);
        assertThat(page.getList()).extracting(view -> view.novel().title()).containsExactly("甲", "乙");
        assertThat(page.getList()).extracting(RankItemView::score).containsExactly(2.5, 1.5);
        assertThat(cache.get(RankPeriod.DAILY, TODAY)).hasSize(2);
    }

    @Test
    @DisplayName("空快照返回空列表，total 为 0，不抛异常")
    void emptySnapshot_returnsEmptyPage() {
        PageResult<RankItemView> page = service.list(new RankQuery(RankPeriod.DAILY, 1, 20));

        assertThat(page.getList()).isEmpty();
        assertThat(page.getTotal()).isZero();
    }

    @Test
    @DisplayName("翻页在内存里切，total 仍是快照全量")
    void page2_slicesList() {
        snapshots.replace(RankPeriod.DAILY, TODAY, List.of(
                new RankEntry(1, 1, 5.0), new RankEntry(2, 2, 4.0), new RankEntry(3, 3, 3.0),
                new RankEntry(4, 4, 2.0), new RankEntry(5, 5, 1.0)));
        givenNovels(novel(1, "甲"), novel(2, "乙"), novel(3, "丙"), novel(4, "丁"), novel(5, "戊"));

        PageResult<RankItemView> page = service.list(new RankQuery(RankPeriod.DAILY, 2, 2));

        assertThat(page.getList()).extracting(RankItemView::rankNo).containsExactly(3, 4);
        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.isHasNext()).isTrue();
    }

    @Test
    @DisplayName("翻过最后一页返回空列表，不抛越界")
    void beyondLastPage_returnsEmpty() {
        snapshots.replace(RankPeriod.DAILY, TODAY, List.of(new RankEntry(1, 1, 5.0)));
        givenNovels(novel(1, "甲"));

        PageResult<RankItemView> page = service.list(new RankQuery(RankPeriod.DAILY, 9, 20));

        assertThat(page.getList()).isEmpty();
        assertThat(page.getTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("快照里的书已被删除时跳过该条，不让整个接口 500")
    void missingNovel_skipped() {
        snapshots.replace(RankPeriod.DAILY, TODAY,
                List.of(new RankEntry(1, 1, 5.0), new RankEntry(2, 2, 4.0)));
        givenNovels(novel(1, "甲"));

        PageResult<RankItemView> page = service.list(new RankQuery(RankPeriod.DAILY, 1, 20));

        assertThat(page.getList()).extracting(view -> view.novel().id()).containsExactly(1L);
    }

    @Test
    @DisplayName("非法周期交给 RankQuery 拒绝，读接口不会收到 null")
    void nullPeriod_rejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new RankQuery(null, 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ 夹具

    private void givenNovels(Novel... values) {
        Map<Long, Novel> byId = new HashMap<>();
        for (Novel novel : values) {
            byId.put(novel.id(), novel);
        }
        when(novels.findByIds(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        });
    }

    private static Novel novel(long id, String title) {
        return new Novel(id, title, "作者", null, 1L, Novel.STATUS_ONGOING, "简介", 1000, 10, null,
                LocalDateTime.of(2026, 11, 4, 12, 0), 0, 0, BigDecimal.ZERO,
                100, 5, 2, BigDecimal.ZERO, 0, LocalDateTime.of(2026, 10, 1, 0, 0));
    }

    private static NovelSummaryView summary(long id, String title) {
        return new NovelSummaryView(id, title, "作者", null, 1L, Novel.STATUS_ONGOING, "简介",
                1000, 10, BigDecimal.ZERO, 0, 100, 5, LocalDateTime.of(2026, 11, 4, 12, 0));
    }

    // ------------------------------------------------------------------ 内存替身

    private static final class FakeSnapshotRepository implements RankSnapshotRepository {

        private final Map<String, List<RankEntry>> store = new HashMap<>();
        private int topCalls;

        @Override
        public void replace(RankPeriod period, LocalDate rankDate, List<RankEntry> entries) {
            store.put(period.bucket(rankDate), List.copyOf(entries));
        }

        @Override
        public List<RankEntry> top(RankPeriod period, LocalDate rankDate, int limit) {
            topCalls++;
            return store.getOrDefault(period.bucket(rankDate), List.of()).stream().limit(limit).toList();
        }
    }

    private static final class FakeRankCache implements RankCache {

        private final Map<String, List<RankItemView>> store = new HashMap<>();

        @Override
        public List<RankItemView> get(RankPeriod period, LocalDate rankDate) {
            return store.get(period.bucket(rankDate));
        }

        @Override
        public void put(RankPeriod period, LocalDate rankDate, List<RankItemView> items) {
            store.put(period.bucket(rankDate), List.copyOf(items));
        }

        @Override
        public void evict(RankPeriod period, LocalDate rankDate) {
            store.remove(period.bucket(rankDate));
        }
    }
}
