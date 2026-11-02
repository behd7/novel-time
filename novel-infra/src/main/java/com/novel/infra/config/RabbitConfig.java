package com.novel.infra.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明。
 *
 * <p>三个业务队列 + 一个死信队列：
 * <ul>
 *   <li>{@code q.novel.search.sync} —— 小说/章节变更后同步 ES 索引</li>
 *   <li>{@code q.novel.rank.agg}   —— 阅读/收藏等行为累加到榜单 ZSET</li>
 *   <li>{@code q.novel.stat.agg}   —— 用户统计、评分聚合的自愈计算</li>
 * </ul>
 *
 * <p>为什么一定要有死信队列：消息处理抛异常时，如果没有 DLX，
 * RabbitMQ 只会在客户端无限 requeue 或直接丢弃，两种结果都很糟——
 * 前者把 CPU 打满，后者静默丢数据。有死信队列，坏消息至少留了现场，可以人工重放。
 *
 * <p>注意：队列名带 {@code q.} 前缀、路由键带 {@code novel.} 前缀，
 * 是为了在 RabbitMQ 控制台里一眼能看出这是本项目的资源。
 */
@Configuration
public class RabbitConfig {

    /** 业务事件交换机。 */
    public static final String EXCHANGE_EVENT = "novel.event";
    /** 死信交换机。 */
    public static final String EXCHANGE_DLX = "novel.dlx";

    public static final String QUEUE_SEARCH_SYNC = "q.novel.search.sync";
    public static final String QUEUE_RANK_AGG = "q.novel.rank.agg";
    public static final String QUEUE_STAT_AGG = "q.novel.stat.agg";
    public static final String QUEUE_DEAD = "q.novel.dead";

    public static final String RK_SEARCH = "novel.search.#";
    public static final String RK_RANK = "novel.rank.#";
    public static final String RK_STAT = "novel.stat.#";
    public static final String RK_DEAD = "dead";

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(EXCHANGE_EVENT, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue searchSyncQueue() {
        return durableWithDlx(QUEUE_SEARCH_SYNC);
    }

    @Bean
    public Queue rankAggQueue() {
        return durableWithDlx(QUEUE_RANK_AGG);
    }

    @Bean
    public Queue statAggQueue() {
        return durableWithDlx(QUEUE_STAT_AGG);
    }

    @Bean
    public Queue deadQueue() {
        return QueueBuilder.durable(QUEUE_DEAD).build();
    }

    @Bean
    public Binding searchSyncBinding() {
        return BindingBuilder.bind(searchSyncQueue()).to(eventExchange()).with(RK_SEARCH);
    }

    @Bean
    public Binding rankAggBinding() {
        return BindingBuilder.bind(rankAggQueue()).to(eventExchange()).with(RK_RANK);
    }

    @Bean
    public Binding statAggBinding() {
        return BindingBuilder.bind(statAggQueue()).to(eventExchange()).with(RK_STAT);
    }

    @Bean
    public Binding deadBinding() {
        return BindingBuilder.bind(deadQueue()).to(deadLetterExchange()).with(RK_DEAD);
    }

    /**
     * JSON 序列化。默认的 SimpleMessageConverter 会把对象序列化成 Java 二进制，
     * 在控制台和别的语言写的消费者那里都读不了，还带安全隐患。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    private Queue durableWithDlx(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_DEAD)
                .build();
    }
}
