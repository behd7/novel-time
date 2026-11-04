package com.novel.infra.mq;

import com.novel.infra.config.RabbitConfig;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 索引同步队列的监听器。
 *
 * <p>只做三件事：交给 {@link NovelSearchSyncHandler}、成功 ack、失败 nack 且**不重回队列**。
 *
 * <p>为什么不 requeue：容器已经配了 {@code default-requeue-rejected: false}，
 * 这里也显式传 {@code false}。消息一旦重回队列会被立刻再次投给同一个消费者，
 * 一条毒消息就能把 CPU 打满、还会把后面的消息全堵住。失败的消息进死信队列留现场，
 * 修好之后从死信里重放，比让它无限循环体面得多。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "novel.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NovelSearchSyncConsumer {

    private final NovelSearchSyncHandler handler;

    @RabbitListener(queues = RabbitConfig.QUEUE_SEARCH_SYNC)
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            handler.handle(message);
            // 手动确认必须在"索引真的写完"之后，否则进程中途挂掉这条文档就永久丢了
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("索引同步失败，转入死信队列 key={}：{}",
                    message.getMessageProperties().getMessageId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
