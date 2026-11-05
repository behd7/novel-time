package com.novel.infra.cache;

import com.novel.core.rank.domain.RankAction;
import com.novel.core.rank.domain.RankPeriod;
import com.novel.core.rank.domain.ScoredNovel;
import com.novel.core.rank.port.RankScoreStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 榜单实时层（Redis ZSET）。
 *
 * <p>一次阅读要同时落到日/周/月三个桶里，还要在去重 Hash 里登记一次。四件事必须原子，
 * 所以走一段 Lua：如果分成四条命令，进程在中间挂掉会出现"去重登记了但分数没加"——
 * 这个人今天再看这本书就永远不计分了。
 *
 * <p>去重用一个大 Hash（{@code rank:seen:{yyyyMMdd}}）而不是每人一个 key，
 * 理由和 {@code novel:view:delta} 一样：键空间不膨胀，{@code HSETNX} 是 O(1)。
 * 键上带日期，跨天自然换一个 Hash，不需要任何清理逻辑。
 *
 * <p>每个 ZSET 都补 {@code PEXPIRE}：桶只在最近一段时间内有用，
 * 过期时间跟着最后一次写入往后顺延，日桶留 3 天（要能结算昨天），周桶留 4 周，月桶留 100 天。
 * 不设过期的话，键空间只增不减，运维半年后才发现 Redis 里躺着几十万个死键。
 */
@Slf4j
public class RedisRankScoreStore implements RankScoreStore {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private static final Duration SEEN_TTL = Duration.ofDays(3);
    private static final Duration DAILY_TTL = Duration.ofDays(3);
    private static final Duration WEEKLY_TTL = Duration.ofDays(28);
    private static final Duration MONTHLY_TTL = Duration.ofDays(100);

    /** 去重 + 三桶累加，一次往返。KEYS: 去重 Hash、日桶、周桶、月桶。 */
    private static final RedisScript<Long> RECORD_VIEW = new DefaultRedisScript<>("""
            if redis.call('HSETNX', KEYS[1], ARGV[1], '1') == 0 then
                return 0
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('ZINCRBY', KEYS[2], ARGV[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[2], ARGV[5])
            redis.call('ZINCRBY', KEYS[3], ARGV[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[3], ARGV[6])
            redis.call('ZINCRBY', KEYS[4], ARGV[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[4], ARGV[7])
            return 1
            """, Long.class);

    /** 收藏 / 书评的加减，不去重。KEYS: 日桶、周桶、月桶。 */
    private static final RedisScript<Long> RECORD_ACTION = new DefaultRedisScript<>("""
            for i = 1, #KEYS do
                redis.call('ZINCRBY', KEYS[i], ARGV[1], ARGV[2])
                redis.call('PEXPIRE', KEYS[i], ARGV[i + 2])
            end
            return #KEYS
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int viewWeight;
    private final int favoriteWeight;
    private final int reviewWeight;
    private final Clock clock;

    public RedisRankScoreStore(StringRedisTemplate redis, int viewWeight, int favoriteWeight,
                               int reviewWeight, Clock clock) {
        this.redis = redis;
        this.viewWeight = viewWeight;
        this.favoriteWeight = favoriteWeight;
        this.reviewWeight = reviewWeight;
        this.clock = clock;
    }

    /**
     * 桶的键。
     *
     * <p>必须带周期名：{@code daily:20261105} 和 {@code monthly:202611} 的桶名不会撞，
     * 但 {@code weekly:2026W45} 与 {@code daily:20261105} 摆在一起时，没有周期段就看不出谁是谁；
     * 更重要的是运维照着文档 {@code rank:novel:daily:*} 去扫，不该扫不到。
     */
    static String zsetKey(RankPeriod period, LocalDate day) {
        return "rank:novel:" + period.name().toLowerCase(Locale.ROOT) + ":" + period.bucket(day);
    }

    private static String seenKey(LocalDate day) {
        return "rank:seen:" + day.format(DAY);
    }

    @Override
    public void recordView(String viewerKey, long novelId) {
        if (viewerKey == null || viewerKey.isBlank() || novelId <= 0) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        String field = novelId + "|" + viewerKey;

        try {
            redis.execute(RECORD_VIEW,
                    List.of(seenKey(today),
                        zsetKey(RankPeriod.DAILY, today),
                        zsetKey(RankPeriod.WEEKLY, today),
                        zsetKey(RankPeriod.MONTHLY, today)),
                    field,
                    String.valueOf(viewWeight),
                    String.valueOf(novelId),
                    String.valueOf(SEEN_TTL.toMillis()),
                    String.valueOf(DAILY_TTL.toMillis()),
                    String.valueOf(WEEKLY_TTL.toMillis()),
                    String.valueOf(MONTHLY_TTL.toMillis()));
        } catch (Exception e) {
            // 榜单是近似指标：Redis 抖一下只是这一次没计分，不能让详情页跟着 500
            log.warn("阅读记账失败，本次不计入榜单 novelId={}：{}", novelId, e.getMessage());
        }
    }

    @Override
    public void recordAction(long novelId, RankAction action, int delta) {
        if (novelId <= 0 || delta == 0) {
            return;
        }
        long increment = (long) weightOf(action) * delta;
        if (increment == 0) {
            return;
        }
        LocalDate today = LocalDate.now(clock);

        try {
            redis.execute(RECORD_ACTION,
                    List.of(zsetKey(RankPeriod.DAILY, today),
                            zsetKey(RankPeriod.WEEKLY, today),
                            zsetKey(RankPeriod.MONTHLY, today)),
                    String.valueOf(increment),
                    String.valueOf(novelId),
                    String.valueOf(DAILY_TTL.toMillis()),
                    String.valueOf(WEEKLY_TTL.toMillis()),
                    String.valueOf(MONTHLY_TTL.toMillis()));
        } catch (Exception e) {
            // 收藏/书评的加分失败绝不能把业务事务带崩：加书架失败了，用户看到的是"加不进去"
            log.warn("行为记账失败，本次不计入榜单 novelId={} action={}：{}", novelId, action, e.getMessage());
        }
    }

    @Override
    public List<ScoredNovel> top(RankPeriod period, LocalDate rankDate, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(zsetKey(period, rankDate), 0, safeLimit - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
                .filter(tuple -> tuple.getValue() != null)
                .map(tuple -> new ScoredNovel(Long.parseLong(tuple.getValue()),
                        Math.round(tuple.getScore() == null ? 0d : tuple.getScore())))
                // ZREVRANGE 已经有序，这里再排一次是为了把"同分时的顺序"钉死：
                // 交给 Redis 的话，同分成员的顺序不保证，结算会得到两个不同但都"正确"的结果
                .sorted(Comparator.comparingLong(ScoredNovel::score).reversed()
                        .thenComparingLong(ScoredNovel::novelId))
                .toList();
    }

    private int weightOf(RankAction action) {
        return switch (action) {
            case FAVORITE -> favoriteWeight;
            case REVIEW -> reviewWeight;
        };
    }
}
