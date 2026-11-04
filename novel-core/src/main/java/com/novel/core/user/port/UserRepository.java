package com.novel.core.user.port;

import com.novel.core.user.domain.UserAccount;
import com.novel.core.user.domain.UserProfile;
import com.novel.core.user.domain.UserStat;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储端口。
 *
 * <p>core 只描述"我需要什么能力"，MyBatis-Plus 的 Mapper、实体类、SQL 全在 infra。
 * 这样做的直接好处：{@code AuthService} 的单元测试不需要起 Spring、不需要连库，
 * 给一个假的 UserRepository 就能验证注册/登录的全部分支。
 */
public interface UserRepository {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findById(long id);

    boolean existsByUsername(String username);

    /**
     * 只插入 {@code user} 表，返回自增主键。
     *
     * <p>用户名唯一性最终由数据库唯一索引保证：应用层的 {@code existsByUsername}
     * 只用于提前给出友好提示，真正的并发冲突要靠捕获唯一键异常来兜底。
     */
    long createUser(String username, String passwordHash);

    /** 初始化 {@code user_profile} 与 {@code user_stat} 两条附属记录。 */
    void initializeUser(long userId, String nickname);

    UserProfile findProfile(long userId);

    /** 批量取资料，书评列表要显示作者昵称，逐条查就是 N+1。 */
    List<UserProfile> findProfiles(java.util.Collection<Long> userIds);

    /** 书评数统计，个人主页要显示。 */
    void addReviewCount(long userId, int delta);

    /** 书架收藏数统计。 */
    void addBookshelfCount(long userId, int delta);

    /** 收到的赞。 */
    void addLikeReceived(long userId, int delta);

    UserStat findStat(long userId);

    /** 只更新非空字段，避免把没传的字段覆盖成 null。 */
    void updateProfile(long userId, UserProfile profile);
}
