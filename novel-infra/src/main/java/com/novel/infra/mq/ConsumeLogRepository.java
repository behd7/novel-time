package com.novel.infra.mq;

import com.novel.infra.persistence.mapper.ConsumeLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 消费流水。
 *
 * <p>唯一键 {@code (message_key, consumer)} 是"这条消息这个消费者处理过了"的唯一凭据。
 * 重复投递时插不进去，据此可以跳过重复处理。
 *
 * <p>但要说清楚：这个表是**审计与去重**，不是正确性的最后一道闸。
 * 真正保证重复消费无害的是索引端的版本号——即使流水插入失败、消息又重放了一遍，
 * 同样的文档写两次结果也一样。两件事不能互相替代：
 * 只靠流水，则"写完索引、记流水之前进程被 kill"会导致这条消息被永久跳过；
 * 只靠版本号，则查不出"到底消费没消费过"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumeLogRepository {

    private final ConsumeLogMapper consumeLogMapper;

    /**
     * 记一条消费流水。
     *
     * @return true 表示这是第一次消费；false 表示已经消费过（唯一键冲突）
     */
    public boolean markConsumed(String messageKey, String consumer) {
        ConsumeLogEntity entity = new ConsumeLogEntity();
        entity.setMessageKey(messageKey);
        entity.setConsumer(consumer);
        try {
            consumeLogMapper.insert(entity);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
