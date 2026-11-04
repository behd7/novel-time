package com.novel.infra.mq;

/**
 * 索引同步消息。
 *
 * <p>字段少得可怜，是有意的：消息里只有"哪本书、哪个版本、发生了什么"，
 * 具体内容由消费端回库读。这样即使消息在队列里躺了十分钟，
 * 也不会把十分钟前的旧标题写进索引。
 */
public record NovelSearchSyncMessage(String messageKey, String eventType, long novelId, long version) {
}
