package com.novel.core.user.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对外展示的用户视图。
 *
 * <p>注意这里**没有** passwordHash 字段 —— 用专门的视图对象而不是直接返回实体，
 * 是防止密码哈希、手机号这类字段某天被顺手序列化出去的最简单办法。
 */
public record UserView(long id, String username, String nickname, String avatarKey, String bio,
                       Integer gender, LocalDate birthday, LocalDateTime createdAt) {
}
