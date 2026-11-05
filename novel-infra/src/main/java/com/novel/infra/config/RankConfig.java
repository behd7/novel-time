package com.novel.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.core.rank.application.HotScoreCalculator;
import com.novel.core.rank.application.RankSettings;
import com.novel.core.rank.port.RankCache;
import com.novel.core.rank.port.RankScoreStore;
import com.novel.infra.cache.RedisRankCache;
import com.novel.infra.cache.RedisRankScoreStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 榜单装配。
 *
 * <p>权重、衰减参数、快照条数都从 {@link RankProperties} 一处读出来，再分给实时层和计算器。
 * 这是"日榜和总榜用同一个刻度"的唯一保证。
 */
@Configuration
@EnableConfigurationProperties(RankProperties.class)
public class RankConfig {

    /**
     * 全项目共用一个时钟（{@code Asia/Shanghai}）。
     *
     * <p>不用 {@code LocalDate.now()} 而是注入 {@code Clock}，是为了让"今天是哪天"变成可替换的东西：
     * 单测能钉死一个时间点，就不会出现"23:59:59 跑测试，日榜算到了第二天"这种偶发失败。
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }

    @Bean
    public RankScoreStore rankScoreStore(StringRedisTemplate redis, RankProperties properties, Clock clock) {
        return new RedisRankScoreStore(redis, properties.getViewWeight(),
                properties.getFavoriteWeight(), properties.getReviewWeight(), clock);
    }

    @Bean
    public RankCache rankCache(StringRedisTemplate redis, ObjectMapper objectMapper, RankProperties properties) {
        return new RedisRankCache(redis, objectMapper, properties.getCacheTtl());
    }

    @Bean
    public HotScoreCalculator hotScoreCalculator(RankProperties properties) {
        return new HotScoreCalculator(properties.getViewWeight(), properties.getFavoriteWeight(),
                properties.getReviewWeight(), properties.getDecayExponent(), properties.getDecayOffsetDays());
    }

    @Bean
    public RankSettings rankSettings(RankProperties properties) {
        return new RankSettings(properties.getSnapshotSize());
    }
}
