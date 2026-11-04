package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 回复书评请求。
 *
 * @param parentReplyId 回复某条已有回复时传它的 id；直接回复主楼传 null
 */
@Schema(description = "回复书评")
public record CreateReplyRequest(

        @Schema(description = "回复内容", example = "同感，第三章那段确实好。")
        @NotBlank(message = "回复内容不能为空")
        @Size(max = 1000, message = "回复不能超过 1000 字")
        String content,

        @Schema(description = "被回复的回复 id，回复主楼时不传", example = "12")
        @Positive(message = "parentReplyId 必须为正数")
        Long parentReplyId
) {
}
