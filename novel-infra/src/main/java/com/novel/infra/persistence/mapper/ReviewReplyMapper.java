package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.persistence.entity.ReviewReplyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReviewReplyMapper extends BaseMapper<ReviewReplyEntity> {

    @Update("UPDATE review_reply SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{id}")
    int addLikeCount(@Param("id") long id, @Param("delta") int delta);
}
