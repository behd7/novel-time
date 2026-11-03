package com.novel.core.user.port;

import java.time.Duration;

/**
 * refresh 令牌白名单。
 *
 * <p>为什么一定要存：纯无状态 JWT 无法吊销。用户点"退出登录"、账号被盗、管理员封号，
 * 只要令牌没过期就还能用 —— 这是 JWT 最常被诟病的点。
 * 把 refresh 的 jti 记在 Redis 里，就有了"服务端可撤销"的能力：
 * access 短命（30 分钟）扛着性能，refresh 长命但可吊销，两者配合才安全。
 */
public interface RefreshTokenStore {

    void save(String jti, long userId, Duration ttl);

    boolean isValid(String jti, long userId);

    void revoke(String jti);
}
