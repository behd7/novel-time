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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * {@link AuthService} 单元测试。
 *
 * <p>完全不起 Spring、不连数据库和 Redis —— 这是端口倒置最直接的收益：
 * 五个依赖全是接口，用 Mockito 造出来就能把所有分支跑一遍，整个类 1 秒内跑完。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.now();

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenPort tokenPort;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private LoginAttemptLimiter loginAttemptLimiter;

    private AuthService authService;

    private final UserAccount alice = new UserAccount(1L, "alice", "HASHED", UserAccount.STATUS_NORMAL,
            LocalDateTime.of(2026, 1, 1, 0, 0));
    private final UserProfile aliceProfile = new UserProfile(1L, "爱丽丝", "avatar/a.jpg", "读书", 2, null);

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordHasher, tokenPort,
                refreshTokenStore, loginAttemptLimiter);
    }

    private void stubTokens() {
        // 构造函数会先用 dummy 密码算一次哈希，这里让它返回一个固定值，避免影响后续断言
        lenient().when(passwordHasher.hash(anyString())).thenReturn("HASHED");
        lenient().when(tokenPort.issue(1L, "alice", TokenPort.Type.ACCESS))
                .thenReturn(new TokenPort.Issued("access-token", "jti-a", NOW.plus(Duration.ofMinutes(30))));
        lenient().when(tokenPort.issue(1L, "alice", TokenPort.Type.REFRESH))
                .thenReturn(new TokenPort.Issued("refresh-token", "jti-r", NOW.plus(Duration.ofDays(7))));
    }

    @Test
    @DisplayName("注册成功：建库、初始化附属记录、下发双令牌")
    void register_shouldCreateUserAndIssueTokens() {
        stubTokens();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.createUser("alice", "HASHED")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findProfile(1L)).thenReturn(aliceProfile);

        AuthView view = authService.register(new RegisterCommand("alice", "pwd123456", "爱丽丝"));

        assertThat(view.accessToken()).isEqualTo("access-token");
        assertThat(view.refreshToken()).isEqualTo("refresh-token");
        assertThat(view.expiresIn()).isPositive();
        assertThat(view.user().nickname()).isEqualTo("爱丽丝");

        verify(userRepository).initializeUser(1L, "爱丽丝");
        // refresh 的 jti 必须进白名单，否则刷新时会被判无效
        verify(refreshTokenStore).save(eq("jti-r"), eq(1L), any(Duration.class));
    }

    @Test
    @DisplayName("注册重名：应用层先拦一道")
    void register_duplicateUsername_shouldFail() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        BizException ex = catchThrowableOfType(
                () -> authService.register(new RegisterCommand("alice", "pwd123456", "爱丽丝")),
                BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.USERNAME_EXISTS);
        verify(userRepository, never()).createUser(anyString(), anyString());
    }

    @Test
    @DisplayName("注册并发重名：唯一索引兜底，仍然转成同一个业务错误")
    void register_raceOnUniqueIndex_shouldStillReportUsernameExists() {
        stubTokens();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.createUser("alice", "HASHED")).thenThrow(new DuplicateKeyException("uk_username"));

        BizException ex = catchThrowableOfType(
                () -> authService.register(new RegisterCommand("alice", "pwd123456", "爱丽丝")),
                BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.USERNAME_EXISTS);
        verify(userRepository, never()).initializeUser(anyLong(), anyString());
    }

    @Test
    @DisplayName("登录成功：不泄露密码哈希，直接下发令牌")
    void login_withCorrectPassword_shouldIssueTokens() {
        stubTokens();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(passwordHasher.matches("pwd123456", "HASHED")).thenReturn(true);
        when(userRepository.findProfile(1L)).thenReturn(aliceProfile);

        AuthView view = authService.login(new LoginCommand("alice", "pwd123456"), "1.2.3.4");

        assertThat(view.accessToken()).isEqualTo("access-token");
        verify(loginAttemptLimiter).checkAndRecord("1.2.3.4");
    }

    @Test
    @DisplayName("密码错误：返回 PASSWORD_INCORRECT")
    void login_withWrongPassword_shouldFail() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(passwordHasher.matches("wrong", "HASHED")).thenReturn(false);

        BizException ex = catchThrowableOfType(
                () -> authService.login(new LoginCommand("alice", "wrong"), "1.2.3.4"),
                BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.PASSWORD_INCORRECT);
    }

    @Test
    @DisplayName("用户不存在：错误码与密码错误完全一致，防止用户名枚举")
    void login_withUnknownUser_shouldReturnSameCodeAndStillHash() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        BizException ex = catchThrowableOfType(
                () -> authService.login(new LoginCommand("ghost", "pwd123456"), "1.2.3.4"),
                BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.PASSWORD_INCORRECT);
        // 关键断言：即使查无此人，也要跑一次哈希来抹平响应时间差
        verify(passwordHasher).matches(eq("pwd123456"), any());
    }

    @Test
    @DisplayName("封禁用户：密码正确也不发令牌")
    void login_withBannedUser_shouldFail() {
        UserAccount banned = new UserAccount(1L, "alice", "HASHED", UserAccount.STATUS_BANNED,
                LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(banned));
        when(passwordHasher.matches("pwd123456", "HASHED")).thenReturn(true);

        BizException ex = catchThrowableOfType(
                () -> authService.login(new LoginCommand("alice", "pwd123456"), "1.2.3.4"),
                BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.ACCOUNT_DISABLED);
        verify(tokenPort, never()).issue(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("刷新：白名单里没有的 jti 一律拒绝（模拟已登出/已轮换）")
    void refresh_withRevokedToken_shouldFail() {
        when(tokenPort.parse("old-refresh", TokenPort.Type.REFRESH))
                .thenReturn(new TokenPort.Claims(1L, "alice", "jti-old", NOW.plusSeconds(3600)));
        when(refreshTokenStore.isValid("jti-old", 1L)).thenReturn(false);

        BizException ex = catchThrowableOfType(
                () -> authService.refresh("old-refresh"), BizException.class);

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.TOKEN_INVALID);
        verify(refreshTokenStore, never()).revoke(anyString());
    }

    @Test
    @DisplayName("刷新成功：旧 jti 立即作废，实现令牌轮换")
    void refresh_shouldRotateOldToken() {
        stubTokens();
        when(tokenPort.parse("old-refresh", TokenPort.Type.REFRESH))
                .thenReturn(new TokenPort.Claims(1L, "alice", "jti-old", NOW.plusSeconds(3600)));
        when(refreshTokenStore.isValid("jti-old", 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findProfile(1L)).thenReturn(aliceProfile);

        AuthView view = authService.refresh("old-refresh");

        assertThat(view.accessToken()).isEqualTo("access-token");
        verify(refreshTokenStore).revoke("jti-old");
    }

    @Test
    @DisplayName("登出：令牌无效也要静默成功，不能变成探测接口")
    void logout_withInvalidToken_shouldNotThrow() {
        when(tokenPort.parse("garbage", TokenPort.Type.REFRESH))
                .thenThrow(new BizException(ResultCode.TOKEN_INVALID));

        authService.logout(1L, "garbage");

        verify(refreshTokenStore, never()).revoke(anyString());
    }
}
