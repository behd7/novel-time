package com.novel.infra.event;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 用 RabbitMQ 投递 Outbox 消息，并等 broker 确认。
 *
 * <p>三个刻意的做法：
 * <ol>
 *   <li><b>直接发 {@link Message} 而不是用消息转换器</b>：消息体就是 Outbox 里存的 JSON 原文，
 *       不希望框架往里头塞 {@code __TypeId__} 之类的 Java 类型头——那会把"消息格式"绑死在类名上，
 *       改个包名就让积压的旧消息反序列化失败。</li>
 *   <li><b>等确认而不是"发完就算"</b>：channel 是异步的，{@code send} 返回时消息可能还在客户端缓冲里。
 *       只有 {@code publisher-confirm-type: correlated} 回来的 ack 才代表 broker 收下了。</li>
 *   <li><b>连"退信"一起当失败</b>：交换机收到了但没有任何队列匹配路由键时，broker 会 ack 并把消息退回来。
 *       这时候如果只看 ack，就会把一条没人收的消息标成投递成功——索引就这么静默缺一条。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitOutboxMessageSender implements OutboxMessageSender {

    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;

    @PostConstruct
    void registerReturnsCallback() {
        // 注册了 ReturnsCallback，RabbitTemplate 才会把退信挂到 CorrelationData 上（否则只打一条日志）
        rabbitTemplate.setReturnsCallback(this::onReturned);
    }

    @Override
    public boolean send(OutboxMessage message) throws Exception {
        CorrelationData correlation = new CorrelationData(message.messageKey());

        MessageProperties headers = new MessageProperties();
        headers.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        headers.setContentEncoding(StandardCharsets.UTF_8.name());
        headers.setMessageId(message.messageKey());
        // AMQP 自带的 type 字段直接承载事件类型，消费端不必再解一遍业务字段
        headers.setType(message.eventType());
        headers.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        byte[] body = (message.payloadJson() == null ? "{}" : message.payloadJson())
                .getBytes(StandardCharsets.UTF_8);
        rabbitTemplate.send(message.exchange(), message.routingKey(), new Message(body, headers), correlation);

        CorrelationData.Confirm confirm = correlation.getFuture()
                .get(properties.getConfirmTimeoutMs(), TimeUnit.MILLISECONDS);

        if (!confirm.isAck()) {
            log.warn("消息被 broker 拒收 key={}：{}", message.messageKey(), confirm.getReason());
            return false;
        }
        if (correlation.getReturned() != null) {
            log.warn("消息没有匹配到任何队列 key={} routingKey={}", message.messageKey(), message.routingKey());
            return false;
        }
        return true;
    }

    private void onReturned(ReturnedMessage returned) {
        log.warn("消息被退回 replyCode={} replyText={} exchange={} routingKey={}",
                returned.getReplyCode(), returned.getReplyText(),
                returned.getExchange(), returned.getRoutingKey());
    }
}
