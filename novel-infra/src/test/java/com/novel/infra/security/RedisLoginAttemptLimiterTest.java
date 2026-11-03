package com.novel.infra.security;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 登录限流测试。
 *
 * <p>只验证"计数超过阈值就拒绝"这一条核心规则，Lua 的原子性靠 Redis 本身保证，
 * 用 Mock 复现不了也不该复现。
 */
@ExtendWith(MockitoExtension.class)
class RedisLoginAttemptLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    private RedisLoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisLoginAttemptLimiter(redis, 5, 60);
    }

    @SuppressWarnings("unchecked")
    private void stubCounter(long value) {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(value);
    }

    @Test
    @DisplayName("第 5 次尝试仍然放行")
    void withinLimit_allowed() {
        stubCounter(5L);

        assertThatCode(() -> limiter.checkAndRecord("1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("第 6 次尝试被拒绝并返回 42900")
    void overLimit_rejected() {
        stubCounter(6L);

        BizException ex = catchThrowableOfType(
                () -> limiter.checkAndRecord("1.2.3.4"), BizException.class);

        org.assertj.core.api.Assertions.assertThat(ex.getResultCode()).isEqualTo(ResultCode.RATE_LIMITED);
    }

    @Test
    @DisplayName("限流维度按 IP 隔离，key 前缀固定")
    void keyIncludesPrefix() {
        stubCounter(1L);
        limiter.checkAndRecord("10.0.0.1");

        org.mockito.Mockito.verify(redis).execute(any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of("auth:login:ip:10.0.0.1")), any());
    }
}
