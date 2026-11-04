package com.novel.infra.mq;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** {@code mq_consume_log} 表映射：消费幂等流水。 */
@Data
@TableName("mq_consume_log")
public class ConsumeLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageKey;
    private String consumer;
    private LocalDateTime createdAt;
}
