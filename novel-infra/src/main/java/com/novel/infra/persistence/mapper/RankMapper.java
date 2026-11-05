package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.persistence.entity.RankEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RankMapper extends BaseMapper<RankEntity> {

    /**
     * 一条 SQL 插完整份快照。
     *
     * <p>逐条 {@code insert} 是 200 次往返，而结算每 10 分钟就要跑一次、一次四个周期。
     * 单条多值 INSERT 只有一次往返，且失败时整批回滚，不会留下"插了一半"的快照。
     */
    @Insert("""
            <script>
            INSERT INTO novel_rank (period, rank_date, novel_id, rank_no, score)
            VALUES
            <foreach collection="entries" item="e" separator=",">
                (#{e.period}, #{e.rankDate}, #{e.novelId}, #{e.rankNo}, #{e.score})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("entries") List<RankEntity> entries);
}
