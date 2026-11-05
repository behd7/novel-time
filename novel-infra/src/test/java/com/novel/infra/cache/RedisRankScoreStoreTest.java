package com.novel.infra.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.core.rank.application.view.RankItemView;
import com.novel.core.rank.domain.RankAction;
import com.novel.core.rank.domain.RankPeriod;
import com.novel.core.rank.domain.ScoredNovel;
import com.novel.core.novel.application.view.NovelSummaryView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实时层的真实 Redis 行为。
 *
 * <p>这里必须用容器，不能用 Mock：要验的核心是 <b>Lua 脚本的原子去重</b>——
 * "同一个人同一天同一本书只加一次"靠 {@code HSETNX} 的返回值做条件，
 * Mock 只能验"调了哪个脚本"，验不出"第二次真的没加"。
 *
 * <p>时钟固定住，否则跨零点跑测试会莫名其妙地变成两个桶。
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisRankScoreStoreTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final Clock CLOCK =
            Clock.fixed(LocalDateTime.of(2026, 11, 5, 12, 0).atZone(ZONE).toInstant(), ZONE);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static StringRedisTemplate redis;

    private RedisRankScoreStore store;

    @BeforeAll
    static void connect() {
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @BeforeEach
    void setUp() {
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushAll();
        }
        store = new RedisRankScoreStore(redis, 1, 3, 2, CLOCK);
    }

    @Test
    @DisplayName("同一个人同一天同一本书，反复打开也只计一次")
    void recordView_deduplicatedPerViewer() {
        store.recordView("u:9", 1L);
        store.recordView("u:9", 1L);
        store.recordView("u:9", 1L);

        assertThat(store.top(RankPeriod.DAILY, TODAY, 10))
                .extracting(ScoredNovel::novelId, ScoredNovel::score)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 1L));
    }

    @Test
    @DisplayName("不同的人看同一本书，各计一次")
    void recordView_countsEachViewer() {
        store.recordView("u:1", 1L);
        store.recordView("u:2", 1L);
        store.recordView("ip:127.0.0.1", 1L);

        assertThat(store.top(RankPeriod.DAILY, TODAY, 10))
                .extracting(ScoredNovel::score)
                .containsExactly(3L);
    }

    @Test
    @DisplayName("一次阅读同时落进日/周/月三个桶")
    void recordView_writesAllPeriods() {
        store.recordView("u:9", 1L);

        assertThat(store.top(RankPeriod.DAILY, TODAY, 10)).hasSize(1);
        assertThat(store.top(RankPeriod.WEEKLY, TODAY, 10)).hasSize(1);
        assertThat(store.top(RankPeriod.MONTHLY, TODAY, 10)).hasSize(1);
    }

    @Test
    @DisplayName("收藏按 3 分、书评按 2 分累加")
    void recordAction_appliesWeights() {
        store.recordAction(1L, RankAction.FAVORITE, 1);
        store.recordAction(1L, RankAction.REVIEW, 1);

        assertThat(store.top(RankPeriod.DAILY, TODAY, 10))
                .extracting(ScoredNovel::score)
                .containsExactly(5L);
    }

    @Test
    @DisplayName("取消收藏扣回去：只加不减的话，收藏/取消就是一个免费刷分循环")
    void recordAction_negativeDeducts() {
        store.recordAction(1L, RankAction.FAVORITE, 1);
        store.recordAction(1L, RankAction.FAVORITE, -1);

        assertThat(store.top(RankPeriod.DAILY, TODAY, 10).get(0).score()).isZero();
    }

    @Test
    @DisplayName("取榜按行为量降序，超过上限就截断")
    void top_ordersAndLimits() {
        store.recordAction(1L, RankAction.FAVORITE, 1);   // 3
        store.recordAction(2L, RankAction.FAVORITE, 1);   // 3
        store.recordAction(2L, RankAction.REVIEW, 1);     // 5
        store.recordAction(3L, RankAction.REVIEW, 1);     // 2

        assertThat(store.top(RankPeriod.DAILY, TODAY, 2))
                .extracting(ScoredNovel::novelId)
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("ZSET 都带过期时间，否则键空间只增不减")
    void zset_hasTtl() {
        store.recordView("u:9", 1L);

        Long ttl = redis.getExpire(RedisRankScoreStore.zsetKey(RankPeriod.DAILY, TODAY));
        assertThat(ttl).isNotNull().isPositive();
    }

    @Test
    @DisplayName("缓存写入后能读回来，删除后读不到")
    void cache_roundTrip() {
        RedisRankCache cache = new RedisRankCache(redis, new ObjectMapper().findAndRegisterModules(),
                Duration.ofMinutes(5));
        RankItemView item = new RankItemView(1, 2.5, new NovelSummaryView(
                1L, "诛仙", "萧鼎", null, 1L, 0, "简介", 1000, 10,
                new BigDecimal("4.62"), 120, 100, 5, LocalDateTime.of(2026, 11, 4, 12, 0)));

        cache.put(RankPeriod.DAILY, TODAY, List.of(item));

        assertThat(cache.get(RankPeriod.DAILY, TODAY)).hasSize(1);
        assertThat(cache.get(RankPeriod.DAILY, TODAY).get(0).novel().title()).isEqualTo("诛仙");
        assertThat(cache.get(RankPeriod.DAILY, TODAY).get(0).novel().latestUpdateAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 4, 12, 0));

        cache.evict(RankPeriod.DAILY, TODAY);
        assertThat(cache.get(RankPeriod.DAILY, TODAY)).isNull();
    }

    @Test
    @DisplayName("缓存里的脏数据按未命中处理，不让接口 500")
    void cache_corruptedValue_treatedAsMiss() {
        RedisRankCache cache = new RedisRankCache(redis, new ObjectMapper().findAndRegisterModules(),
                Duration.ofMinutes(5));
        redis.opsForValue().set(RedisRankCache.cacheKey(RankPeriod.DAILY, TODAY), "{not-json");

        assertThat(cache.get(RankPeriod.DAILY, TODAY)).isNull();
    }
}
