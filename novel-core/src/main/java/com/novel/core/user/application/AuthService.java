package com.novel.core.user.application;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import com.novel.core.user.domain.UserAccount;
import com.novel.core.user.domain.UserProfile;
import com.novel.core.user.port.LoginAttemptLimiter;
import com.novel.core.user.port.PasswordHasher;
import com.novel.core.user.port.RefreshTokenStore;
import com.novel.core.user.port.TokenPort;
import com.novel.core.user.port.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 认证应用服务：注册、登录、刷新、登出。
 *
 * <p>几个刻意的取舍：
 * <ul>
 *   <li><b>登录失败不区分原因</b>：用户名不存在和密码错误都返回同一个
 *       {@code PASSWORD_INCORRECT}。区分开等于送给攻击者一个用户名枚举接口。</li>
 *   <li><b>用户名不存在也跑一次 BCrypt</b>：否则"查无此人"会在 1ms 内返回，
 *       而"密码错"要 100ms，响应时间本身就是旁路。</li>
 *   <li><b>refresh 令牌一次性</b>：每次刷新都作废旧 jti，被窃取的 refresh 用第二次就会失败。</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenPort tokenPort;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginAttemptLimiter loginAttemptLimiter;

    /** 用户名不存在时用来凑时间的哑哈希，内容无意义。 */
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       PasswordHasher passwordHasher,
                       TokenPort tokenPort,
                       RefreshTokenStore refreshTokenStore,
                       LoginAttemptLimiter loginAttemptLimiter) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenPort = tokenPort;
        this.refreshTokenStore = refreshTokenStore;
        this.loginAttemptLimiter = loginAttemptLimiter;
        // 启动时算一次，避免每次登录失败都多一次哈希开销
        this.dummyHash = passwordHasher.hash("dummy-password-for-timing");
    }

    @Transactional
    public AuthView register(RegisterCommand command) {
        if (userRepository.existsByUsername(command.username())) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        long userId;
        try {
            userId = userRepository.createUser(command.username(), passwordHasher.hash(command.password()));
        } catch (DuplicateKeyException e) {
            // 两个请求同时通过上面的 exists 检查时，唯一索引是最后一道防线
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        userRepository.initializeUser(userId, command.nickname());
        log.info("用户注册成功 userId={} username={}", userId, command.username());

        UserAccount account = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ResultCode.USER_NOT_FOUND));
        return issue(account);
    }

    public AuthView login(LoginCommand command, String clientKey) {
        loginAttemptLimiter.checkAndRecord(clientKey);

        Optional<UserAccount> found = userRepository.findByUsername(command.username());
        if (found.isEmpty()) {
            // 故意跑一次哈希，让"用户不存在"和"密码错误"的耗时接近
            passwordHasher.matches(command.password(), dummyHash);
            throw new BizException(ResultCode.PASSWORD_INCORRECT);
        }

        UserAccount account = found.get();
        if (!passwordHasher.matches(command.password(), account.passwordHash())) {
            throw new BizException(ResultCode.PASSWORD_INCORRECT);
        }
        if (account.isBanned()) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        log.info("用户登录成功 userId={}", account.id());
        return issue(account);
    }

    /**
     * 刷新令牌。
     *
     * <p>同时做两件事：校验 jti 仍在白名单里，然后立刻作废旧 jti（轮换）。
     * 这样 refresh 令牌就是一次性的，被盗用后原用户下次刷新必然失败，能尽早暴露。
     */
    public AuthView refresh(String refreshToken) {
        TokenPort.Claims claims = tokenPort.parse(refreshToken, TokenPort.Type.REFRESH);
        if (!refreshTokenStore.isValid(claims.jti(), claims.userId())) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        refreshTokenStore.revoke(claims.jti());

        UserAccount account = userRepository.findById(claims.userId())
                .orElseThrow(() -> new BizException(ResultCode.TOKEN_INVALID));
        if (account.isBanned()) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        return issue(account);
    }

    /**
     * 登出：幂等。
     *
     * <p>令牌过期、伪造、别人的 refresh 一律返回成功 —— 登出接口不该成为探测令牌有效性的工具。
     */
    public void logout(long userId, String refreshToken) {
        try {
            TokenPort.Claims claims = tokenPort.parse(refreshToken, TokenPort.Type.REFRESH);
            if (claims.userId() == userId) {
                refreshTokenStore.revoke(claims.jti());
            }
        } catch (BizException e) {
            log.debug("登出时收到无效 refresh 令牌，已忽略");
        }
    }

    private AuthView issue(UserAccount account) {
        TokenPort.Issued access = tokenPort.issue(account.id(), account.username(), TokenPort.Type.ACCESS);
        TokenPort.Issued refresh = tokenPort.issue(account.id(), account.username(), TokenPort.Type.REFRESH);

        Duration refreshTtl = Duration.between(Instant.now(), refresh.expiresAt());
        refreshTokenStore.save(refresh.jti(), account.id(), refreshTtl);

        UserProfile profile = userRepository.findProfile(account.id());
        UserView view = new UserView(account.id(), account.username(),
                profile.nickname(), profile.avatarKey(), profile.bio(),
                profile.gender(), profile.birthday(), account.createdAt());

        long expiresIn = Math.max(0, Duration.between(Instant.now(), access.expiresAt()).toSeconds());
        return new AuthView(access.token(), refresh.token(), expiresIn, view);
    }
}
