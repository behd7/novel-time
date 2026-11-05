package com.novel.api.controller;

import com.novel.common.result.PageResult;
import com.novel.common.result.Result;
import com.novel.core.rank.application.RankQuery;
import com.novel.core.rank.application.RankService;
import com.novel.core.rank.application.view.RankItemView;
import com.novel.core.rank.domain.RankPeriod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 榜单接口。
 *
 * <p>和阅读、搜索一样免登录：榜单是给所有人看的入口，要求登录等于把它变成"会员功能"。
 *
 * <p>路径独立于 {@code /novels}：榜单读的是另一份数据（快照），不是列表加个排序参数。
 * 挂在 {@code /novels?sort=hot} 下会让人以为它和列表同源，将来想给榜单单独加缓存、
 * 单独降级时也会被路径绑住。
 */
@Tag(name = "榜单", description = "日榜 / 周榜 / 月榜 / 总榜")
@RestController
@RequestMapping("/api/v1/ranks")
@RequiredArgsConstructor
public class RankController {

    private final RankService rankService;

    @Operation(summary = "榜单",
            description = "period 取 daily | weekly | monthly | all；返回名次、热度分和小说卡片；"
                    + "榜单由结算任务生成，最多滞后一个刷新周期")
    @GetMapping
    public Result<PageResult<RankItemView>> list(
            @Parameter(description = "daily | weekly | monthly | all") @RequestParam String period,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(rankService.list(new RankQuery(RankPeriod.from(period), page, size)));
    }
}
