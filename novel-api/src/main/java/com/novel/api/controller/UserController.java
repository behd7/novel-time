package com.novel.api.controller;

import com.novel.api.controller.dto.UpdateProfileRequest;
import com.novel.api.security.SecurityUtils;
import com.novel.common.result.Result;
import com.novel.core.user.application.ProfileUpdate;
import com.novel.core.user.application.UserDetailView;
import com.novel.core.user.application.UserService;
import com.novel.core.user.application.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料接口。
 */
@Tag(name = "用户", description = "个人资料与主页")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public Result<UserView> me() {
        return Result.ok(userService.getMe(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "修改当前登录用户资料")
    @PutMapping("/me")
    public Result<UserView> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(SecurityUtils.currentUserId(),
                new ProfileUpdate(request.nickname(), request.avatarKey(), request.bio(),
                        request.gender(), request.birthday())));
    }

    @Operation(summary = "查看他人主页", description = "免登录可访问，返回资料与统计")
    @GetMapping("/{id}")
    public Result<UserDetailView> detail(@PathVariable long id) {
        return Result.ok(userService.getDetail(id));
    }
}
