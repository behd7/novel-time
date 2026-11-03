package com.novel.core.user.application;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import com.novel.core.user.domain.UserAccount;
import com.novel.core.user.domain.UserProfile;
import com.novel.core.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户信息应用服务：查自己、改自己、查别人。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserView getMe(long userId) {
        return toView(requireUser(userId));
    }

    public UserDetailView getDetail(long userId) {
        UserAccount account = requireUser(userId);
        return new UserDetailView(toView(account), userRepository.findStat(userId));
    }

    @Transactional
    public UserView updateProfile(long userId, ProfileUpdate update) {
        requireUser(userId);
        userRepository.updateProfile(userId,
                new UserProfile(userId, update.nickname(), update.avatarKey(),
                        update.bio(), update.gender(), update.birthday()));
        return toView(requireUser(userId));
    }

    private UserAccount requireUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ResultCode.USER_NOT_FOUND));
    }

    private UserView toView(UserAccount account) {
        UserProfile profile = userRepository.findProfile(account.id());
        return new UserView(account.id(), account.username(),
                profile.nickname(), profile.avatarKey(), profile.bio(),
                profile.gender(), profile.birthday(), account.createdAt());
    }
}
