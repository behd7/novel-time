package com.novel.infra.job;

import com.novel.core.rank.application.RankRefreshService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 榜单结算任务。
 *
 * <p>自己把异常吞掉，不让它冒到调度器：一次结算失败只应该意味着"这一轮没刷新"，
 * 上一轮的快照还在服务，下一轮十分钟后还会重来。异常冒出去只会得到一条刺眼的堆栈，
 * 而实际影响是零——这个取舍和阅读数落库任务一致。
 *
 * <p>多实例部署时这里会重复执行，但结算本身是幂等的（整份替换），所以不做分布式锁。
 * 真要上多实例，先解决的是 Outbox 投递，不是榜单。
 *
 * <p>为什么"吞异常"的写法必须配一个失败计数：日志里一条 ERROR 很容易淹在几万行里，
 * 而 {@code novel.rank.refresh.failures} 是单调递增的，画成曲线就是"从什么时候开始每轮都失败"。
 * 代价是调度线程仍在跑，只是这一轮的快照没换——指标让这件事从"没人知道"变成"一眼看到"。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "novel.rank", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RankRefreshJob {

    private final RankRefreshService rankRefreshService;
    private final Clock clock;
    private final Timer refreshTimer;
    private final Counter failures;

    public RankRefreshJob(RankRefreshService rankRefreshService, Clock clock, MeterRegistry registry) {
        this.rankRefreshService = rankRefreshService;
        this.clock = clock;
        this.refreshTimer = Timer.builder("novel.rank.refresh")
                .description("一轮榜单结算（日/周/月/总四个周期）的耗时")
                .register(registry);
        this.failures = Counter.builder("novel.rank.refresh.failures")
                .description("榜单结算抛异常的次数，单调递增")
                .register(registry);
    }

    @Scheduled(cron = "${novel.rank.refresh-cron:0 */10 * * * ?}", zone = "Asia/Shanghai")
    public void refresh() {
        refreshTimer.record(() -> {
            try {
                rankRefreshService.refresh(LocalDate.now(clock));
            } catch (Exception e) {
                failures.increment();
                log.error("榜单结算失败，保留上一轮快照：{}", e.getMessage(), e);
            }
        });
    }
}
