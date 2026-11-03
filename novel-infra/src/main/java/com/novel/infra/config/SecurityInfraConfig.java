package com.novel.infra.config;

import com.novel.core.user.port.LoginAttemptLimiter;
import com.novel.core.user.port.PasswordHasher;
import com.novel.core.user.port.RefreshTokenStore;
import com.novel.core.user.port.TokenPort;
import com.novel.infra.security.AuthRateLimitProperties;
import com.novel.infra.security.BCryptPasswordHasher;
import com.novel.infra.security.JwtProperties;
import com.novel.infra.security.JwtTokenAdapter;
import com.novel.infra.security.RedisLoginAttemptLimiter;
import com.novel.infra.security.RedisTokenStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 认证相关端口的实现装配。
 *
 * <p>四个 port → 四个 adapter，全部在 infra 注册，core 和 api 都不需要知道具体类型。
 * 这也是为什么 {@code AuthService} 的单元测试可以塞假实现：依赖的是接口，不是 bean 名。
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthRateLimitProperties.class})
public class SecurityInfraConfig {

    @Bean
    public PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    public TokenPort tokenPort(JwtProperties properties) {
        return new JwtTokenAdapter(properties);
    }

    @Bean
    public RefreshTokenStore refreshTokenStore(StringRedisTemplate redis) {
        return new RedisTokenStore(redis);
    }

    @Bean
    public LoginAttemptLimiter loginAttemptLimiter(StringRedisTemplate redis,
                                                   AuthRateLimitProperties rateLimit) {
        return new RedisLoginAttemptLimiter(redis,
                rateLimit.getLoginMaxAttempts(),
                (int) rateLimit.getLoginWindow().toSeconds());
    }
}
