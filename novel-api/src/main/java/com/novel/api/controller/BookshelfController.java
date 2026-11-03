package com.novel.api.controller;

import com.novel.api.controller.dto.AddBookshelfRequest;
import com.novel.api.controller.dto.UpdateProgressRequest;
import com.novel.api.security.SecurityUtils;
import com.novel.common.result.PageQuery;
import com.novel.common.result.PageResult;
import com.novel.common.result.Result;
import com.novel.core.reading.application.BookshelfService;
import com.novel.core.reading.application.view.BookshelfView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 书架接口。全部需要登录。
 */
@Tag(name = "书架", description = "收藏与阅读进度")
@RestController
@RequestMapping("/api/v1/bookshelf")
@RequiredArgsConstructor
public class BookshelfController {

    private final BookshelfService bookshelfService;

    @Operation(summary = "我的书架", description = "按最近阅读时间倒序；hasUpdate 表示有新章节")
    @GetMapping
    public Result<PageResult<BookshelfView>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), PageQuery.MAX_SIZE);
        return Result.ok(bookshelfService.list(SecurityUtils.currentUserId(), Math.max(page, 1), safeSize));
    }

    @Operation(summary = "加入书架", description = "幂等：已在书架里直接返回成功")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody AddBookshelfRequest request) {
        bookshelfService.add(SecurityUtils.currentUserId(), request.novelId());
        return Result.ok();
    }

    @Operation(summary = "移出书架", description = "物理删除；幂等")
    @DeleteMapping("/{novelId}")
    public Result<Void> remove(@PathVariable long novelId) {
        bookshelfService.remove(SecurityUtils.currentUserId(), novelId);
        return Result.ok();
    }

    @Operation(summary = "更新阅读进度", description = "章节必须真实存在，否则返回 10202")
    @PutMapping("/{novelId}/progress")
    public Result<Void> updateProgress(@PathVariable long novelId,
                                       @Valid @RequestBody UpdateProgressRequest request) {
        bookshelfService.updateProgress(SecurityUtils.currentUserId(), novelId, request.seq());
        return Result.ok();
    }
}
