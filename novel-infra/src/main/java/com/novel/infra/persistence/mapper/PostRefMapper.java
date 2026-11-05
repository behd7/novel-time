package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.persistence.entity.PostRefEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostRefMapper extends BaseMapper<PostRefEntity> {
}
