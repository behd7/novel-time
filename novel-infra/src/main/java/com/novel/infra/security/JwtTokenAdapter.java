package com.novel.infra.security;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import com.novel.core.user.port.TokenPort;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JJWT 实现。
 *
 * <p>令牌里放什么、不放什么是有讲究的：
 * <ul>
 *   <li>放 {@code sub=userId}、{@code uname}、{@code jti}、{@code typ} —— 都是非敏感且必需的信息</li>
 *   <li><b>不放</b>手机号、邮箱、密码哈希、权限列表 —— JWT 只是 base64，任何人拿到都能解开看</li>
 * </ul>
 *
 * <p>{@code typ} 声明是刻意加的：access 和 refresh 用同一把密钥签发，
 * 如果不区分类型，用户就能拿 refresh 令牌直接调业务接口（它有效期 7 天，
 * 等于把短命设计废掉）。
 */
public class JwtTokenAdapter implements TokenPort {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_USERNAME = "uname";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtTokenAdapter(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Issued issue(long userId, String username, Type type) {
        Instant now = Instant.now();
        Duration ttl = type == Type.ACCESS ? properties.getAccessTtl() : properties.getRefreshTtl();
        Instant expiresAt = now.plus(ttl);
        String jti = UUID.randomUUID().toString().replace("-", "");

        String token = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(Long.toString(userId))
                .id(jti)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TYPE, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();

        return new Issued(token, jti, expiresAt);
    }

    @Override
    public Claims parse(String token, Type expectedType) {
        if (token == null || token.isBlank()) {
            throw invalid();
        }
        try {
            io.jsonwebtoken.Claims jwt = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!expectedType.name().equals(jwt.get(CLAIM_TYPE, String.class))) {
                // 拿 refresh 当 access 用，直接拒绝
                throw invalid();
            }

            return new Claims(
                    Long.parseLong(jwt.getSubject()),
                    jwt.get(CLAIM_USERNAME, String.class),
                    jwt.getId(),
                    jwt.getExpiration().toInstant());
        } catch (ExpiredJwtException e) {
            throw new BizException(ResultCode.TOKEN_INVALID, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    private BizException invalid() {
        return new BizException(ResultCode.TOKEN_INVALID);
    }
}
