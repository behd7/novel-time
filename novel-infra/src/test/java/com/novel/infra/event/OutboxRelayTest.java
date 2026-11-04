package com.novel.infra.event;

import com.novel.infra.persistence.mapper.OutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outbox 投递的状态流转。
 *
 * <p>要保证的是三条：成功才置"已投递"、失败要退避而不是立刻重发、
 * 连续失败到上限必须停下来（否则一条毒消息会把日志和 CPU 一起刷爆）。
 * 这些逻辑用真 broker 测很麻烦，用假的 sender 反而更准。
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxMapper outboxMapper;
    @Mock
    private OutboxMessageSender sender;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private OutboxProperties properties;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        properties = new OutboxProperties();
        properties.setMaxAttempts(6);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        relay = new OutboxRelay(outboxMapper, sender, properties, transactionManager);
    }

    @Test
    @DisplayName("broker 确认后置为已投递")
    void ack_marksSent() throws Exception {
        OutboxEntity row = row(10L, 0);
        when(outboxMapper.claimBatch(anyInt(), any())).thenReturn(List.of(row));
        when(sender.send(any())).thenReturn(true);

        assertThat(relay.relayOnce()).isEqualTo(1);

        verify(outboxMapper).markSent(10L);
        verify(outboxMapper, never()).markFailed(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("没确认时退避重试：retry_count+1，下次时间往后推")
    void nack_schedulesRetry() throws Exception {
        OutboxEntity row = row(11L, 0);
        when(outboxMapper.claimBatch(anyInt(), any())).thenReturn(List.of(row));
        when(sender.send(any())).thenReturn(false);

        assertThat(relay.relayOnce()).isZero();

        ArgumentCaptor<LocalDateTime> nextRetry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxMapper).markFailed(eq(11L), eq(0), eq(1), nextRetry.capture());
        // 第一次失败退避 2 秒
        assertThat(nextRetry.getValue()).isAfter(LocalDateTime.now().plusSeconds(1));
        verify(outboxMapper, never()).markSent(anyLong());
    }

    @Test
    @DisplayName("发送抛异常也按失败处理，不会把整批炸掉")
    void exception_schedulesRetry() throws Exception {
        OutboxEntity row = row(12L, 0);
        when(outboxMapper.claimBatch(anyInt(), any())).thenReturn(List.of(row));
        when(sender.send(any())).thenThrow(new IllegalStateException("broker down"));

        assertThat(relay.relayOnce()).isZero();

        verify(outboxMapper).markFailed(eq(12L), eq(0), eq(1), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("达到最大尝试次数后置为投递失败，不再无限重试")
    void exhausted_marksFailedPermanently() throws Exception {
        OutboxEntity row = row(13L, properties.getMaxAttempts() - 1);
        when(outboxMapper.claimBatch(anyInt(), any())).thenReturn(List.of(row));
        when(sender.send(any())).thenReturn(false);

        relay.relayOnce();

        verify(outboxMapper).markFailed(eq(13L), eq(2), eq(properties.getMaxAttempts()), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("没有待投递记录时不做任何事")
    void emptyBatch_doesNothing() throws Exception {
        when(outboxMapper.claimBatch(anyInt(), any())).thenReturn(List.of());

        assertThat(relay.relayOnce()).isZero();

        verify(sender, never()).send(any());
        verify(outboxMapper, never()).markSent(anyLong());
        verify(outboxMapper, never()).markFailed(anyLong(), anyInt(), anyInt(), any());
        verify(outboxMapper, never()).markFailed(anyLong(), anyInt(), anyInt(), any(LocalDateTime.class));
    }

    private OutboxEntity row(long id, int retryCount) {
        OutboxEntity row = new OutboxEntity();
        row.setId(id);
        row.setMessageKey("novel.search.created:1:2");
        row.setEventType("novel.search.created");
        row.setAggregateType("novel");
        row.setAggregateId(1L);
        row.setPayload("{\"novelId\":1,\"version\":2}");
        row.setStatus(0);
        row.setRetryCount(retryCount);
        row.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        return row;
    }
}
