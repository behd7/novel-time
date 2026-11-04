package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.persistence.entity.UserStatEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserStatMapper extends BaseMapper<UserStatEntity> {

    @Update("UPDATE user_stat SET review_count = GREATEST(review_count + #{delta}, 0) WHERE user_id = #{userId}")
    int addReviewCount(@Param("userId") long userId, @Param("delta") int delta);
}
