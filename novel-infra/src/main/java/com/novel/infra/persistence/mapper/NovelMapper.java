package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.persistence.entity.NovelEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NovelMapper extends BaseMapper<NovelEntity> {

    /**
     * 原子的自增，而不是"读出来 +1 再写回去"。
     *
     * <p>读-改-写在并发下会丢更新：两个请求同时读到 100，各自写回 101，实际应该是 102。
     * 交给数据库做 {@code view_count = view_count + ?} 才没有这个问题。
     */
    @Update("UPDATE novel SET view_count = view_count + #{delta} WHERE id = #{id}")
    int addViewCount(@Param("id") long id, @Param("delta") long delta);

    /**
     * 收藏数增减。
     *
     * <p>{@code GREATEST(..., 0)} 是兜底：导入/删除/重复取消收藏等异常路径都可能让计数掉到负数，
     * 而页面上显示"-1 人收藏"会非常难堪。
     */
    @Update("UPDATE novel SET favorite_count = GREATEST(favorite_count + #{delta}, 0) WHERE id = #{id}")
    int addFavoriteCount(@Param("id") long id, @Param("delta") int delta);

    /**
     * 评分聚合的增量维护。
     *
     * <p>写法有个 MySQL 特有的坑：{@code UPDATE} 的多个赋值是**从左到右依次生效**的，
     * 后面的表达式看到的是前面已经改过的值。所以这里先改 sum 与 count，
     * 最后算 avg 时读到的就是新值 —— 不必再手工推算一遍。
     * 如果顺序反过来，avg 会用旧的 sum 算，结果永远差一次更新。
     */
    @Update("""
            UPDATE novel
               SET rating_sum = rating_sum + #{sumDelta},
                   rating_count = GREATEST(rating_count + #{countDelta}, 0),
                   avg_rating = CASE WHEN rating_count = 0 THEN 0
                                     ELSE ROUND(rating_sum / rating_count, 2) END
             WHERE id = #{id}
            """)
    int addRating(@Param("id") long id, @Param("sumDelta") long sumDelta, @Param("countDelta") int countDelta);

    /** 对账任务用：明细重算出来的值直接覆盖。 */
    @Update("""
            UPDATE novel
               SET rating_sum = #{sum},
                   rating_count = #{count},
                   avg_rating = CASE WHEN #{count} = 0 THEN 0
                                     ELSE ROUND(#{sum} / #{count}, 2) END
             WHERE id = #{id}
            """)
    int setRatingStats(@Param("id") long id, @Param("sum") long sum, @Param("count") int count);
}
