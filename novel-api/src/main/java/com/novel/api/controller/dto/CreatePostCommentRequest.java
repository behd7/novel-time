package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 帖子评论请求。
 *
 * @param parentCommentId 回复某条已有评论时传它的 id；直接评论帖子传 null
 */
@Schema(description = "发表帖子评论")
public record CreatePostCommentRequest(

        @Schema(description = "评论内容", example = "同感，这段确实好")
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 1000, message = "评论不能超过 1000 字")
        String content,

        @Schema(description = "被回复的评论 id，直接评论帖子时不传", example = "12")
        @Positive(message = "parentCommentId 必须为正数")
        Long parentCommentId
) {
}
