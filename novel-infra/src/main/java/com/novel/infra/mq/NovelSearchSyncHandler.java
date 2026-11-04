package com.novel.infra.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.search.port.NovelSearchPort;
import com.novel.infra.search.NovelDocAssembler;
import com.novel.infra.search.SearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * 索引同步的消费逻辑（与 MQ 无关的那一半）。
 *
 * <p>拆出来是为了可测：监听器只管 ack/nack，真正的"读库 → 写索引 → 记流水 → 重试"
 * 全在这里，没有 broker 也能用单测覆盖。
 *
 * <p>幂等分两层：
 * <ol>
 *   <li><b>索引端</b>：写入带外部版本号，重复写同一版本不会产生第二份效果；</li>
 *   <li><b>流水端</b>：同一 (messageKey, consumer) 只记一次，用来识别重复投递。</li>
 * </ol>
 * 注意顺序是"先写索引、再记流水"。反过来会有一个致命缺口：流水先提交、
 * 索引写失败时，重试会被唯一键判成"已消费"直接跳过，那条数据就永久缺了。
 * 而"索引写成功但流水没记上"最坏只是多写一次索引，版本号会让它变成无害的空操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NovelSearchSyncHandler {

    /** 流水里的消费者标识，同一个队列以后可能有别的消费者，靠它区分。 */
    static final String CONSUMER = "search-sync";

    private final ObjectMapper objectMapper;
    private final NovelRepository novelRepository;
    private final NovelSearchPort searchPort;
    private final NovelDocAssembler docAssembler;
    private final ConsumeLogRepository consumeLogRepository;
    private final SearchProperties properties;

    /**
     * 处理一条消息。解析失败直接抛出（毒消息不重试，交给死信），
     * 同步失败按配置的次数退避重试，仍失败则抛出交给监听器 nack。
     */
    public void handle(Message message) throws Exception {
        NovelSearchSyncMessage event = parse(message);

        int[] delays = properties.getRetryDelaysMs();
        for (int attempt = 0; attempt <= delays.length; attempt++) {
            try {
                sync(event);
                return;
            } catch (Exception e) {
                if (attempt == delays.length) {
                    throw e;
                }
                long delay = delays[attempt];
                log.warn("索引同步失败，{}ms 后重试（第 {}/{} 次）key={}：{}",
                        delay, attempt + 1, delays.length, event.messageKey(), e.getMessage());
                Thread.sleep(delay);
            }
        }
    }

    private NovelSearchSyncMessage parse(Message message) throws IOException {
        MessageProperties headers = message.getMessageProperties();
        String messageKey = headers.getMessageId();
        String eventType = headers.getType();

        JsonNode body = objectMapper.readTree(message.getBody());
        long novelId = body.path("novelId").asLong();
        long version = body.path("version").asLong();
        if (messageKey == null || messageKey.isBlank() || novelId <= 0) {
            // 格式不对的消息重试一百次也不会变好，抛出去让监听器直接 nack 进死信
            throw new IllegalArgumentException("消息缺少 messageId 或 novelId，无法同步索引");
        }
        return new NovelSearchSyncMessage(messageKey, eventType, novelId, version);
    }

    private void sync(NovelSearchSyncMessage event) {
        Optional<Novel> novel = novelRepository.findById(event.novelId());
        if (novel.isEmpty() || isDeleted(event.eventType())) {
            // 逻辑删除后 findById 本来就查不到，这里再判一次事件类型，
            // 是为了让"显式下架"即使还没落成 deleted=1 也能把文档摘掉
            searchPort.delete(event.novelId(), event.version());
        } else {
            searchPort.upsert(docAssembler.assemble(novel.get()), event.version());
        }

        if (!consumeLogRepository.markConsumed(event.messageKey(), CONSUMER)) {
            log.info("消息已消费过，索引写入本身幂等，跳过流水 key={}", event.messageKey());
        }
    }

    private boolean isDeleted(String eventType) {
        return eventType != null && eventType.endsWith(".deleted");
    }
}
