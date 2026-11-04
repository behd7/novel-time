package com.novel.infra.event;

/**
 * 把一条消息送出去的最小抽象。
 *
 * <p>返回值只有两种：broker 确认收下（true）或者没有（false / 抛异常）。
 * "确认"这个词很重要——只要消息有可能没真正落到 broker 上，就不能把 Outbox 行标成已投递，
 * 否则这条变更就永远丢了。
 */
public interface OutboxMessageSender {

    boolean send(OutboxMessage message) throws Exception;
}
