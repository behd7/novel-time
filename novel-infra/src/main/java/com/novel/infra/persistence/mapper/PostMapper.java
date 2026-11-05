package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.persistence.entity.PostEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<PostEntity> {

    @Update("UPDATE post SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int addViewCount(@Param("id") long id);

    @Update("UPDATE post SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{id}")
    int addLikeCount(@Param("id") long id, @Param("delta") int delta);

    @Update("UPDATE post SET comment_count = GREATEST(comment_count + #{delta}, 0) WHERE id = #{id}")
    int addCommentCount(@Param("id") long id, @Param("delta") int delta);
}
