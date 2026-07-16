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
     * 查找允许的状态转换（精确匹配 projectId）。
     * <p>
     * 当 projectId 为 null 时，精确匹配 project_id IS NULL（全局规则）。
     * 当 projectId 非 null 时，精确匹配 project_id = projectId（项目级规则）。
     * <p>
     * issue_type 同理：精确匹配指定值，不含通配符。
     */
    List<Long> findAllowedNewStatusIdsExact(@Param("projectId") Long projectId,
                                            @Param("issueType") String issueType,
                                            @Param("roleIds") List<Long> roleIds,
                                            @Param("oldStatusId") Long oldStatusId);

    /**
     * 查找指定角色可以发起转换的所有源状态 ID（精确匹配 projectId）。
     * 用于看板等场景预判卡片是否可拖拽。
     * <p>
     * 当 projectId 为 null 时，精确匹配 project_id IS NULL。
     * 当 projectId 非 null 时，精确匹配 project_id = projectId。
     */
    List<Long> findTransitionableSourceStatusIdsExact(@Param("projectId") Long projectId,
                                                     @Param("roleIds") List<Long> roleIds);

    /**
     * 检查指定状态在某个 issueType 的工作流中是否存在（作为 old_status 或 new_status）。
     * 用于类型变更后判断当前状态是否仍然合法。
     * <p>
     * 此方法仍使用 OR 合并，因为它是"存在性检查"而非"权限决策"——
     * 只要状态在任何层级的工作流中存在即可。
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
