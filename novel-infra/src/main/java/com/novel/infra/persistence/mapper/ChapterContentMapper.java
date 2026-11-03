package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.persistence.entity.ChapterContentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChapterContentMapper extends BaseMapper<ChapterContentEntity> {
}
