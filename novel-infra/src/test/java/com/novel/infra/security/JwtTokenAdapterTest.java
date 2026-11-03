package com.novel.infra.security;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import com.novel.core.user.port.TokenPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * JWT 实现测试。
 *
 * <p>重点验证两件容易被忽略的事：
 * <ol>
 *   <li>access 和 refresh 不能混用 —— 否则 7 天有效期的 refresh 会被当成访问令牌，
 *       短令牌设计直接失效；</li>
 *   <li>过期令牌必须被拒绝，而且要转换成业务错误码而不是抛 {@code ExpiredJwtException} 冒到 500。</li>
 * </ol>
 */
class JwtTokenAdapterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private JwtProperties properties;
    private JwtTokenAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessTtl(Duration.ofMinutes(30));
        properties.setRefreshTtl(Duration.ofDays(7));
        properties.setIssuer("novel");
        adapter = new JwtTokenAdapter(properties);
    }

    @Test
    @DisplayName("签发的 access 令牌可以被解析出 userId / username / jti")
    void issueAndParse_accessToken() {
        TokenPort.Issued issued = adapter.issue(42L, "alice", TokenPort.Type.ACCESS);

        TokenPort.Claims claims = adapter.parse(issued.token(), TokenPort.Type.ACCESS);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.username()).isEqualTo("alice");
        assertThat(claims.jti()).isEqualTo(issued.jti());
        assertThat(claims.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    @DisplayName("access 令牌不能当 refresh 用")
    void accessToken_cannotBeUsedAsRefresh() {
        String access = adapter.issue(42L, "alice", TokenPort.Type.ACCESS).token();

        BizException ex = catchThrowableOfType(
                () -> adapter.parse(access, TokenPort.Type.REFRESH), BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("refresh 令牌不能当 access 用")
    void refreshToken_cannotBeUsedAsAccess() {
        String refresh = adapter.issue(42L, "alice", TokenPort.Type.REFRESH).token();

        BizException ex = catchThrowableOfType(
                () -> adapter.parse(refresh, TokenPort.Type.ACCESS), BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("乱码 / 被篡改的令牌一律拒绝")
    void garbageToken_isRejected() {
        BizException ex = catchThrowableOfType(
                () -> adapter.parse("not-a-jwt", TokenPort.Type.ACCESS), BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("换一把密钥签的令牌，验签必须失败")
    void tokenSignedByAnotherSecret_isRejected() {
        JwtProperties other = new JwtProperties();
        other.setSecret("ffffffffffffffffffffffffffffffff");
        other.setIssuer("novel");
        String forged = new JwtTokenAdapter(other).issue(42L, "alice", TokenPort.Type.ACCESS).token();

        BizException ex = catchThrowableOfType(
                () -> adapter.parse(forged, TokenPort.Type.ACCESS), BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("过期令牌要转成 TOKEN_INVALID，而不是让 ExpiredJwtException 冒到 500")
    void expiredToken_isRejectedAsBizError() {
        properties.setAccessTtl(Duration.ofSeconds(-10));
        String expired = adapter.issue(42L, "alice", TokenPort.Type.ACCESS).token();

        BizException ex = catchThrowableOfType(
                () -> adapter.parse(expired, TokenPort.Type.ACCESS), BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("密钥为空或过短时启动即失败")
    void weakSecret_shouldFailFast() {
        JwtProperties weak = new JwtProperties();
        weak.setSecret("short");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, weak::validate);
    }
}
