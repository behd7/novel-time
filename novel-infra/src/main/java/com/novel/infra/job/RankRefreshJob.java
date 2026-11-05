package com.novel.infra.job;

import com.novel.core.rank.application.RankRefreshService;
import lombok.RequiredArgsConstructor;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "novel.rank", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RankRefreshJob {

    private final RankRefreshService rankRefreshService;
    private final Clock clock;

    @Scheduled(cron = "${novel.rank.refresh-cron:0 */10 * * * ?}", zone = "Asia/Shanghai")
    public void refresh() {
        try {
            rankRefreshService.refresh(LocalDate.now(clock));
        } catch (Exception e) {
            log.error("榜单结算失败，保留上一轮快照：{}", e.getMessage(), e);
        }
    }
}
