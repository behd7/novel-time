package com.novel.infra.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT 配置。
 *
 * <p>密钥**不允许**出现在 {@code application.yml} 里 —— 那个文件是要提交到仓库的。
 * 走 {@code NOVEL_JWT_SECRET} 环境变量或 {@code application-local.yml}。
 *
 * <p>启动时做长度校验并直接抛异常：宁可起不来，也不要带着弱密钥跑进生产。
 * HS256 要求密钥至少 256 bit（32 字节），短于此 jjwt 会在签名时抛
 * {@code WeakKeyException}，不如提前给出人话提示。
 *
 * <p>算法不是写死的：jjwt 按密钥长度自动选择 —— 32 字节 → HS256、48 → HS384、64 → HS512。
 * 用 {@code openssl rand -base64 48} 生成的是 64 字节，实际签出来就是 HS512，
 * 所以真实令牌的 header 里会看到 {@code "alg":"HS512"}，这是正常的。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "novel.jwt")
public class JwtProperties {

    private static final int MIN_SECRET_BYTES = 32;

    /** HMAC 密钥，至少 32 字节。 */
    private String secret;

    /** access 令牌有效期，短命是为了降低泄露后的窗口。 */
    private Duration accessTtl = Duration.ofMinutes(30);

    /** refresh 令牌有效期，配合 Redis 白名单可吊销。 */
    private Duration refreshTtl = Duration.ofDays(7);

    /** 签发者，解析时会校验，防止别处签的同算法令牌混进来。 */
    private String issuer = "novel";

    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("""
                    novel.jwt.secret 未配置。
                    请在 application-local.yml 里设置 novel.jwt.secret，
                    或设置环境变量 NOVEL_JWT_SECRET（至少 32 字节）。
                    生成方式：openssl rand -base64 48""");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "novel.jwt.secret 长度不足 " + MIN_SECRET_BYTES + " 字节，HS256 拒绝弱密钥");
        }
    }
}
