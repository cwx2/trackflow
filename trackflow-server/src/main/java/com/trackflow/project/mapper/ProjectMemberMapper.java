package com.trackflow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.result.MemberCountRow;
import com.trackflow.project.mapper.result.TopMemberRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    /**
     * 获取用户有权限的项目 ID 列表
     */
    @Select("SELECT project_id FROM project_member WHERE user_id = #{userId}")
    List<Long> selectProjectIdsByUserId(@Param("userId") Long userId);

    /**
     * 获取用户在指定项目中的角色 ID 列表
     */
    @Select("SELECT role_id FROM project_member WHERE user_id = #{userId} AND project_id = #{projectId}")
    List<Long> selectRoleIdsByUserAndProject(@Param("userId") Long userId, @Param("projectId") Long projectId);

    /**
     * 获取用户在指定项目中的角色代码列表（单次 JOIN 查询，替代 selectRoleIdsByUserAndProject + selectBatchIds 两步操作）
     */
    @Select("""
            SELECT r.code FROM project_member pm
            JOIN sys_role r ON r.id = pm.role_id
            WHERE pm.user_id = #{userId} AND pm.project_id = #{projectId}
            """)
    List<String> selectRoleCodesByUserAndProject(@Param("userId") Long userId, @Param("projectId") Long projectId);

    /**
     * 获取项目中拥有指定权限的成员 user_id 列表（去重）。
     * 用于过滤可分配成员——仅具有 issue:edit 权限的角色成员可被分配工单。
     */
    @Select("""
            SELECT DISTINCT pm.user_id FROM project_member pm
            JOIN role_permission rp ON rp.role_id = pm.role_id
            WHERE pm.project_id = #{projectId} AND rp.permission = #{permission}
            """)
    List<Long> selectUserIdsWithPermission(@Param("projectId") Long projectId, @Param("permission") String permission);

    /**
     * 统计项目中的唯一成员数量（一个用户可能有多角色，只计一次）。
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM project_member WHERE project_id = #{projectId}")
    int countDistinctUsers(@Param("projectId") Long projectId);

    /**
     * 批量统计多个项目的去重成员数量。
     * 返回每个项目的 (projectId, memberCount)，一次查询替代 N 次 countDistinctUsers。
     */
    List<MemberCountRow> countDistinctUsersByProjects(@Param("projectIds") List<Long> projectIds);

    /**
     * 批量获取多个项目各自最早加入的前 topN 名成员（去重）。
     * 使用窗口函数 ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY joined_at)，
     * 并 JOIN sys_user 直接返回 displayName，避免二次查询。
     */
    List<TopMemberRow> selectTopMembersByProjects(@Param("projectIds") List<Long> projectIds, @Param("topN") int topN);

    /**
     * 查询项目中已不再是成员、但仍有工单被分配的历史 assignee 用户 ID 列表。
     * 用于 Assignee 下拉保留历史有效性（参照 YouTrack 行为：移除成员不自动清除 Assignee 候选）。
     */
    @Select("""
            SELECT DISTINCT i.assignee_id FROM issue i
            WHERE i.project_id = #{projectId}
              AND i.assignee_id IS NOT NULL
              AND i.deleted_at IS NULL
              AND i.assignee_id NOT IN (
                  SELECT DISTINCT pm.user_id FROM project_member pm WHERE pm.project_id = #{projectId}
              )
            """)
    List<Long> selectFormerAssigneeUserIds(@Param("projectId") Long projectId);

    /**
     * 获取用户拥有指定权限的项目 ID 列表（通过 project_member → sys_role → role_permission 链查询）。
     * 用于按权限过滤项目列表（如 Sprint 规划页面仅显示有 sprint:view 权限的项目）。
     */
    @Select("""
            SELECT DISTINCT pm.project_id FROM project_member pm
            JOIN role_permission rp ON rp.role_id = pm.role_id
            WHERE pm.user_id = #{userId} AND rp.permission = #{permission}
            """)
    List<Long> selectProjectIdsWithPermission(@Param("userId") Long userId, @Param("permission") String permission);
}
