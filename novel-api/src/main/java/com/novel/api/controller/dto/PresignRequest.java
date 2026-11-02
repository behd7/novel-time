package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 预签名上传请求。
 *
 * @param bizType     业务分类：COVER / AVATAR / POST / IMPORT
 * @param fileName    原始文件名，仅用于取扩展名
 * @param contentType 必须是白名单内的 MIME 类型，且上传时必须原样带上
 */
@Schema(description = "预签名上传请求")
public record PresignRequest(

        @Schema(description = "业务分类", example = "COVER", allowableValues = {"COVER", "AVATAR", "POST", "IMPORT"})
        @NotBlank(message = "bizType 不能为空")
        String bizType,

        @Schema(description = "原始文件名", example = "cover.jpg")
        @NotBlank(message = "fileName 不能为空")
        @Size(max = 128, message = "文件名长度不能超过 128")
        String fileName,

        @Schema(description = "MIME 类型", example = "image/jpeg")
        @NotBlank(message = "contentType 不能为空")
        @Size(max = 64, message = "contentType 长度不能超过 64")
        String contentType
) {
}
