package com.novel.infra.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 缓存命中率埋点。
 *
 * <p>要守住的是"命中率的分母不能漏"：只统计命中、把未命中漏掉，指标会永远是 100%，
 * 比没有指标更坏——它会让人以为缓存没问题，从而不再去看真实回源量。
 */
@ExtendWith(MockitoExtension.class)
class RedisNovelCacheTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private SimpleMeterRegistry registry;
    private RedisNovelCache cache;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        cache = new RedisNovelCache(redis, new ObjectMapper(), registry);
    }

    private double count(String cacheName, String result) {
        Counter counter = registry.get("novel.cache.requests")
                .tags("cache", cacheName, "result", result)
                .counter();
        return counter == null ? 0d : counter.count();
    }

    @Test
    @DisplayName("读到正文算命中，读不到算未命中")
    void chapterHitAndMiss() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("正文").thenReturn(null);

        assertThat(cache.getChapterText(1L)).isEqualTo("正文");
        assertThat(cache.getChapterText(1L)).isNull();

        assertThat(count("chapter", "hit")).isEqualTo(1d);
        assertThat(count("chapter", "miss")).isEqualTo(1d);
    }

    @Test
    @DisplayName("分类缓存反序列化失败按未命中计，不虚高命中率")
    void categoryBrokenJson_countsAsMiss() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("{不是合法数组}");

        assertThat(cache.getCategories()).isNull();
        assertThat(count("categories", "miss")).isEqualTo(1d);
        assertThat(count("categories", "hit")).isZero();
    }

    @Test
    @DisplayName("分类缓存正常反序列化算命中")
    void categoryHit() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("[]");

        assertThat(cache.getCategories()).isEmpty();
        assertThat(count("categories", "hit")).isEqualTo(1d);
    }
}
