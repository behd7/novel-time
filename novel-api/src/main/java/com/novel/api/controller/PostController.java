package com.novel.api.controller;

import com.novel.api.controller.dto.CreatePostCommentRequest;
import com.novel.api.controller.dto.CreatePostRequest;
import com.novel.api.security.SecurityUtils;
import com.novel.common.result.PageQuery;
import com.novel.common.result.PageResult;
import com.novel.common.result.Result;
import com.novel.core.community.application.PostQuery;
import com.novel.core.community.application.PostService;
import com.novel.core.community.application.PostSort;
import com.novel.core.community.application.view.PostCommentView;
import com.novel.core.community.application.view.PostDetailView;
import com.novel.core.community.application.view.PostView;
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
 * 社区接口：帖子、引用卡片、点赞与两层评论。
 *
 * <p>浏览免登录、写入要登录：社区的价值在于被看见，要求登录才能读会挡住新用户；
 * 但发帖、评论、点赞必须绑定身份，否则做不了删除权限与幂等。
 */
@Tag(name = "社区", description = "帖子、小说引用、点赞、两层评论")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "帖子列表", description = "sort=hot 按点赞与评论加权衰减，latest 按时间倒序；可按 novelId 反查引用")
    @GetMapping("/posts")
    public Result<PageResult<PostView>> list(
            @Parameter(description = "hot | latest") @RequestParam(defaultValue = "hot") String sort,
            @Parameter(description = "按引用的小说筛选") @RequestParam(required = false) Long novelId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PostQuery query = new PostQuery(PostSort.from(sort), novelId, page, size);
        return Result.ok(postService.list(query, SecurityUtils.currentUserIdOrNull()));
    }

    @Operation(summary = "发表帖子", description = "正文用 [[ref:0]] 引用 refNovelIds 的第 0 本")
    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PostDetailView> create(@Valid @RequestBody CreatePostRequest request) {
        return Result.ok(postService.create(SecurityUtils.currentUserId(),
                request.title(), request.content(), request.coverKey(), request.refNovelIds()));
    }

    @Operation(summary = "帖子详情", description = "返回正文与小说卡片，浏览数 +1")
    @GetMapping("/posts/{postId}")
    public Result<PostDetailView> detail(@PathVariable long postId) {
        return Result.ok(postService.detail(postId, SecurityUtils.currentUserIdOrNull()));
    }

    @Operation(summary = "删除帖子", description = "只能删自己的，软删除")
    @DeleteMapping("/posts/{postId}")
    public Result<Void> delete(@PathVariable long postId) {
        postService.delete(postId, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @Operation(summary = "点赞帖子", description = "幂等，重复点赞不叠加计数")
    @PostMapping("/posts/{postId}/like")
    public Result<Void> like(@PathVariable long postId) {
        postService.like(postId, SecurityUtils.currentUserId(), true);
        return Result.ok();
    }

    @Operation(summary = "取消点赞帖子", description = "幂等")
    @DeleteMapping("/posts/{postId}/like")
    public Result<Void> unlike(@PathVariable long postId) {
        postService.like(postId, SecurityUtils.currentUserId(), false);
        return Result.ok();
    }

    @Operation(summary = "帖子评论列表", description = "平铺分页，前端按 rootId 归并成两层")
    @GetMapping("/posts/{postId}/comments")
    public Result<PageResult<PostCommentView>> comments(
            @PathVariable long postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), PageQuery.MAX_SIZE);
        return Result.ok(postService.listComments(postId, Math.max(page, 1), safeSize,
                SecurityUtils.currentUserIdOrNull()));
    }

    @Operation(summary = "发表评论", description = "带 parentCommentId 即为楼中楼")
    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PostCommentView> comment(@PathVariable long postId,
                                           @Valid @RequestBody CreatePostCommentRequest request) {
        return Result.ok(postService.createComment(postId, SecurityUtils.currentUserId(),
                request.content(), request.parentCommentId()));
    }

    @Operation(summary = "删除评论", description = "只能删自己的")
    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable long commentId) {
        postService.deleteComment(commentId, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @Operation(summary = "点赞评论", description = "幂等")
    @PostMapping("/comments/{commentId}/like")
    public Result<Void> likeComment(@PathVariable long commentId) {
        postService.likeComment(commentId, SecurityUtils.currentUserId(), true);
        return Result.ok();
    }

    @Operation(summary = "取消点赞评论", description = "幂等")
    @DeleteMapping("/comments/{commentId}/like")
    public Result<Void> unlikeComment(@PathVariable long commentId) {
        postService.likeComment(commentId, SecurityUtils.currentUserId(), false);
        return Result.ok();
    }
}
