package com.novel.core.rank.application;

import com.novel.common.result.PageResult;
import com.novel.core.novel.application.view.NovelSummaryView;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.rank.application.view.RankItemView;
import com.novel.core.rank.domain.RankEntry;
import com.novel.core.rank.domain.RankPeriod;
import com.novel.core.rank.port.RankCache;
import com.novel.core.rank.port.RankSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 榜单读取。
 *
 * <p>只做三件事：缓存命中就返回、未命中就查快照并补卡片、分页在内存里切。
 *
 * <p>为什么分页在内存里做：一个周期的快照最多两三百行，还不到一次网络往返的量级。
 * 让 SQL 去 {@code LIMIT offset} 反而多一次往返，而且 offset 会随页码漂移——
 * 榜单每十分钟重排一次，翻页时"第 2 页"的内容本来就在变，做再精确的 SQL 分页也没意义。
 *
 * <p>读路径刻意<b>不触发结算</b>。快照为空就返回空榜，等下一次结算任务来填。
 * 在读接口里写库是这类系统最常见的自伤：一次凌晨的空窗会被流量放大成数据库抖动。
 */
@Service
@RequiredArgsConstructor
public class RankService {

    private final RankSnapshotRepository snapshotRepository;
    private final NovelRepository novelRepository;
    private final RankCache rankCache;
    private final RankSettings settings;
    private final Clock clock;

    public PageResult<RankItemView> list(RankQuery query) {
        RankPeriod period = query.period();
        LocalDate rankDate = period.rankDate(LocalDate.now(clock));

        List<RankItemView> items = rankCache.get(period, rankDate);
        if (items == null) {
            items = assemble(period, rankDate);
            rankCache.put(period, rankDate, items);
        }

        // 用 long 算偏移：page 直接来自查询参数，Integer.MAX_VALUE 时 int 乘法会溢出成负数，
        // 从而绕过下面的越界判断，最后在 subList 里抛成 500
        long offset = (long) (query.getPage() - 1) * query.getSize();
        if (offset >= items.size()) {
            // 越界页也要带真实总数，否则前端会把"翻过头了"当成"榜单空了"
            return PageResult.of(List.of(), items.size(), query.getPage(), query.getSize());
        }
        int from = (int) offset;
        int to = Math.min(from + query.getSize(), items.size());
        return PageResult.of(List.copyOf(items.subList(from, to)),
                items.size(), query.getPage(), query.getSize());
    }

    /**
     * 读快照 + 一次性补卡片。
     *
     * <p>补卡片是<b>一次</b> {@code IN} 查询，不是每条一次主键查——两三百条就是两三百次往返，
     * 而这个接口的 QPS 和首页同级。快照里可能残留已被下架的书（结算和读取之间有时间差），
     * 取不到就跳过，不让整个接口 500。
     */
    private List<RankItemView> assemble(RankPeriod period, LocalDate rankDate) {
        List<RankEntry> entries = snapshotRepository.top(period, rankDate, settings.snapshotSize());
        if (entries.isEmpty()) {
            return List.of();
        }

        Map<Long, Novel> novels = novelRepository.findByIds(
                        entries.stream().map(RankEntry::novelId).toList())
                .stream()
                .collect(Collectors.toMap(Novel::id, Function.identity()));

        return entries.stream()
                .filter(entry -> novels.containsKey(entry.novelId()))
                .map(entry -> new RankItemView(entry.rankNo(), entry.score(),
                        NovelSummaryView.from(novels.get(entry.novelId()))))
                .toList();
    }
}
