package com.novel.api.controller;

import com.novel.api.controller.dto.LoginRequest;
import com.novel.api.controller.dto.RefreshRequest;
import com.novel.api.controller.dto.RegisterRequest;
import com.novel.api.security.SecurityUtils;
import com.novel.common.result.Result;
import com.novel.core.user.application.AuthService;
import com.novel.core.user.application.AuthView;
import com.novel.core.user.application.LoginCommand;
import com.novel.core.user.application.RegisterCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@Tag(name = "认证", description = "注册 / 登录 / 刷新 / 登出")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "注册", description = "注册成功直接下发双令牌，省掉一次登录往返")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AuthView> register(@Valid @RequestBody RegisterRequest request) {
        String nickname = StringUtils.hasText(request.nickname()) ? request.nickname() : request.username();
        return Result.ok(authService.register(
                new RegisterCommand(request.username(), request.password(), nickname)));
    }

    @Operation(summary = "登录", description = "同一 IP 每分钟最多 5 次")
    @PostMapping("/login")
    public Result<AuthView> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return Result.ok(authService.login(
                new LoginCommand(request.username(), request.password()), clientIp(servletRequest)));
    }

    @Operation(summary = "刷新令牌", description = "refreshToken 一次性，用过即作废")
    @PostMapping("/refresh")
    public Result<AuthView> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "登出", description = "吊销 refreshToken，幂等")
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(SecurityUtils.currentUserId(), request.refreshToken());
        return Result.ok();
    }

    /**
     * 取客户端 IP 作为限流维度。
     *
     * <p>刻意**不读** {@code X-Forwarded-For}：没有可信代理时这个头可以被任意伪造，
     * 攻击者每次换个值就能绕过限流。等真正上了 Nginx，再配合
     * {@code server.forward-headers-strategy=framework} 由框架统一处理。
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
