package com.novel.api.controller;

import com.novel.api.controller.dto.RatingRequest;
import com.novel.api.security.SecurityUtils;
import com.novel.common.result.Result;
import com.novel.core.review.application.RatingService;
import com.novel.core.review.application.view.RatingSummaryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评分接口。查询免登录（未登录时 {@code myScore} 为空），打分与取消需要登录。
 */
@Tag(name = "评分", description = "1-5 星，一人一票")
@RestController
@RequestMapping("/api/v1/novels/{novelId}/rating")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @Operation(summary = "评分汇总", description = "平均分、人数、我的评分、星级分布")
    @GetMapping
    public Result<RatingSummaryView> summary(@PathVariable long novelId) {
        return Result.ok(ratingService.summary(novelId, SecurityUtils.currentUserIdOrNull()));
    }

    @Operation(summary = "打分或改分", description = "同一用户重复调用是改分，不会重复计入人数")
    @PutMapping
    public Result<RatingSummaryView> rate(@PathVariable long novelId,
                                          @Valid @RequestBody RatingRequest request) {
        return Result.ok(ratingService.rate(novelId, SecurityUtils.currentUserId(), request.score()));
    }

    @Operation(summary = "取消评分", description = "幂等：没打过分也返回成功")
    @DeleteMapping
    public Result<RatingSummaryView> remove(@PathVariable long novelId) {
        return Result.ok(ratingService.remove(novelId, SecurityUtils.currentUserId()));
    }
}
