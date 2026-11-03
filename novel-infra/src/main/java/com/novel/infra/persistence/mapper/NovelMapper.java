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
}
