package com.novel.infra.job;

import com.novel.core.novel.port.NovelRepository;
import com.novel.infra.cache.RedisNovelViewCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 阅读数落库任务。
 *
 * <p>每 60 秒把 Redis 里攒的增量合并进 {@code novel.view_count}。
 * 详情页的写压力从"每次访问一次 UPDATE"降到"每分钟一次批量 UPDATE"，
 * 热门书的行锁争用直接消失。
 *
 * <p>失败处理刻意粗放：某一本更新失败只记 warn，不影响其他书。
 * 这种情况下那部分阅读数会丢——可以接受，因为阅读数本来就是近似指标，
 * 为此加一套补偿队列不值得。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushJob {

    private final RedisNovelViewCounter viewCounter;
    private final NovelRepository novelRepository;

    @Scheduled(fixedDelayString = "${novel.reading.view-flush-interval-ms:60000}")
    public void flush() {
        Map<Long, Long> deltas = viewCounter.drain();
        if (deltas.isEmpty()) {
            return;
        }
        int ok = 0;
        for (Map.Entry<Long, Long> entry : deltas.entrySet()) {
            try {
                novelRepository.addViewCount(entry.getKey(), entry.getValue());
                ok++;
            } catch (Exception e) {
                log.warn("阅读数落库失败 novelId={} delta={}：{}", entry.getKey(), entry.getValue(), e.getMessage());
            }
        }
        log.info("阅读数落库完成 {} 本", ok);
    }
}
