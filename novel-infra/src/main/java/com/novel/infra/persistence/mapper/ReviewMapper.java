package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.persistence.entity.ReviewEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReviewMapper extends BaseMapper<ReviewEntity> {

    @Update("UPDATE novel_review SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{id}")
    int addLikeCount(@Param("id") long id, @Param("delta") int delta);

    @Update("UPDATE novel_review SET reply_count = GREATEST(reply_count + #{delta}, 0) WHERE id = #{id}")
    int addReplyCount(@Param("id") long id, @Param("delta") int delta);
}
