package com.novel.infra.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.core.novel.domain.Category;
import com.novel.core.novel.port.NovelCache;
import lombok.RequiredArgsConstructor;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisNovelCache implements NovelCache {

    private static final String CHAPTER_KEY_PREFIX = "novel:chapter:text:";
    private static final Duration CHAPTER_TTL = Duration.ofHours(2);

    private static final String CATEGORY_KEY = "novel:categories";
    private static final Duration CATEGORY_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public String getChapterText(long chapterId) {
        return redis.opsForValue().get(CHAPTER_KEY_PREFIX + chapterId);
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
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Category>>() {
            });
        } catch (Exception e) {
            log.warn("分类缓存反序列化失败，按未命中处理：{}", e.getMessage());
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
