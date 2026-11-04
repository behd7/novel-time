package com.novel.infra.job;

import com.novel.core.review.application.RatingService;
import com.novel.core.review.port.RatingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 评分对账任务。
 *
 * <p>平时刻靠增量维护（打分时只改 sum 与 count），但增量会漂移：事务回滚、并发竞争、
 * 有人直接改库，都会让聚合值和明细对不上。这个任务每天凌晨从明细重算一遍，
 * 把 {@code novel} 上的三个字段拉回真实值。
 *
 * <p>分批扫（每批 200 个 novelId）而不是一次 {@code SELECT DISTINCT novel_id} 全读：
 * 小说几十万本时，一次性拉进内存再逐个更新，容易把应用和数据库一起拖垮。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingRecalcJob {

    private static final int BATCH_SIZE = 200;

    private final RatingRepository ratingRepository;
    private final RatingService ratingService;

    @Scheduled(cron = "${novel.review.rating-recalc-cron:0 30 3 * * ?}")
    public void recalculateAll() {
        long lastNovelId = 0L;
        int total = 0;
        while (true) {
            List<Long> ids = ratingRepository.pageNovelIdsWithRating(lastNovelId, BATCH_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            for (Long novelId : ids) {
                try {
                    ratingService.recalculate(novelId);
                    total++;
                } catch (Exception e) {
                    log.warn("评分对账失败 novelId={}：{}", novelId, e.getMessage());
                }
            }
            lastNovelId = ids.get(ids.size() - 1);
            if (ids.size() < BATCH_SIZE) {
                break;
            }
        }
        log.info("评分对账完成，重算 {} 本", total);
    }
}
