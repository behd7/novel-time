package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 修改个人资料。
 *
 * <p>所有字段可空，语义是"不传就不改"。想清空签名就传空字符串，想保留就整个字段都不传。
 */
@Schema(description = "修改个人资料请求")
public record UpdateProfileRequest(

        @Schema(description = "昵称", example = "书虫")
        @Size(min = 1, max = 20, message = "昵称长度 1-20")
        String nickname,

        @Schema(description = "头像对象键，由 /files/presign 上传后回传", example = "avatar/2026/11/02/xxx.jpg")
        @Size(max = 255, message = "头像键过长")
        String avatarKey,

        @Schema(description = "个性签名")
        @Size(max = 255, message = "个性签名最长 255 字符")
        String bio,

        @Schema(description = "性别 0 未知 / 1 男 / 2 女", example = "1")
        @Min(value = 0, message = "性别取值 0-2")
        @Max(value = 2, message = "性别取值 0-2")
        Integer gender,

        @Schema(description = "生日", example = "2000-01-01")
        @Past(message = "生日必须是过去的日期")
        LocalDate birthday
) {
}
