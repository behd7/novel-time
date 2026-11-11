package com.novel.infra.metrics;

import com.novel.infra.config.RabbitConfig;
import com.novel.infra.persistence.mapper.OutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 积压指标的读数语义。
 *
 * <p>要守住的三条：没有待投递行时滞后是 0 而不是未知；读不到数据时保留上次值；
 * 一次都没读到时必须是 NaN —— "不知道"和"零积压"如果都画成 0，这个指标就不能拿来值班。
 */
@ExtendWith(MockitoExtension.class)
class BacklogMetricsTest {

    @Mock
    private OutboxMapper outboxMapper;

    @Mock
    private AmqpAdmin amqpAdmin;

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        new BacklogMetrics(outboxMapper, amqpAdmin, registry);
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }

    private void stubDeadQueue(int messages) {
        QueueInformation info = mock(QueueInformation.class);
        when(info.getMessageCount()).thenReturn(messages);
        when(amqpAdmin.getQueueInfo(RabbitConfig.QUEUE_DEAD)).thenReturn(info);
    }

    @Test
    @DisplayName("正常读数：待投递条数、滞后秒数、死信条数各归其位")
    void normalReadings() {
        when(outboxMapper.countPending()).thenReturn(7L);
        when(outboxMapper.selectPendingLagSeconds()).thenReturn(42L);
        stubDeadQueue(3);

        assertThat(gauge(BacklogMetrics.OUTBOX_PENDING)).isEqualTo(7d);
        assertThat(gauge(BacklogMetrics.OUTBOX_LAG)).isEqualTo(42d);
        assertThat(gauge(BacklogMetrics.DEAD_LETTERS)).isEqualTo(3d);
    }

    @Test
    @DisplayName("没有待投递行时 SQL 返回 NULL，滞后按 0 处理")
    void emptyOutbox_lagIsZero() {
        when(outboxMapper.countPending()).thenReturn(0L);
        when(outboxMapper.selectPendingLagSeconds()).thenReturn(null);

        assertThat(gauge(BacklogMetrics.OUTBOX_PENDING)).isZero();
        assertThat(gauge(BacklogMetrics.OUTBOX_LAG)).isZero();
    }

    @Test
    @DisplayName("死信队列还没声明时按 0 处理，不让抓取失败")
    void queueNotDeclared_isZero() {
        when(amqpAdmin.getQueueInfo(RabbitConfig.QUEUE_DEAD)).thenReturn(null);

        assertThat(gauge(BacklogMetrics.DEAD_LETTERS)).isZero();
    }

    @Test
    @DisplayName("第一次抓取就失败时是 NaN：未知不等于零积压")
    void firstReadFails_nan() {
        when(outboxMapper.countPending()).thenThrow(new IllegalStateException("db down"));

        assertThat(gauge(BacklogMetrics.OUTBOX_PENDING)).isNaN();
    }

    @Test
    @DisplayName("先成功后失败：保留上次成功值，避免图上画出假的清零")
    void readFails_keepsLastGoodValue() {
        when(outboxMapper.countPending())
                .thenReturn(7L)
                .thenThrow(new IllegalStateException("db down"));

        assertThat(gauge(BacklogMetrics.OUTBOX_PENDING)).isEqualTo(7d);
        assertThat(gauge(BacklogMetrics.OUTBOX_PENDING)).isEqualTo(7d);
    }
}
