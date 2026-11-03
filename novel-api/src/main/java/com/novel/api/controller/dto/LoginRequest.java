package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "登录请求")
public record LoginRequest(

        @Schema(description = "登录名", example = "bookworm")
        @NotBlank(message = "用户名不能为空")
        String username,

        @Schema(description = "密码", example = "novel123456")
        @NotBlank(message = "密码不能为空")
        String password
) {
}
