package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.persistence.entity.ChapterEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChapterMapper extends BaseMapper<ChapterEntity> {
}
