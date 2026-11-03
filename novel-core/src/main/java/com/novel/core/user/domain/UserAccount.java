package com.novel.core.user.domain;

import java.time.LocalDateTime;

/**
 * 用户认证主体。
 *
 * <p>刻意不叫 {@code User} 也不带任何注解：它是纯业务对象，跟数据库表、JSON 字段都无关。
 * 表结构变了改 infra 的 Entity，接口格式变了改 api 的 DTO，这里不动。
 *
 * @param id           用户 id
 * @param username     登录名，全局唯一
 * @param passwordHash BCrypt 哈希，永不出现明文
 * @param status       1 正常 / 0 封禁
 * @param createdAt    注册时间
 */
public record UserAccount(Long id, String username, String passwordHash, int status, LocalDateTime createdAt) {

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_BANNED = 0;

    public boolean isBanned() {
        return status == STATUS_BANNED;
    }
}
