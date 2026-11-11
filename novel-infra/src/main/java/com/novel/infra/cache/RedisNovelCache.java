package com.novel.infra.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.core.novel.domain.Category;
import com.novel.core.novel.port.NovelCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis 缓存实现。
 *
 * <p>正文直接按字符串存（本来就是字符串，没必要再套 JSON）；
 * 分类列表用 JSON 存，因为要还原成对象。
 *
 * <p>反序列化失败时**返回 null 而不是抛异常**：缓存里的数据可能因为改过类结构而变得不可读，
 * 那种情况下正确答案是"当成没缓存"，回源查库并覆盖，而不是让接口 500。
 *
 * <p>命中率按 {@code cache} 维度分标签统计：正文缓存和分类缓存的生命周期完全不同
 * （2 小时 vs 10 分钟），混在一个比率里就分不清是哪一层在失效。
 * 反序列化失败也记一次 miss——从调用方视角它确实没拿到数据，算命中会高估缓存价值。
 */
@Slf4j
@Component
public class RedisNovelCache implements NovelCache {

    private static final String CHAPTER_KEY_PREFIX = "novel:chapter:text:";
    private static final Duration CHAPTER_TTL = Duration.ofHours(2);

    private static final String CATEGORY_KEY = "novel:categories";
    private static final Duration CATEGORY_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private final Counter chapterHit;
    private final Counter chapterMiss;
    private final Counter categoryHit;
    private final Counter categoryMiss;

    public RedisNovelCache(StringRedisTemplate redis, ObjectMapper objectMapper, MeterRegistry registry) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.chapterHit = cacheCounter(registry, "chapter", "hit");
        this.chapterMiss = cacheCounter(registry, "chapter", "miss");
        this.categoryHit = cacheCounter(registry, "categories", "hit");
        this.categoryMiss = cacheCounter(registry, "categories", "miss");
    }

    private static Counter cacheCounter(MeterRegistry registry, String cache, String result) {
        return Counter.builder("novel.cache.requests")
                .description("缓存读取结果计数；命中率 = hit / (hit + miss)")
                .tags("cache", cache, "result", result)
                .register(registry);
    }

    @Override
    public String getChapterText(long chapterId) {
        String text = redis.opsForValue().get(CHAPTER_KEY_PREFIX + chapterId);
        if (text == null) {
            chapterMiss.increment();
        } else {
            chapterHit.increment();
        }
        return text;
    }

    @Override
    public void putChapterText(long chapterId, String text) {
        if (text == null) {
            return;
        }
        redis.opsForValue().set(CHAPTER_KEY_PREFIX + chapterId, text, CHAPTER_TTL);
    }

    @Override
    public void evictChapterText(long chapterId) {
        redis.delete(CHAPTER_KEY_PREFIX + chapterId);
    }

    @Override
    public List<Category> getCategories() {
        String json = redis.opsForValue().get(CATEGORY_KEY);
        if (json == null || json.isBlank()) {
            categoryMiss.increment();
            return null;
        }
        try {
            List<Category> categories = objectMapper.readValue(json, new TypeReference<List<Category>>() {
            });
            categoryHit.increment();
            return categories;
        } catch (Exception e) {
            log.warn("分类缓存反序列化失败，按未命中处理：{}", e.getMessage());
            categoryMiss.increment();
            return null;
        }
    }

    @Override
    public void putCategories(List<Category> categories) {
        try {
            redis.opsForValue().set(CATEGORY_KEY, objectMapper.writeValueAsString(categories), CATEGORY_TTL);
        } catch (Exception e) {
            log.warn("分类缓存写入失败，不影响主流程：{}", e.getMessage());
        }
    }

    @Override
    public void evictCategories() {
        redis.delete(CATEGORY_KEY);
    }
}
