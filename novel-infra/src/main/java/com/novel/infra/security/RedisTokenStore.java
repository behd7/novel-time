package com.novel.infra.security;

import com.novel.core.user.port.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * refresh 令牌白名单，存 Redis。
 *
 * <p>键设计：{@code auth:refresh:{jti}} → {@code userId}，TTL 与令牌有效期一致。
 * 用 jti 而不是 userId 做键，是因为同一用户可以多端登录，
 * 用 userId 会导致"手机上登录把电脑端挤下线"；用 jti 才能做到逐条吊销。
 *
 * <p>值里存了 userId：校验时同时比对，防止有人拿别人 jti 拼自己的令牌（虽然签名已经挡住了，
 * 这属于纵深防御）。
 */
@RequiredArgsConstructor
public class RedisTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;

    @Override
    public void save(String jti, long userId, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + jti, String.valueOf(userId), ttl);
    }

    @Override
    public boolean isValid(String jti, long userId) {
        String stored = redis.opsForValue().get(KEY_PREFIX + jti);
        return stored != null && stored.equals(String.valueOf(userId));
    }

    @Override
    public void revoke(String jti) {
        redis.delete(KEY_PREFIX + jti);
    }
}
