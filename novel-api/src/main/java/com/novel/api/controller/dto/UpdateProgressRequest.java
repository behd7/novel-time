package com.novel.api.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 更新阅读进度请求。 */
@Schema(description = "更新阅读进度")
public record UpdateProgressRequest(

        @Schema(description = "读到第几章", example = "3")
        @NotNull(message = "seq 不能为空")
        @Min(value = 1, message = "章节序号从 1 开始")
        Integer seq
) {
}
