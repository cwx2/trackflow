package com.trackflow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.project.entity.ProjectMember;
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
}
