package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 打分请求。 */
@Schema(description = "小说评分")
public record RatingRequest(

        @Schema(description = "1-5 星", example = "5")
        @NotNull(message = "score 不能为空")
        @Min(value = 1, message = "评分最低 1 星")
        @Max(value = 5, message = "评分最高 5 星")
        Integer score
) {
}
