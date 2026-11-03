package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 加入书架请求。 */
@Schema(description = "加入书架")
public record AddBookshelfRequest(

        @Schema(description = "小说 id", example = "1")
        @NotNull(message = "novelId 不能为空")
        @Positive(message = "novelId 必须为正数")
        Long novelId
) {
}
