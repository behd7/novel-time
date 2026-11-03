package com.novel.infra.cache;

import com.novel.core.novel.port.NovelViewCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阅读数累加器（Redis Hash）。
 *
 * <p>所有小说共用一个大 Hash {@code novel:view:delta}，field 是 novelId，value 是尚未落库的增量。
 * 用一个大 Hash 而不是每本书一个 key，是为了让"取出全部增量"变成一次 {@code HGETALL}，
 * 不必去 {@code SCAN} 键空间——生产环境 {@code KEYS}/{@code SCAN} 会阻塞 Redis 主线程。
 *
 * <p>{@link #drain} 用 Lua 一次完成"读出 + 删除"：如果分成两次命令，
 * 两步之间新来的阅读会被一起删掉，白白丢掉计数。丢阅读数不算严重事故，但能避免就该避免。
 */
@Component
@RequiredArgsConstructor
public class RedisNovelViewCounter implements NovelViewCounter {

    private static final String DELTA_KEY = "novel:view:delta";

    private static final RedisScript<List> DRAIN_SCRIPT = new DefaultRedisScript<>("""
            local all = redis.call('HGETALL', KEYS[1])
            if #all > 0 then
                redis.call('DEL', KEYS[1])
            end
            return all
            """, List.class);

    private final StringRedisTemplate redis;

    @Override
    public void increment(long novelId) {
        redis.opsForHash().increment(DELTA_KEY, String.valueOf(novelId), 1);
    }

    @Override
    public long pendingDelta(long novelId) {
        Object value = redis.opsForHash().get(DELTA_KEY, String.valueOf(novelId));
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 取走全部增量并清空。
     *
     * @return novelId → 待累加的阅读数
     */
    @SuppressWarnings("unchecked")
    public Map<Long, Long> drain() {
        List<String> flat = (List<String>) redis.execute(DRAIN_SCRIPT, List.of(DELTA_KEY));
        Map<Long, Long> result = new LinkedHashMap<>();
        if (flat == null || flat.isEmpty()) {
            return result;
        }
        // Lua 返回的是 HGETALL 的扁平数组：[field1, value1, field2, value2, ...]
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            try {
                result.put(Long.parseLong(flat.get(i)), Long.parseLong(flat.get(i + 1)));
            } catch (NumberFormatException ignored) {
                // 脏数据直接跳过，不要因为这个把整批刷新搞挂
            }
        }
        return result;
    }
}
