package com.novel.infra.event;

import com.novel.infra.config.RabbitConfig;
import com.novel.infra.persistence.mapper.OutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫描并投递 Outbox。
 *
 * <p>为什么要在<b>一个事务里</b>完成"选中 → 投递 → 改状态"：
 * 选中的是待投递记录，改的是同一批行的状态，中间还有一次网络往返。
 * 把行锁住直到状态落库，多实例部署时另一个实例自然会跳过这批（{@code SKIP LOCKED}），
 * 不需要再发明"认领"字段和租约超时。代价是这条事务会持有连接几百毫秒，
 * 对一个每分钟几十条消息的系统来说完全不是瓶颈。
 *
 * <p>失败不重发同一条消息，而是把行推后：{@code retry_count + 1}、
 * {@code next_retry_at = now + 退避秒数}。这样"重试"就是一次普通的 UPDATE，
 * 进程重启也不会丢状态，比在内存里攒一个重试队列可靠得多。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "novel.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    /** 2s、4s、8s、16s、32s …… 封顶 5 分钟，避免长故障时把日志刷爆。 */
    private static final long BASE_BACKOFF_SECONDS = 2;
    private static final long MAX_BACKOFF_SECONDS = 300;

    private final OutboxMapper outboxMapper;
    private final OutboxMessageSender sender;
    private final OutboxProperties properties;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelay(OutboxMapper outboxMapper,
                       OutboxMessageSender sender,
                       OutboxProperties properties,
                       PlatformTransactionManager transactionManager) {
        this.outboxMapper = outboxMapper;
        this.sender = sender;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${novel.outbox.poll-interval-ms:1000}")
    public void scheduledRelay() {
        try {
            int sent = relayOnce();
            if (sent > 0) {
                log.info("Outbox 投递完成 {} 条", sent);
            }
        } catch (Exception e) {
            // 这里是最后一道兜底：投递任务的异常不该让调度线程死掉，更不能影响别的定时任务
            log.error("Outbox 投递任务异常", e);
        }
    }

    /**
     * 跑一轮，返回成功投递的条数。抽成公开方法是为了测试能确定性地触发一次，
     * 而不是靠 {@code Thread.sleep} 等调度器。
     */
    public int relayOnce() {
        Integer sent = transactionTemplate.execute(status -> {
            List<OutboxEntity> batch = outboxMapper.claimBatch(properties.getBatchSize(), LocalDateTime.now());
            int ok = 0;
            for (OutboxEntity row : batch) {
                if (deliver(row)) {
                    ok++;
                }
            }
            return ok;
        });
        return sent == null ? 0 : sent;
    }

    private boolean deliver(OutboxEntity row) {
        OutboxMessage message = new OutboxMessage(
                RabbitConfig.EXCHANGE_EVENT,
                // 事件类型本身就是路由键：Topic 交换机按 "novel.search.#" 匹配，
                // 以后加榜单/统计，各自的消费者用前缀区分即可
                row.getEventType(),
                row.getMessageKey(),
                row.getEventType(),
                row.getPayload());

        try {
            if (sender.send(message)) {
                outboxMapper.markSent(row.getId());
                return true;
            }
            markFailure(row, "broker 未确认");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailure(row, "投递被中断");
            return false;
        } catch (Exception e) {
            markFailure(row, e.getMessage());
            return false;
        }
    }

    private void markFailure(OutboxEntity row, String reason) {
        int attempts = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
        if (attempts >= properties.getMaxAttempts()) {
            outboxMapper.markFailed(row.getId(), 2, attempts, LocalDateTime.now());
            log.error("事件投递失败，已放弃 key={} 原因={}", row.getMessageKey(), reason);
            return;
        }
        long delay = Math.min(BASE_BACKOFF_SECONDS << (attempts - 1), MAX_BACKOFF_SECONDS);
        // 同样截到秒：让库里的值和退避的语义对齐，免得四舍五入把退避时间拉长
        outboxMapper.markFailed(row.getId(), 0, attempts,
                LocalDateTime.now().plusSeconds(delay).withNano(0));
        log.warn("事件投递失败，{} 秒后重试（第 {} 次）key={} 原因={}",
                delay, attempts, row.getMessageKey(), reason);
    }
}
