package com.novel.infra.job;

import com.novel.core.rank.application.RankRefreshService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 榜单结算的失败可观测性。
 *
 * <p>这个任务的设计是"失败只吞不抛"——上一轮快照还在服务，十分钟后重来，比抛异常更合理。
 * 代价是失败完全无声，所以必须靠计数把"从什么时候开始每轮都失败"变成一条曲线。
 * 这里守的就是这条兜底：异常照吞，但计数一定要加。
 */
@ExtendWith(MockitoExtension.class)
class RankRefreshJobTest {

    @Mock
    private RankRefreshService rankRefreshService;

    private SimpleMeterRegistry registry;
    private RankRefreshJob job;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        // 2026-11-11T00:00:00Z 在 Asia/Shanghai 是 11-11 08:00
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        job = new RankRefreshJob(rankRefreshService, clock, registry);
    }

    @Test
    @DisplayName("结算成功：耗时被记录，失败计数不动")
    void success_recordsTimerOnly() {
        job.refresh();

        assertThat(registry.get("novel.rank.refresh").timer().count()).isEqualTo(1L);
        assertThat(registry.get("novel.rank.refresh.failures").counter().count()).isZero();
        verify(rankRefreshService).refresh(eq(LocalDate.of(2026, 11, 11)));
    }

    @Test
    @DisplayName("结算抛异常：异常不外抛，失败计数 +1，耗时仍然记一次")
    void failure_countedAndSwallowed() {
        doThrow(new IllegalStateException("boom")).when(rankRefreshService).refresh(LocalDate.of(2026, 11, 11));

        job.refresh();

        assertThat(registry.get("novel.rank.refresh.failures").counter().count()).isEqualTo(1d);
        // 计时器仍要记：失败的轮次同样占用了调度窗口，不算进去就会低估真实压力
        assertThat(registry.get("novel.rank.refresh").timer().count()).isEqualTo(1L);
    }
}
