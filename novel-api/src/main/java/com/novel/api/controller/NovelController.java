package com.novel.api.controller;

import com.novel.common.result.PageQuery;
import com.novel.common.result.PageResult;
import com.novel.common.result.Result;
import com.novel.core.novel.application.NovelQuery;
import com.novel.core.novel.application.NovelService;
import com.novel.core.novel.application.NovelSort;
import com.novel.core.novel.application.view.ChapterSummaryView;
import com.novel.core.novel.application.view.ChapterView;
import com.novel.core.novel.application.view.NovelDetailView;
import com.novel.core.novel.application.view.NovelSummaryView;
import com.novel.api.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读接口：列表、详情、目录、正文。
 *
 * <p>全部免登录。小说平台的阅读页是引流入口，要求登录会劝退一多半新用户；
 * 需要登录的只有书架、评分、评论这些"留下痕迹"的操作。
 */
@Tag(name = "小说", description = "列表 / 详情 / 目录 / 正文")
@RestController
@RequestMapping("/api/v1/novels")
@RequiredArgsConstructor
public class NovelController {

    private final NovelService novelService;

    @Operation(summary = "小说列表", description = "支持分类、状态过滤与四种排序；size 上限 50")
    @GetMapping
    public Result<PageResult<NovelSummaryView>> list(
            @Parameter(description = "分类 id") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "0 连载中 / 1 已完结") @RequestParam(required = false) Integer status,
            @Parameter(description = "latest | newest | wordCount | hot") @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        NovelQuery query = new NovelQuery(categoryId, status, NovelSort.from(sort), page, size);
        return Result.ok(novelService.list(query));
    }

    @Operation(summary = "小说详情",
            description = "每次调用会累加一次阅读数（写入 Redis，由定时任务落库）；"
                    + "榜单只按「同一个人同一天同一本书」计一次，匿名按 IP 去重")
    @GetMapping("/{id}")
    public Result<NovelDetailView> detail(@PathVariable long id, HttpServletRequest request) {
        return Result.ok(novelService.detail(id, viewerKey(request)));
    }

    /**
     * 榜单去重用的身份：登录了用用户 id，没登录用 IP。
     *
     * <p>没有解析 {@code X-Forwarded-For}——本项目前面没有反向代理，现在解析等于给伪造留口子。
     * 接了网关之后再补一行，而不是提前"预防性"地信任一个可伪造的头。
     */
    private String viewerKey(HttpServletRequest request) {
        Long userId = SecurityUtils.currentUserIdOrNull();
        return userId != null ? "u:" + userId : "ip:" + request.getRemoteAddr();
    }

    @Operation(summary = "章节目录", description = "按 seq 升序分页")
    @GetMapping("/{id}/chapters")
    public Result<PageResult<ChapterSummaryView>> catalog(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), PageQuery.MAX_SIZE);
        return Result.ok(novelService.catalog(id, Math.max(page, 1), safeSize));
    }

    @Operation(summary = "读某一章正文", description = "返回上下章序号，第一章 prevSeq 为 null，最后一章 nextSeq 为 null")
    @GetMapping("/{id}/chapters/{seq}")
    public Result<ChapterView> chapter(@PathVariable long id, @PathVariable int seq) {
        return Result.ok(novelService.readChapter(id, seq));
    }
}
