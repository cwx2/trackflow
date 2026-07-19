package com.trackflow.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.workflow.entity.WorkflowRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工作流规则 Mapper
 */
@Mapper
public interface WorkflowRuleMapper extends BaseMapper<WorkflowRule> {

    /**
     * 查询匹配指定项目和触发事件的已启用规则（项目级 + 全局级）
     */
    @Select("""
        SELECT * FROM workflow_rule
        WHERE enabled = true
          AND trigger_event = #{triggerEvent}
          AND (project_id = #{projectId} OR project_id IS NULL)
        ORDER BY sort_order ASC, id ASC
        """)
    List<WorkflowRule> findEnabledRules(@Param("projectId") Long projectId,
                                        @Param("triggerEvent") String triggerEvent);

    /**
     * 查询指定项目的所有规则（含全局），用于管理页面
     */
    @Select("""
        SELECT * FROM workflow_rule
        WHERE project_id = #{projectId} OR project_id IS NULL
        ORDER BY sort_order ASC, id ASC
        """)
    List<WorkflowRule> findByProjectIncludeGlobal(@Param("projectId") Long projectId);
}
