package com.novel.infra.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code outbox} 表映射。
 *
 * <p>{@code payload} 用 String 而不是结构化对象：这一列是 MySQL 的 JSON 类型，
 * 存的是"消费端契约"的原文。映射成对象反而会在读写之间多一次序列化，
 * 将来换成别的语言写消费者时还得跟着改。
 */
@Data
@TableName("outbox")
public class OutboxEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageKey;
    private String eventType;
    private String aggregateType;
    private Long aggregateId;
    private String payload;

    /** 0=待投递 1=已投递 2=投递失败 */
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
