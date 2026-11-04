package com.novel.infra.event;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox 投递参数。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "novel.outbox")
public class OutboxProperties {

    /**
     * 是否启动投递任务。
     *
     * <p>导入进程（{@code import} profile）会把它关掉：那是个短命进程，
     * 确认回调还没回来 {@code System.exit} 就把 JVM 收了，容易留下
     * "消息发出去了但状态没改成已投递"的行。交给常驻服务投递更干净，
     * 反正这些行在库里放着不会丢。
     */
    private boolean enabled = true;

    /** 轮询间隔（毫秒）。 */
    private long pollIntervalMs = 1000;

    /** 每批最多投递多少条。 */
    private int batchSize = 100;

    /** 最大尝试次数，超过就置为投递失败并留在库里等人工处理。 */
    private int maxAttempts = 6;

    /** 等待 broker 确认的超时（毫秒）。 */
    private long confirmTimeoutMs = 3000;
}
