package com.novel.api.controller;

import com.novel.common.result.Result;
import com.novel.core.novel.application.NovelService;
import com.novel.core.novel.application.view.CategoryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类接口。
 *
 * <p>返回扁平列表而不是嵌套树：前端按 {@code parentId} 一次 reduce 就能拼出两级结构，
 * 后端拼树反而会让"某个二级分类下没有子项"这种边界变得难处理。
 */
@Tag(name = "分类")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final NovelService novelService;

    @Operation(summary = "全部分类", description = "两级，parentId=0 为顶级；结果缓存 10 分钟")
    @GetMapping
    public Result<List<CategoryView>> list() {
        return Result.ok(novelService.categories());
    }
}
