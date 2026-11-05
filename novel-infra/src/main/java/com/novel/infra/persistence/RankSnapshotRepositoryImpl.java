package com.novel.infra.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novel.core.rank.domain.RankEntry;
import com.novel.core.rank.domain.RankPeriod;
import com.novel.core.rank.port.RankSnapshotRepository;
import com.novel.infra.persistence.entity.RankEntity;
import com.novel.infra.persistence.mapper.RankMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 榜单快照仓储。
 *
 * <p>{@link #replace} 是"删干净再插一批"，而且必须在一个事务里：
 * 先删后插之间如果被读到（或者进程挂掉），用户会看到一个空榜。
 * 榜单页是首页级别的入口，空榜比旧榜难看得多。
 *
 * <p>{@code period} 存枚举名的小写（{@code daily} / {@code weekly} / {@code monthly} / {@code all}）：
 * 库里存字符串而不是存序号，是为了 `mysql` 里直接看得懂，
 * 也为了以后加周期时不用回头改数字含义。
 */
@Repository
@RequiredArgsConstructor
public class RankSnapshotRepositoryImpl implements RankSnapshotRepository {

    private final RankMapper rankMapper;

    @Override
    @Transactional
    public void replace(RankPeriod period, LocalDate rankDate, List<RankEntry> entries) {
        String periodKey = period.name().toLowerCase(Locale.ROOT);

        rankMapper.delete(Wrappers.<RankEntity>lambdaQuery()
                .eq(RankEntity::getPeriod, periodKey)
                .eq(RankEntity::getRankDate, rankDate));
        if (entries.isEmpty()) {
            return;
        }

        List<RankEntity> rows = entries.stream().map(entry -> {
            RankEntity row = new RankEntity();
            row.setPeriod(periodKey);
            row.setRankDate(rankDate);
            row.setNovelId(entry.novelId());
            row.setRankNo(entry.rankNo());
            // DECIMAL(12,4)：不在这里定死精度，MySQL 会按列定义截断，但提前截一次能避免
            // "写进去 2.4316666666666666、读出来 2.4317"这种测试里对不上的困惑
            row.setScore(BigDecimal.valueOf(entry.score()).setScale(4, RoundingMode.HALF_UP));
            return row;
        }).toList();

        rankMapper.batchInsert(rows);
    }

    @Override
    public List<RankEntry> top(RankPeriod period, LocalDate rankDate, int limit) {
        // 值只来自配置（1~1000），仍然夹一道：拼进 SQL 的字符串不接受任何外部输入
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        return rankMapper.selectList(Wrappers.<RankEntity>lambdaQuery()
                        .eq(RankEntity::getPeriod, period.name().toLowerCase(Locale.ROOT))
                        .eq(RankEntity::getRankDate, rankDate)
                        .orderByAsc(RankEntity::getRankNo)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(row -> new RankEntry(row.getNovelId(), row.getRankNo(), row.getScore().doubleValue()))
                .toList();
    }
}
