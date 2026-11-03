package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 刷新令牌 / 登出请求。 */
@Schema(description = "刷新令牌请求")
public record RefreshRequest(

        @Schema(description = "登录时下发的 refreshToken")
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken
) {
}
