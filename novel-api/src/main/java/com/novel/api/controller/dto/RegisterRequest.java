package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * <p>密码复杂度用正则卡在 8-64 位、字母+数字：太弱的规则在真实场景里等于没注册门槛，
 * 太强的规则（必须大小写+符号+定期更换）会把用户赶走，而且会诱导出
 * {@code Password1!} 这种规律性极强的"合规密码"。
 *
 * <p>上限 64 不是随便定的：BCrypt 只处理前 72 字节，超长密码后半段会被静默丢弃，
 * 用户以为设了个超长密码，实际有效部分只有前 72 字节。
 */
@Schema(description = "注册请求")
public record RegisterRequest(

        @Schema(description = "登录名，4-20 位字母/数字/下划线", example = "bookworm")
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名只能包含字母、数字、下划线，长度 4-20")
        String username,

        @Schema(description = "密码，8-64 位且包含字母和数字", example = "novel123456")
        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "密码需 8-64 位且同时包含字母和数字")
        String password,

        @Schema(description = "昵称，不传则默认与用户名相同", example = "书虫")
        @Size(max = 20, message = "昵称最长 20 个字符")
        String nickname
) {
}
