package com.novel.infra.metrics;

import com.novel.infra.config.RabbitConfig;
import com.novel.infra.persistence.mapper.OutboxMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;

/**
 * 吞吐与积压类指标。
 *
 * <p>埋的不是"服务器活得怎么样"，而是<b>这套设计有没有按预期工作</b>：
 * <ul>
 *   <li>{@code novel.outbox.pending} —— 还有几条变更没投出去</li>
 *   <li>{@code novel.outbox.lag.seconds} —— 最老的一条等了多久。这是 Outbox 模式唯一的可视图：
 *       "不丢消息"没法直接观测，"消息多晚才到"可以</li>
 *   <li>{@code novel.mq.dead.letters} —— 死信队列有几条。配了死信队列却不看深度，
 *       等于配了个黑洞：消息进去了没人知道</li>
 * </ul>
 *
 * <p>为什么用 {@link Gauge} 而不是定时任务往 {@code AtomicLong} 里写：Gauge 是拉模型，
 * Prometheus 抓一次算一次，空闲时零开销。代价是读数值发生在<b>抓取线程</b>上，
 * 数据库或 broker 抖动会直接让这次抓取抛异常，把整份 {@code /actuator/prometheus}
 * 打成一个 500——所以三个读数都套了 {@link LazyGauge}，失败时返回上一次成功值并在日志里留一条。
 *
 * <p>由此带来的一个限制要写清楚：应用刚启动、第一次抓取就失败时，仪表会显示 NaN 而不是 0。
 * 这是刻意的——"不知道"和"零积压"必须是两种颜色，混淆了就没法用它值班。
 */
@Slf4j
@Component
public class BacklogMetrics {

    public static final String OUTBOX_PENDING = "novel.outbox.pending";
    public static final String OUTBOX_LAG = "novel.outbox.lag.seconds";
    public static final String DEAD_LETTERS = "novel.mq.dead.letters";

    private final LazyGauge pending;
    private final LazyGauge lag;
    private final LazyGauge deadLetters;

    public BacklogMetrics(OutboxMapper outboxMapper, AmqpAdmin amqpAdmin, MeterRegistry registry) {
        this.pending = new LazyGauge(OUTBOX_PENDING, outboxMapper::countPending);
        this.lag = new LazyGauge(OUTBOX_LAG, () -> {
            Long seconds = outboxMapper.selectPendingLagSeconds();
            // 没有待投递行时 SQL 返回 NULL，语义是"零滞后"而不是"未知"
            return seconds == null ? 0d : seconds.doubleValue();
        });
        this.deadLetters = new LazyGauge(DEAD_LETTERS, () -> {
            QueueInformation info = amqpAdmin.getQueueInfo(RabbitConfig.QUEUE_DEAD);
            // 队列尚未声明时 RabbitAdmin 返回 null：当作 0，而不是让抓取失败
            return info == null ? 0d : info.getMessageCount();
        });

        Gauge.builder(OUTBOX_PENDING, pending, LazyGauge::read)
                .description("outbox 中 status=0 的待投递条数")
                .register(registry);
        Gauge.builder(OUTBOX_LAG, lag, LazyGauge::read)
                .description("最老一条待投递记录已等待的秒数，0 表示没有积压")
                .register(registry);
        Gauge.builder(DEAD_LETTERS, deadLetters, LazyGauge::read)
                .description("死信队列 q.novel.dead 中的消息条数")
                .register(registry);
    }

    /**
     * 抓取时读一次、失败留上次值的仪表。
     *
     * <p>刻意不吞掉"第一次就失败"的情况：那时没有上次值，返回 NaN 让面板显示无数据。
     * 如果拿 0 兜底，一次数据库抖动就会在图上画出一条"积压清零"的假谷。
     */
    static final class LazyGauge {

        private final String name;
        private final DoubleSupplier supplier;
        private final AtomicReference<Double> last = new AtomicReference<>(Double.NaN);

        LazyGauge(String name, DoubleSupplier supplier) {
            this.name = name;
            this.supplier = supplier;
        }

        double read() {
            try {
                double value = supplier.getAsDouble();
                last.set(value);
                return value;
            } catch (RuntimeException e) {
                // 指标读不到不能连累整份抓取：保留上一次的值，日志降级到 debug 避免刷屏
                log.debug("指标 {} 读取失败，保留上次值 {}：{}", name, last.get(), e.getMessage());
                return last.get();
            }
        }
    }
}
