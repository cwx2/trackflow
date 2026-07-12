package com.trackflow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 查询指定项目中 issue_key 序号的实际最大值
     */
    @Select("SELECT MAX(CAST(SUBSTRING(issue_key FROM '([0-9]+)$') AS INT)) FROM issue WHERE project_id = #{projectId} AND deleted_at IS NULL AND issue_key ~ '^[A-Z0-9]+-[0-9]+$'")
    Integer selectMaxIssueSequence(@Param("projectId") Long projectId);
}
