package com.novel.api.security;

/**
 * 当前登录用户，放进 {@code SecurityContext} 的 principal。
 *
 * <p>只放 id 和 username：权限判断以后按角色从库里查，不往令牌里塞。
 * 令牌里的东西用户看得见，也改不了（有签名），但一旦把权限写进去，
 * 改权限就得等令牌过期才生效 —— 这是很多系统的隐性坑。
 */
public record LoginUser(long userId, String username) {
}
