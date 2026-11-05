package com.novel.core.support;

import com.novel.core.user.domain.UserProfile;
import com.novel.core.user.port.UserRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 批量加载用户资料。
 *
 * <p>书评与社区列表都要显示作者昵称，逐条查就是 N+1；两处各写一遍迟早会有一处忘了批量。
 */
public final class ProfileLoader {

    private ProfileLoader() {
    }

    public static Map<Long, UserProfile> load(UserRepository userRepository, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserProfile> profiles = new HashMap<>();
        userRepository.findProfiles(userIds).forEach(profile -> profiles.put(profile.userId(), profile));
        return profiles;
    }
}
