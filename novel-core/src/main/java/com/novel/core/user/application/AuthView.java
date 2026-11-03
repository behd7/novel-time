package com.novel.core.user.application;

/**
 * 认证结果：双令牌 + 用户信息。
 *
 * @param expiresIn access 令牌剩余有效秒数，前端据此决定何时静默刷新
 */
public record AuthView(String accessToken, String refreshToken, long expiresIn, UserView user) {
}
