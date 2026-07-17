package com.trackflow.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.workflow.entity.TransitionAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TransitionActionMapper extends BaseMapper<TransitionAction> {

    /**
     * 查询指定转换路径上所有启用的动作（按 sort_order 升序）。
     * 同时匹配项目级和全局（project_id IS NULL），以及精确类型和通配符 '*'。
     * ActionResolver 负责按优先级从返回结果中筛选最终执行列表。
     */
    @Select("""
            SELECT * FROM transition_action
            WHERE (project_id = #{projectId} OR project_id IS NULL)
              AND (issue_type = #{issueType} OR issue_type = '*')
              AND old_status_id = #{oldStatusId}
              AND new_status_id = #{newStatusId}
              AND enabled = true
            ORDER BY sort_order ASC
            """)
    List<TransitionAction> selectByTransitionPath(@Param("projectId") Long projectId,
                                                  @Param("issueType") String issueType,
                                                  @Param("oldStatusId") Long oldStatusId,
                                                  @Param("newStatusId") Long newStatusId);

    /**
     * 查询创建时（old_status_id IS NULL）的自动化动作。
     * 用于 Issue 创建时触发自动分配规则。
     */
    @Select("""
            SELECT * FROM transition_action
            WHERE (project_id = #{projectId} OR project_id IS NULL)
              AND (issue_type = #{issueType} OR issue_type = '*')
              AND old_status_id IS NULL
              AND new_status_id = #{newStatusId}
              AND enabled = true
            ORDER BY sort_order ASC
            """)
    List<TransitionAction> selectByCreationPath(@Param("projectId") Long projectId,
                                                @Param("issueType") String issueType,
                                                @Param("newStatusId") Long newStatusId);
}
