package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发帖请求。
 *
 * <p>{@code content} 里用 {@code [[ref:0]]} 引用 {@code refNovelIds} 的第 0 本，
 * 服务端只校验下标不越界，不校验每个引用是否真的被用到。
 */
@Schema(description = "发表帖子")
public record CreatePostRequest(

        @Schema(description = "标题", example = "守山人这段写得太好了")
        @NotBlank(message = "标题不能为空")
        @Size(max = 120, message = "标题不能超过 120 字")
        String title,

        @Schema(description = "正文，引用小说处写 [[ref:0]]", example = "守山人这段[[ref:0]]直接封神")
        @NotBlank(message = "正文不能为空")
        @Size(max = 10000, message = "正文不能超过 10000 字")
        String content,

        @Schema(description = "封面对象键，可不传")
        @Size(max = 255, message = "封面键不能超过 255 字符")
        String coverKey,

        @Schema(description = "引用的小说 id，下标对应正文里的 [[ref:N]]，最多 5 本")
        @Size(max = 5, message = "最多引用 5 本小说")
        List<@NotNull(message = "引用的小说 id 不能为空")
             @Positive(message = "引用的小说 id 必须为正数") Long> refNovelIds
) {
}
