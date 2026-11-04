package com.novel.infra.event;

import com.novel.core.event.NovelChangeType;
import com.novel.core.event.NovelEventPublisher;
import com.novel.infra.persistence.mapper.NovelMapper;
import com.novel.infra.persistence.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 把"某本书变了"落成一条 Outbox 记录。
 *
 * <p>这里是整条链路里唯一写 {@code outbox} 的地方，也是唯一读取同步版本号的地方。
 * 版本号在业务 UPDATE 里已经自增过，所以同一个事务里读到的一定是这次变更之后的版本。
 *
 * <p>消息键取 {@code 事件类型:书号:版本}，依靠 {@code uk_message_key} 去重：
 * 同一个版本被发两次（重试、并发调用）时第二次插不进去，直接忽略即可，
 * 它的语义就是"这次变更已经登记过了"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher implements NovelEventPublisher {

    private final OutboxMapper outboxMapper;
    private final NovelMapper novelMapper;

    @Override
    public void novelChanged(long novelId, NovelChangeType type) {
        Long version = novelMapper.selectVersion(novelId);
        if (version == null) {
            // 物理行都不在了，没有可同步的东西；这属于异常路径，留个日志别静默
            log.warn("事件登记失败：小说不存在 novelId={} type={}", novelId, type);
            return;
        }

        String eventType = "novel.search." + type.routingSuffix();
        String messageKey = eventType + ":" + novelId + ":" + version;

        OutboxEntity row = new OutboxEntity();
        row.setMessageKey(messageKey);
        row.setEventType(eventType);
        row.setAggregateType("novel");
        row.setAggregateId(novelId);
        // 只带 id 与版本，不带任何业务字段：消费端回库读最新值，
        // 消息体就不会因为"发出去之后又被改了一次"而过期。
        row.setPayload("{\"novelId\":" + novelId + ",\"version\":" + version + "}");
        row.setStatus(0);
        row.setRetryCount(0);
        // 截到秒：next_retry_at 是秒级 DATETIME，MySQL 会把带纳秒的值四舍五入，
        // 于是"现在就该投递"会变成"最多 1 秒后才可投递"，轮询看起来像卡了半天。
        row.setNextRetryAt(LocalDateTime.now().withNano(0));

        try {
            outboxMapper.insert(row);
        } catch (DuplicateKeyException e) {
            log.debug("同一版本的事件已登记过，忽略 key={}", messageKey);
        }
    }
}
