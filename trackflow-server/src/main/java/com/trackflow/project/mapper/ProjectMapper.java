package com.trackflow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
