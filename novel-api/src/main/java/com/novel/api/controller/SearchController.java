package com.novel.api.controller;

import com.novel.common.result.PageResult;
import com.novel.common.result.Result;
import com.novel.core.search.application.SearchQuery;
import com.novel.core.search.application.SearchService;
import com.novel.core.search.application.SearchSort;
import com.novel.core.search.application.view.SearchHitView;
import com.novel.core.search.application.view.SuggestView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索接口。
 *
 * <p>和阅读接口一样免登录：搜索是找书的入口，要求登录等于把新用户挡在门外。
 *
 * <p>路径刻意独立于 {@code /novels}：搜索读的不是数据库，而是 ES 里的副本，
 * 把它挂在小说资源下会让人误以为"搜索只是列表加了个 q 参数"，
 * 将来想给搜索单独限流、单独降级时也会被路径绑定拖住。
 */
@Tag(name = "搜索", description = "关键字搜索 / 搜索建议")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "搜索小说",
            description = "关键字匹配书名/作者/简介并返回高亮片段；支持分类、状态过滤与五种排序；"
                    + "最多翻到 page*size=1000")
    @GetMapping
    public Result<PageResult<SearchHitView>> search(
            @Parameter(description = "关键字，1~50 字符") @RequestParam String q,
            @Parameter(description = "分类 id") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "0 连载中 / 1 已完结") @RequestParam(required = false) Integer status,
            @Parameter(description = "relevance | latest | hot | rating | wordCount")
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        SearchQuery query = new SearchQuery(q, categoryId, status, SearchSort.from(sort), page, size);
        return Result.ok(searchService.search(query));
    }

    @Operation(summary = "搜索建议", description = "按书名前缀补全，最多 10 条")
    @GetMapping("/suggest")
    public Result<List<SuggestView>> suggest(
            @Parameter(description = "已输入的前缀") @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(searchService.suggest(q, limit));
    }
}
