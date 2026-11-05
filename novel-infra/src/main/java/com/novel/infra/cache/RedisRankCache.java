package com.novel.infra.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.core.rank.application.view.RankItemView;
import com.novel.core.rank.domain.RankPeriod;
import com.novel.core.rank.port.RankCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 榜单读取缓存。
 *
 * <p>key 里带周期和日期（{@code rank:cache:daily:20261105}），所以跨天自然失配——
 * 这是它不需要任何失效逻辑的原因。绝大多数缓存事故都是 key 里少了一个维度，
 * 于是"昨天的榜"会在零点之后继续服务一整天。
 *
 * <p>刷新时主动删 key，不靠 TTL 等它过期：结算是每 10 分钟一次，TTL 是 5 分钟，
 * 纯靠 TTL 也能对，但那意味着"刷新完还要再等最多 5 分钟"，调参时说不清到底哪一层慢。
 *
 * <p>反序列化失败按未命中处理并回源：缓存里的结构可能因为改过类而变得不可读，
 * 那种情况下正确答案是重查一次，而不是让榜单页 500。
 */
@Slf4j
public class RedisRankCache implements RankCache {

    private static final String KEY_PREFIX = "rank:cache:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisRankCache(StringRedisTemplate redis, ObjectMapper objectMapper, Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    static String cacheKey(RankPeriod period, LocalDate day) {
        return KEY_PREFIX + period.name().toLowerCase(Locale.ROOT) + ":" + period.bucket(day);
    }

    @Override
    public List<RankItemView> get(RankPeriod period, LocalDate rankDate) {
        String json = redis.opsForValue().get(cacheKey(period, rankDate));
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RankItemView>>() {
            });
        } catch (Exception e) {
            log.warn("榜单缓存反序列化失败，按未命中处理：{}", e.getMessage());
            return null;
        }
    }

    @Override
    public void put(RankPeriod period, LocalDate rankDate, List<RankItemView> items) {
        if (ttl.isZero() || ttl.isNegative()) {
            // 配成 0 表示"不要缓存"，用于排查"到底是缓存还是库返回的旧数据"
            return;
        }
        try {
            redis.opsForValue().set(cacheKey(period, rankDate),
                    objectMapper.writeValueAsString(items), ttl);
        } catch (Exception e) {
            log.warn("榜单缓存写入失败，不影响主流程：{}", e.getMessage());
        }
    }

    @Override
    public void evict(RankPeriod period, LocalDate rankDate) {
        redis.delete(cacheKey(period, rankDate));
    }
}
