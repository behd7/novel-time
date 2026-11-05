package com.novel.core.rank.application;

import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.rank.application.view.RankItemView;
import com.novel.core.rank.domain.RankAction;
import com.novel.core.rank.domain.RankEntry;
import com.novel.core.rank.domain.RankPeriod;
import com.novel.core.rank.domain.ScoredNovel;
import com.novel.core.rank.port.RankCache;
import com.novel.core.rank.port.RankScoreStore;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算链路。
 *
 * <p>这条链路只有一个目标：**把实时层的桶换成一份可以被稳定读取的快照**。
 * 所以测试全部围绕"换进去的东西对不对"展开——顺序、名次、截断、已删的书、
 * 跨周期边界，以及"刷新两次结果一样"这条幂等性。
 *
 * <p>幂等性在这里不是洁癖：结算每 10 分钟跑一次，一天 144 次，
 * 任何一点不幂等都会在 `novel_rank` 里堆出肉眼可见的脏数据。
 */
class RankRefreshServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private final FakeScoreStore scoreStore = new FakeScoreStore();
    private final FakeSnapshotRepository snapshots = new FakeSnapshotRepository();
    private final FakeRankCache cache = new FakeRankCache();
    private final NovelRepository novels = mock(NovelRepository.class);

    private RankRefreshService service(int snapshotSize) {
        return new RankRefreshService(scoreStore, snapshots, novels, cache,
                new HotScoreCalculator(1, 3, 2, 0.5, 2), new RankSettings(snapshotSize), CLOCK);
    }

    @BeforeEach
    void stubEmptyDatabase() {
        when(novels.findByIds(anyCollection())).thenReturn(List.of());
        when(novels.pageAfterId(anyLong(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("日榜按行为量降序写快照，名次从 1 开始连续")
    void dailyRank_ordersByBehavior() {
        givenNovels(novel(1), novel(2), novel(3));
        scoreStore.put(RankPeriod.DAILY, TODAY, 1, 100);
        scoreStore.put(RankPeriod.DAILY, TODAY, 2, 300);
        scoreStore.put(RankPeriod.DAILY, TODAY, 3, 200);

        service(10).refresh(TODAY);

        assertThat(snapshots.get(RankPeriod.DAILY, TODAY))
                .extracting(RankEntry::novelId, RankEntry::rankNo)
                .containsExactly(tuple(2L, 1), tuple(3L, 2), tuple(1L, 3));
        assertThat(snapshots.get(RankPeriod.DAILY, TODAY))
                .extracting(RankEntry::score)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("已删除的书不进快照：ZSET 里还留着它，但它已经不是候选了")
    void deletedNovel_skipped() {
        givenNovels(novel(1));
        scoreStore.put(RankPeriod.DAILY, TODAY, 1, 100);
        scoreStore.put(RankPeriod.DAILY, TODAY, 2, 300);

        service(10).refresh(TODAY);

        assertThat(snapshots.get(RankPeriod.DAILY, TODAY))
                .extracting(RankEntry::novelId)
                .containsExactly(1L);
        // 名次不能因为跳过而出现空洞
        assertThat(snapshots.get(RankPeriod.DAILY, TODAY))
                .extracting(RankEntry::rankNo)
                .containsExactly(1);
    }

    @Test
    @DisplayName("刷新两次结果完全一样：结算必须幂等")
    void refresh_isIdempotent() {
        givenNovels(novel(1), novel(2));
        scoreStore.put(RankPeriod.DAILY, TODAY, 1, 100);
        scoreStore.put(RankPeriod.DAILY, TODAY, 2, 300);

        RankRefreshService service = service(10);
        service.refresh(TODAY);
        List<RankEntry> first = snapshots.get(RankPeriod.DAILY, TODAY);
        service.refresh(TODAY);

        assertThat(snapshots.get(RankPeriod.DAILY, TODAY)).isEqualTo(first);
    }

    @Test
    @DisplayName("快照截断到配置的条数，不把整个 ZSET 都写进库")
    void snapshot_truncatedToConfiguredSize() {
        givenNovels(novel(1), novel(2), novel(3), novel(4), novel(5));
        scoreStore.put(RankPeriod.DAILY, TODAY, 1, 500);
        scoreStore.put(RankPeriod.DAILY, TODAY, 2, 400);
        scoreStore.put(RankPeriod.DAILY, TODAY, 3, 300);
        scoreStore.put(RankPeriod.DAILY, TODAY, 4, 200);
        scoreStore.put(RankPeriod.DAILY, TODAY, 5, 100);

        service(3).refresh(TODAY);

        assertThat(snapshots.get(RankPeriod.DAILY, TODAY))
                .extracting(RankEntry::novelId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("跨周期边界：昨天那个桶也要结算一次，23:50 之后的行为不能丢")
    void settlesYesterdayBucketToo() {
        givenNovels(novel(1));
        scoreStore.put(RankPeriod.DAILY, YESTERDAY, 1, 50);

        service(10).refresh(TODAY);

        assertThat(snapshots.get(RankPeriod.DAILY, YESTERDAY))
                .extracting(RankEntry::novelId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("周榜：今天和昨天同属一周，只结算一次，不会写重复的 rank_date")
    void weekly_settlesOncePerWeek() {
        givenNovels(novel(1));
        scoreStore.put(RankPeriod.WEEKLY, TODAY, 1, 50);

        service(10).refresh(TODAY);

        assertThat(snapshots.replaceCount(RankPeriod.WEEKLY)).isEqualTo(1);
        assertThat(snapshots.get(RankPeriod.WEEKLY, TODAY))
                .extracting(RankEntry::rankNo)
                .containsExactly(1);
    }

    @Test
    @DisplayName("总榜读库里的累计列，不依赖实时层")
    void allRank_usesDatabaseCounters() {
        givenNovels(novel(1), novel(2), novel(3));
        givenScan(novelWithCounters(1, 1000, 10, 1), novelWithCounters(2, 10, 0, 0),
                novelWithCounters(3, 100, 1, 0));

        service(10).refresh(TODAY);

        // raw: 1 → 1032、3 → 103、2 → 10
        assertThat(snapshots.get(RankPeriod.ALL, LocalDate.of(1970, 1, 1)))
                .extracting(RankEntry::novelId)
                .containsExactly(1L, 3L, 2L);
    }

    @Test
    @DisplayName("总榜顺带回写 hot_score，用同一次算出来的分数")
    void allRank_writesHotScore() {
        givenScan(novelWithCounters(1, 1000, 10, 1));

        service(10).refresh(TODAY);

        verify(novels).updateHotScores(org.mockito.ArgumentMatchers.argThat(scores -> {
            BigDecimal score = scores.get(1L);
            // log10(1 + 1032) / sqrt(1 + 2)
            return score != null && Math.abs(score.doubleValue() - 1.7402) < 0.001;
        }));
    }

    @Test
    @DisplayName("结算后清缓存，否则刷新完还要等 TTL 才生效")
    void refresh_evictsCache() {
        givenNovels(novel(1));
        scoreStore.put(RankPeriod.DAILY, TODAY, 1, 100);
        cache.put(RankPeriod.DAILY, TODAY, List.of());

        service(10).refresh(TODAY);

        assertThat(cache.get(RankPeriod.DAILY, TODAY)).isNull();
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

    private void givenScan(Novel... values) {
        List<Novel> all = List.of(values);
        when(novels.pageAfterId(anyLong(), anyInt())).thenAnswer(invocation -> {
            long after = invocation.getArgument(0);
            int limit = invocation.getArgument(1);
            return all.stream().filter(n -> n.id() > after).limit(limit).toList();
        });
    }

    private static Novel novel(long id) {
        return novelWithCounters(id, 0, 0, 0);
    }

    /** 一天前刚更新过，所以总榜衰减的分母是 sqrt(1 + 2)。 */
    private static Novel novelWithCounters(long id, long views, int favorites, int reviews) {
        return new Novel(id, "书" + id, "作者", null, 1L, Novel.STATUS_ONGOING, "简介",
                1000, 10, null, NOW.minusDays(1), 0, 0, BigDecimal.ZERO,
                views, favorites, reviews, BigDecimal.ZERO, 0, NOW.minusDays(30));
    }

    // ------------------------------------------------------------------ 内存替身

    private static final class FakeScoreStore implements RankScoreStore {

        private final Map<String, Map<Long, Long>> buckets = new HashMap<>();

        void put(RankPeriod period, LocalDate rankDate, long novelId, long score) {
            buckets.computeIfAbsent(period.bucket(rankDate), key -> new HashMap<>()).put(novelId, score);
        }

        @Override
        public void recordView(String viewerKey, long novelId) {
            throw new UnsupportedOperationException("结算不该写实时层");
        }

        @Override
        public void recordAction(long novelId, RankAction action, int delta) {
            throw new UnsupportedOperationException("结算不该写实时层");
        }

        @Override
        public List<ScoredNovel> top(RankPeriod period, LocalDate rankDate, int limit) {
            return buckets.getOrDefault(period.bucket(rankDate), Map.of()).entrySet().stream()
                    .map(entry -> new ScoredNovel(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparingLong(ScoredNovel::score).reversed()
                            .thenComparingLong(ScoredNovel::novelId))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class FakeSnapshotRepository implements RankSnapshotRepository {

        private final Map<String, List<RankEntry>> store = new HashMap<>();
        private final Map<String, Integer> replaces = new HashMap<>();

        @Override
        public void replace(RankPeriod period, LocalDate rankDate, List<RankEntry> entries) {
            store.put(period.bucket(rankDate), List.copyOf(entries));
            replaces.merge(period.bucket(rankDate), 1, Integer::sum);
        }

        @Override
        public List<RankEntry> top(RankPeriod period, LocalDate rankDate, int limit) {
            return store.getOrDefault(period.bucket(rankDate), List.of()).stream().limit(limit).toList();
        }

        List<RankEntry> get(RankPeriod period, LocalDate rankDate) {
            return store.getOrDefault(period.bucket(rankDate), List.of());
        }

        int replaceCount(RankPeriod period) {
            return replaces.getOrDefault(period.bucket(TODAY), 0);
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
