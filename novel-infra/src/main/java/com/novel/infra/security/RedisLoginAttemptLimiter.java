package com.novel.infra.security;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import com.novel.core.user.port.LoginAttemptLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * 基于 Redis 固定窗口计数的登录限流。
 *
 * <p>为什么用 Lua：{@code INCR} 和 {@code EXPIRE} 是两次往返。如果 INCR 之后进程挂了，
 * 这个 key 就永远不过期，该 IP 会被永久锁死 —— 这是个真实发生过的故障模式。
 * Lua 脚本在 Redis 单线程里原子执行，不存在这个窗口。
 *
 * <p>固定窗口的已知缺陷：59 秒时打满 5 次，下一秒窗口重置又能打 5 次，
 * 瞬时能达到 10 次。对这个项目够用；要更严格就换滑动窗口（ZSET 记时间戳）。
 */
@RequiredArgsConstructor
public class RedisLoginAttemptLimiter implements LoginAttemptLimiter {

    private static final String KEY_PREFIX = "auth:login:ip:";

    /** INCR 后若是首次计数则设过期，整体原子。 */
    private static final RedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final int windowSeconds;

    @Override
    public void checkAndRecord(String clientKey) {
        String key = KEY_PREFIX + clientKey;
        Long current = redis.execute(INCR_WITH_TTL, List.of(key), String.valueOf(windowSeconds));

        if (current != null && current > maxAttempts) {
            throw new BizException(ResultCode.RATE_LIMITED,
                    "登录尝试过于频繁，请 " + windowSeconds + " 秒后再试");
        }
    }
}
