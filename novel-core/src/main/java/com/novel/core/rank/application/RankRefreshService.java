package com.novel.core.rank.application;

import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.rank.domain.RankEntry;
import com.novel.core.rank.domain.RankPeriod;
import com.novel.core.rank.domain.ScoredNovel;
import com.novel.core.rank.port.RankCache;
import com.novel.core.rank.port.RankScoreStore;
import com.novel.core.rank.port.RankSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 榜单结算：把实时层的桶换成一份可以被稳定读取的快照。
 *
 * <p>四个周期一起算，但<b>数据来源分两种</b>：
 * <ul>
 *   <li>日 / 周 / 月 —— 读实时层，要的是"窗口内"的行为量；</li>
 *   <li>总榜 —— 读 {@code novel} 表的累计列。</li>
 * </ul>
 * 总榜为什么不走实时层：{@code rank:novel:all} 只能从上线那天开始累加，
 * M2 导入的那批书在它眼里是零分，总榜从第一天起就是错的。
 * 而累计列本来就是总榜要的那个数，还是现成的。
 *
 * <p><b>跨周期边界</b>：每轮同时结算"今天"和"昨天"两个日期。日榜跨天时两天不同，
 * 各算一次；周榜和月榜在同周期内两天会归到同一个 {@code rank_date}，去重后只剩一次。
 * 少了这一步，23:50 之后的那批行为就永远进不了昨天的榜——
 * 而它恰恰是结算前最后一段、也是最热的一段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankRefreshService {

    /** 全表扫描的批大小，也是每批 hot_score 回写的条数。 */
    private static final int SCAN_BATCH = 200;

    /** 从实时层多取一倍：其中一部分可能是已下架的书，取回来还要过滤。 */
    private static final int FETCH_FACTOR = 2;

    private final RankScoreStore scoreStore;
    private final RankSnapshotRepository snapshotRepository;
    private final NovelRepository novelRepository;
    private final RankCache rankCache;
    private final HotScoreCalculator calculator;
    private final RankSettings settings;
    private final Clock clock;

    public void refresh(LocalDate today) {
        LocalDateTime now = LocalDateTime.now(clock);

        for (RankPeriod period : RankPeriod.values()) {
            if (period == RankPeriod.ALL) {
                settleAll(now);
                continue;
            }
            for (LocalDate rankDate : distinctRankDates(period, today)) {
                settleFromStore(period, rankDate, now);
            }
        }
    }

    /**
     * 今天和昨天各自所属周期的起点，去重后返回。
     *
     * <p>周榜的两天通常落在同一个周一，去重后只结算一次，避免对同一份 {@code rank_date} 重复替换。
     */
    private Set<LocalDate> distinctRankDates(RankPeriod period, LocalDate today) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        dates.add(period.rankDate(today));
        dates.add(period.rankDate(today.minusDays(1)));
        return dates;
    }

    private void settleFromStore(RankPeriod period, LocalDate rankDate, LocalDateTime now) {
        List<ScoredNovel> scored = scoreStore.top(period, rankDate, settings.snapshotSize() * FETCH_FACTOR);

        List<RankEntry> entries = List.of();
        if (!scored.isEmpty()) {
            Map<Long, Novel> novels = novelRepository.findByIds(
                            scored.stream().map(ScoredNovel::novelId).toList())
                    .stream()
                    .collect(Collectors.toMap(Novel::id, Function.identity(), (a, b) -> a));

            entries = rank(scored.stream()
                    .filter(candidate -> novels.containsKey(candidate.novelId()))
                    .map(candidate -> new Candidate(candidate.novelId(),
                            calculator.score(period, candidate.score(),
                                    novels.get(candidate.novelId()).latestUpdateAt(), now)))
                    .toList());
        }

        snapshotRepository.replace(period, rankDate, entries);
        rankCache.evict(period, rankDate);
        log.info("榜单已刷新 period={} bucket={} size={}", period, period.bucket(rankDate), entries.size());
    }

    private void settleAll(LocalDateTime now) {
        List<Candidate> candidates = new ArrayList<>();
        long cursor = 0;
        while (true) {
            List<Novel> batch = novelRepository.pageAfterId(cursor, SCAN_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            cursor = batch.get(batch.size() - 1).id();

            Map<Long, BigDecimal> hotScores = new LinkedHashMap<>();
            for (Novel novel : batch) {
                long raw = calculator.raw(novel.viewCount(), novel.favoriteCount(), novel.reviewCount());
                double score = calculator.score(RankPeriod.ALL, raw, novel.latestUpdateAt(), now);
                candidates.add(new Candidate(novel.id(), score));
                hotScores.put(novel.id(), BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP));
            }
            // 分批回写：热度分不参与排序，边扫边写，不必把十万本书的 map 攒在内存里
            novelRepository.updateHotScores(hotScores);
        }

        snapshotRepository.replace(RankPeriod.ALL, RankPeriod.ALL_RANK_DATE, rank(candidates));
        rankCache.evict(RankPeriod.ALL, RankPeriod.ALL_RANK_DATE);
        log.info("榜单已刷新 period={} bucket=all candidates={}", RankPeriod.ALL, candidates.size());
    }

    /**
     * 排序、截断、编名次。
     *
     * <p>同分时按 novelId 升序，是为了让名次稳定：允许并列会让分页出现重复或漏项，
     * 而"同一份快照刷两次得到两个顺序"会让幂等性测试直接失败。
     */
    private List<RankEntry> rank(List<Candidate> candidates) {
        List<Candidate> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparingLong(Candidate::novelId))
                .limit(settings.snapshotSize())
                .toList();

        List<RankEntry> entries = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            entries.add(new RankEntry(sorted.get(i).novelId(), i + 1, sorted.get(i).score()));
        }
        return entries;
    }

    /** 排好序的候选，只有 novelId 和分数。 */
    private record Candidate(long novelId, double score) {
    }
}
