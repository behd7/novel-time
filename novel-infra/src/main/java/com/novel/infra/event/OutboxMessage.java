package com.novel.infra.event;

/**
 * 一条待投递的消息。
 *
 * <p>用它把"Outbox 表里的一行"翻译成"要发出去的一条消息"，是为了让投递任务不认识 AMQP：
 * 任务只管"发出去、等结果、改状态"，具体走 Rabbit 还是 Kafka 由实现决定。
 * 单元测试里换成一个内存实现，就能在没有 broker 的情况下验证退避与状态流转。
 */
public record OutboxMessage(
        String exchange,
        String routingKey,
        String messageKey,
        String eventType,
        String payloadJson
) {
}
