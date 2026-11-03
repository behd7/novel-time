package com.novel.infra.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novel.core.user.domain.UserAccount;
import com.novel.core.user.domain.UserProfile;
import com.novel.core.user.domain.UserStat;
import com.novel.core.user.port.UserRepository;
import com.novel.infra.persistence.entity.UserEntity;
import com.novel.infra.persistence.entity.UserProfileEntity;
import com.novel.infra.persistence.entity.UserStatEntity;
import com.novel.infra.persistence.mapper.UserMapper;
import com.novel.infra.persistence.mapper.UserProfileMapper;
import com.novel.infra.persistence.mapper.UserStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户仓储实现。
 *
 * <p>这一层存在的唯一职责是"翻译"：Entity ↔ Domain。看起来是样板代码，
 * 但它把 MyBatis-Plus 的注解、逻辑删除、字段命名策略全部挡在 infra 里，
 * 所以 core 的单元测试可以完全脱离数据库跑。
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    /** 资料缺失时的兜底昵称，避免读接口因为一条脏数据 500。 */
    private static final String DEFAULT_NICKNAME = "书友";

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserStatMapper userStatMapper;

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        // 逻辑删除条件由 MyBatis-Plus 自动追加 deleted = 0
        UserEntity entity = userMapper.selectOne(
                Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username));
        return Optional.ofNullable(entity).map(this::toAccount);
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toAccount);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.exists(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username));
    }

    @Override
    public long createUser(String username, String passwordHash) {
        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordHash);
        entity.setStatus(UserAccount.STATUS_NORMAL);
        userMapper.insert(entity);
        // 自增主键由 MyBatis 回填到实体上
        return entity.getId();
    }

    @Override
    public void initializeUser(long userId, String nickname) {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setNickname(nickname);
        profile.setGender(0);
        userProfileMapper.insert(profile);

        UserStatEntity stat = new UserStatEntity();
        stat.setUserId(userId);
        userStatMapper.insert(stat);
    }

    @Override
    public UserProfile findProfile(long userId) {
        UserProfileEntity entity = userProfileMapper.selectById(userId);
        if (entity == null) {
            return new UserProfile(userId, DEFAULT_NICKNAME, null, null, 0, null);
        }
        return new UserProfile(entity.getUserId(), entity.getNickname(), entity.getAvatarKey(),
                entity.getBio(), entity.getGender(), entity.getBirthday());
    }

    @Override
    public UserStat findStat(long userId) {
        UserStatEntity entity = userStatMapper.selectById(userId);
        if (entity == null) {
            return UserStat.empty(userId);
        }
        return new UserStat(entity.getUserId(),
                nullSafe(entity.getBookshelfCount()),
                nullSafe(entity.getReviewCount()),
                nullSafe(entity.getPostCount()),
                nullSafe(entity.getLikeReceived()));
    }

    @Override
    public void updateProfile(long userId, UserProfile profile) {
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setNickname(profile.nickname());
        entity.setAvatarKey(profile.avatarKey());
        entity.setBio(profile.bio());
        entity.setGender(profile.gender());
        entity.setBirthday(profile.birthday());
        // updateById 默认只更新非 null 字段，未传的字段保持原值
        userProfileMapper.updateById(entity);
    }

    private UserAccount toAccount(UserEntity entity) {
        return new UserAccount(entity.getId(), entity.getUsername(), entity.getPasswordHash(),
                entity.getStatus() == null ? UserAccount.STATUS_NORMAL : entity.getStatus(),
                entity.getCreatedAt());
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
