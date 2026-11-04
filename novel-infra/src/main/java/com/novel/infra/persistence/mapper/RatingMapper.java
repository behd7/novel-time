package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.persistence.entity.RatingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface RatingMapper extends BaseMapper<RatingEntity> {

    /** 星级分布，一页直方图只需要一次查询。 */
    @Select("SELECT score AS score, COUNT(*) AS cnt FROM novel_rating "
            + "WHERE novel_id = #{novelId} GROUP BY score")
    List<Map<String, Object>> distribution(@Param("novelId") long novelId);

    /** 详情核对账用：从明细算出总分与人数。 */
    @Select("SELECT COALESCE(SUM(score), 0) AS total, COUNT(*) AS cnt "
            + "FROM novel_rating WHERE novel_id = #{novelId}")
    Map<String, Object> aggregate(@Param("novelId") long novelId);

    /** 对账任务分批遍历，避免一次性把全表 id 读进内存。 */
    @Select("SELECT DISTINCT novel_id FROM novel_rating WHERE novel_id > #{lastId} "
            + "ORDER BY novel_id LIMIT #{limit}")
    List<Long> pageNovelIds(@Param("lastId") long lastId, @Param("limit") int limit);
}
