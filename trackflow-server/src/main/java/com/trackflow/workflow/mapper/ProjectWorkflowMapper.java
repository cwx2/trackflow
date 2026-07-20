package com.trackflow.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.workflow.entity.ProjectWorkflow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectWorkflowMapper extends BaseMapper<ProjectWorkflow> {

    /**
     * 获取绑定了指定工作流定义的项目 ID 列表
     */
    @Select("SELECT project_id FROM project_workflow WHERE workflow_definition_id = #{definitionId}")
    List<Long> selectProjectIdsByDefinitionId(@Param("definitionId") Long definitionId);

    /**
     * 获取指定项目绑定的工作流定义 ID 列表
     */
    @Select("SELECT workflow_definition_id FROM project_workflow WHERE project_id = #{projectId}")
    List<Long> selectDefinitionIdsByProjectId(@Param("projectId") Long projectId);
}
