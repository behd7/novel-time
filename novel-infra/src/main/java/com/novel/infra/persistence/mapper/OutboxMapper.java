package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.event.OutboxEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxMapper extends BaseMapper<OutboxEntity> {

    /**
     * 取一批待投递记录并锁住。
     *
     * <p>两条关键子句，缺一不可：
     * <ul>
     *   <li>{@code FOR UPDATE}：从"选中"到"改状态"之间不允许别的实例再选中同一批，
     *       否则多实例部署时同一条消息会被发两次（消费端虽然幂等，但重试计数会被搞乱）。</li>
     *   <li>{@code SKIP LOCKED}：别人正在处理的行直接跳过，而不是排队等锁。
     *       没有它，两个实例会互相等待，吞吐直接对半砍。</li>
     * </ul>
     *
     * <p>{@code next_retry_at} 是退避的落点：投递失败的行会被推到未来某个时刻，
     * 在这之前轮询扫不到它，天然实现了"指数退避"而不用在内存里记状态。
     */
    @Select("""
            SELECT *
              FROM outbox
             WHERE status = 0
               AND next_retry_at <= #{now}
             ORDER BY id
             LIMIT #{limit}
             FOR UPDATE SKIP LOCKED
            """)
    List<OutboxEntity> claimBatch(@Param("limit") int limit, @Param("now") LocalDateTime now);

    @Update("UPDATE outbox SET status = 1 WHERE id = #{id}")
    int markSent(@Param("id") long id);

    @Update("""
            UPDATE outbox
               SET status = #{status},
                   retry_count = #{retryCount},
                   next_retry_at = #{nextRetryAt}
             WHERE id = #{id}
            """)
    int markFailed(@Param("id") long id,
                   @Param("status") int status,
                   @Param("retryCount") int retryCount,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt);

    /**
     * 重建收尾用：查某个水位之后发生过变更的书。
     *
     * <p>重建要顺序扫描全表，扫完到切换别名之间必然有一段窗口，
     * 这期间发生的更新会落到旧索引上。把这段时间内产生过 outbox 的书再补写一次，
     * 窗口就从"整段重建时长"缩到"最后一次查询到切换之间"。
     *
     * <p>水位用自增 id 而不是时间：{@code created_at} 是数据库默认值（取数据库时区），
     * 应用侧算出来的时间取的是应用时区，两者在容器里能差 8 小时，用时间比对会漏掉整批数据。
     */
    @Select("""
            SELECT DISTINCT aggregate_id
              FROM outbox
             WHERE aggregate_type = 'novel'
               AND id > #{afterId}
            """)
    List<Long> findAggregateIdsChangedAfter(@Param("afterId") long afterId);

    /** 重建开始前记下的水位：此刻之后新登记的变更才需要补写。 */
    @Select("SELECT COALESCE(MAX(id), 0) FROM outbox")
    long selectMaxId();
}
