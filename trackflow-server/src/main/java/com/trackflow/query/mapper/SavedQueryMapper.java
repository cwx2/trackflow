package com.trackflow.query.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.query.entity.SavedQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SavedQueryMapper extends BaseMapper<SavedQuery> {
}
