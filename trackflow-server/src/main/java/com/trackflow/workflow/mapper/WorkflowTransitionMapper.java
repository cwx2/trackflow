package com.trackflow.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.workflow.entity.WorkflowTransition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkflowTransitionMapper extends BaseMapper<WorkflowTransition> {

    /**
     * 查找允许的状态转换
     * 优先查项目级，如无则 fallback 到全局（project_id IS NULL）
     * 支持精确 issue_type 匹配或通配符 '*'
     */
    @Select("""
            SELECT DISTINCT new_status_id FROM workflow_transition
            WHERE (project_id = #{projectId} OR project_id IS NULL)
              AND (issue_type = #{issueType} OR issue_type = '*')
              AND role_id IN (${roleIds})
              AND old_status_id = #{oldStatusId}
            ORDER BY new_status_id
            """)
    List<Long> findAllowedNewStatusIds(@Param("projectId") Long projectId,
                                       @Param("issueType") String issueType,
                                       @Param("roleIds") String roleIds,
                                       @Param("oldStatusId") Long oldStatusId);

    /**
     * 查找指定角色可以发起转换的所有源状态 ID（即有出边的状态）
     * 用于看板等场景预判卡片是否可拖拽
     */
    @Select("""
            SELECT DISTINCT old_status_id FROM workflow_transition
            WHERE (project_id = #{projectId} OR project_id IS NULL)
              AND role_id IN (${roleIds})
            """)
    List<Long> findTransitionableSourceStatusIds(@Param("projectId") Long projectId,
                                                 @Param("roleIds") String roleIds);

    /**
     * 检查指定状态在某个 issueType 的工作流中是否存在（作为 old_status 或 new_status）
     * 用于类型变更后判断当前状态是否仍然合法
     */
    @Select("""
            SELECT COUNT(*) FROM workflow_transition
            WHERE (project_id = #{projectId} OR project_id IS NULL)
              AND (issue_type = #{issueType} OR issue_type = '*')
              AND (old_status_id = #{statusId} OR new_status_id = #{statusId})
            """)
    int countStatusInWorkflow(@Param("projectId") Long projectId,
                              @Param("issueType") String issueType,
                              @Param("statusId") Long statusId);
}
