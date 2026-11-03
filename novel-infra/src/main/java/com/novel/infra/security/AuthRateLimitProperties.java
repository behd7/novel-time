package com.novel.infra.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 认证相关的限流参数。
 *
 * <p>做成配置项而不是常量，最直接的好处是集成测试能把它调高到 100，
 * 否则同一个测试上下文里连跑几次登录就会把 127.0.0.1 打满，
 * 后面所有用例都被自己的限流挡住，排查起来非常浪费时间。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "novel.auth.rate-limit")
public class AuthRateLimitProperties {

    /** 同一 IP 在窗口期内允许的登录尝试次数。 */
    private int loginMaxAttempts = 5;

    /** 计数窗口长度。 */
    private Duration loginWindow = Duration.ofMinutes(1);
}
