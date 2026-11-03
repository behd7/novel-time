package com.novel.core.user.domain;

import java.time.LocalDate;

/**
 * 用户展示信息。
 *
 * @param userId    所属用户
 * @param nickname  昵称，可与登录名不同
 * @param avatarKey MinIO 对象键（不是完整 URL，换域名时不用改库）
 * @param bio       个性签名
 * @param gender    0 未知 / 1 男 / 2 女
 * @param birthday  生日
 */
public record UserProfile(Long userId, String nickname, String avatarKey, String bio,
                          Integer gender, LocalDate birthday) {
}
