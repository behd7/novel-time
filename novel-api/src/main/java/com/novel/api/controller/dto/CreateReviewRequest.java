package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发书评请求。
 *
 * @param score 可选星级；带上会同时记入评分，避免"书评区 5 星、评分区没打分"的错位
 */
@Schema(description = "发表书评")
public record CreateReviewRequest(

        @Schema(description = "书评内容", example = "守山人的设定很扎实，前三章就把人物立住了。")
        @NotBlank(message = "书评内容不能为空")
        @Size(max = 2000, message = "书评不能超过 2000 字")
        String content,

        @Schema(description = "可选星级 1-5", example = "5")
        @Min(value = 1, message = "评分最低 1 星")
        @Max(value = 5, message = "评分最高 5 星")
        Integer score
) {
}
