package com.novel.core.user.application;

import java.time.LocalDate;

/**
 * 资料更新命令。
 *
 * <p>字段全部可空，含义是"不传就不改"：只改昵称时不该把个性签名清空。
 * 这个语义由 infra 的 {@code updateById}（只更新非空字段）落实。
 */
public record ProfileUpdate(String nickname, String avatarKey, String bio, Integer gender, LocalDate birthday) {
}
