package com.novel.api.controller;

import com.novel.api.controller.dto.CreateReplyRequest;
import com.novel.api.controller.dto.CreateReviewRequest;
import com.novel.api.security.SecurityUtils;
import com.novel.common.result.PageQuery;
import com.novel.common.result.PageResult;
import com.novel.common.result.Result;
import com.novel.core.review.application.ReviewService;
import com.novel.core.review.application.ReviewSort;
import com.novel.core.review.application.view.ReviewReplyView;
import com.novel.core.review.application.view.ReviewView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 书评与楼中楼接口。
 *
 * <p>浏览免登录、写入要登录：评论区的价值在于"能被看见"，
 * 要求登录才能读会把新用户挡在外面；但发帖与点赞必须绑定身份，否则没法做删除权限与幂等。
 */
@Tag(name = "书评", description = "书级评论区、楼中楼回复、点赞")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "书评列表", description = "sort=hot 按点赞与时间衰减排序，latest 按时间倒序")
    @GetMapping("/novels/{novelId}/reviews")
    public Result<PageResult<ReviewView>> list(
            @PathVariable long novelId,
            @Parameter(description = "hot | latest") @RequestParam(defaultValue = "hot") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), PageQuery.MAX_SIZE);
        return Result.ok(reviewService.list(novelId, ReviewSort.from(sort),
                Math.max(page, 1), safeSize, SecurityUtils.currentUserIdOrNull()));
    }

    @Operation(summary = "发表书评", description = "带 score 会同时记入评分")
    @PostMapping("/novels/{novelId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ReviewView> create(@PathVariable long novelId,
                                     @Valid @RequestBody CreateReviewRequest request) {
        return Result.ok(reviewService.create(novelId, SecurityUtils.currentUserId(),
                request.content(), request.score()));
    }

    @Operation(summary = "删除书评", description = "只能删自己的，软删除")
    @DeleteMapping("/reviews/{reviewId}")
    public Result<Void> delete(@PathVariable long reviewId) {
        reviewService.delete(reviewId, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @Operation(summary = "书评的回复列表", description = "两层：直接回复主楼 + 楼中楼")
    @GetMapping("/reviews/{reviewId}/replies")
    public Result<PageResult<ReviewReplyView>> replies(
            @PathVariable long reviewId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), PageQuery.MAX_SIZE);
        return Result.ok(reviewService.listReplies(reviewId, Math.max(page, 1), safeSize,
                SecurityUtils.currentUserIdOrNull()));
    }

    @Operation(summary = "回复书评", description = "带 parentReplyId 即为楼中楼")
    @PostMapping("/reviews/{reviewId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ReviewReplyView> reply(@PathVariable long reviewId,
                                         @Valid @RequestBody CreateReplyRequest request) {
        return Result.ok(reviewService.createReply(reviewId, SecurityUtils.currentUserId(),
                request.content(), request.parentReplyId()));
    }

    @Operation(summary = "删除回复", description = "只能删自己的")
    @DeleteMapping("/replies/{replyId}")
    public Result<Void> deleteReply(@PathVariable long replyId) {
        reviewService.deleteReply(replyId, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @Operation(summary = "点赞书评", description = "幂等，重复点赞不叠加计数")
    @PostMapping("/reviews/{reviewId}/like")
    public Result<Void> likeReview(@PathVariable long reviewId) {
        reviewService.likeReview(reviewId, SecurityUtils.currentUserId(), true);
        return Result.ok();
    }

    @Operation(summary = "取消点赞书评", description = "幂等")
    @DeleteMapping("/reviews/{reviewId}/like")
    public Result<Void> unlikeReview(@PathVariable long reviewId) {
        reviewService.likeReview(reviewId, SecurityUtils.currentUserId(), false);
        return Result.ok();
    }

    @Operation(summary = "点赞回复")
    @PostMapping("/replies/{replyId}/like")
    public Result<Void> likeReply(@PathVariable long replyId) {
        reviewService.likeReply(replyId, SecurityUtils.currentUserId(), true);
        return Result.ok();
    }

    @Operation(summary = "取消点赞回复")
    @DeleteMapping("/replies/{replyId}/like")
    public Result<Void> unlikeReply(@PathVariable long replyId) {
        reviewService.likeReply(replyId, SecurityUtils.currentUserId(), false);
        return Result.ok();
    }
}
