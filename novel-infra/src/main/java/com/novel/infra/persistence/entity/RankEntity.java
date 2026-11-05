package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@code novel_rank} 表映射。
 *
 * <p>这张表没有 {@code deleted}、没有 {@code updated_at}：它存的是快照，不是实体。
 * 每次结算整份替换，旧行直接消失，所以不存在"软删除"和"改过几次"的概念。
 */
@Data
@TableName("novel_rank")
public class RankEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** daily / weekly / monthly / all，与 {@code RankPeriod} 的枚举名小写一致。 */
    private String period;

    /** 周期起点：日榜是当天，周榜是本周一，月榜是 1 号，总榜是 1970-01-01 哨兵。 */
    private LocalDate rankDate;

    private Long novelId;

    /** 名次，从 1 开始，同一份快照内连续不跳号。 */
    private Integer rankNo;

    private BigDecimal score;

    private LocalDateTime createdAt;
}
