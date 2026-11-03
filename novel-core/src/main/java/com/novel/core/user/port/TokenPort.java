package com.novel.core.user.port;

import java.time.Instant;

/**
 * 令牌端口。
 *
 * <p>core 不知道令牌是 JWT 还是随机串、是 HS256 还是 RS256，也不该知道。
 * 换签名算法、换密钥轮换策略，只改 infra 的 {@code JwtTokenAdapter}。
 */
public interface TokenPort {

    /** 令牌类型。access 用于访问，refresh 只能拿来换新令牌，两者不可混用。 */
    enum Type {
        ACCESS, REFRESH
    }

    /**
     * 已签发的令牌。
     *
     * @param token     字符串本身
     * @param jti       令牌唯一 id，refresh 令牌靠它进 Redis 白名单做吊销
     * @param expiresAt 过期时间，调用方据此算 expiresIn 下发前端
     */
    record Issued(String token, String jti, Instant expiresAt) {
    }

    /** 解析后的声明。 */
    record Claims(long userId, String username, String jti, Instant expiresAt) {
    }

    Issued issue(long userId, String username, Type type);

    /**
     * 解析并校验令牌。
     *
     * @throws com.novel.common.exception.BizException 签名不对、过期、类型不符时抛 TOKEN_INVALID
     */
    Claims parse(String token, Type expectedType);
}
