package com.novel.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.infra.mq.ConsumeLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConsumeLogMapper extends BaseMapper<ConsumeLogEntity> {
}
