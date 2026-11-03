package com.novel.core.user.application;

import com.novel.core.user.domain.UserStat;

/** 用户详情：基本资料 + 统计。个人主页一次取完，省一次往返。 */
public record UserDetailView(UserView user, UserStat stat) {
}
