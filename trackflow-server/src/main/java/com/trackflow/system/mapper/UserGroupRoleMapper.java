package com.trackflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.system.entity.UserGroupRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserGroupRoleMapper extends BaseMapper<UserGroupRole> {

    /**
     * 查询用户通过组继承的全局权限
     * 路径: user_group_member → user_group_role (project_id IS NULL + role.role_type='global') → role_permission
     * 仅返回 scope='global' 的权限——全局路径不应获得项目级权限
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN user_group_role ugr ON ugr.role_id = rp.role_id
            INNER JOIN user_group_member ugm ON ugm.group_id = ugr.group_id
            INNER JOIN sys_role sr ON sr.id = ugr.role_id
            INNER JOIN sys_permission sp ON sp.code = rp.permission
            WHERE ugm.user_id = #{userId}
              AND ugr.project_id IS NULL
              AND sr.role_type = 'global'
              AND sp.scope = 'global'
            """)
    List<String> selectGlobalPermissionsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户通过组继承的项目级权限（含"全局作用域"的项目角色）
     * 匹配两种情况：
     * 1. ugr.project_id = 指定项目 ID（精确绑定）
     * 2. ugr.project_id IS NULL 且 role_type = 'project'（全局作用域，对所有项目生效）
     * 仅返回 scope='project' 的权限——项目级分配时全局权限不应生效
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN user_group_role ugr ON ugr.role_id = rp.role_id
            INNER JOIN user_group_member ugm ON ugm.group_id = ugr.group_id
            INNER JOIN sys_role sr ON sr.id = ugr.role_id
            INNER JOIN sys_permission sp ON sp.code = rp.permission
            WHERE ugm.user_id = #{userId}
              AND (
                ugr.project_id = #{projectId}
                OR (ugr.project_id IS NULL AND sr.role_type = 'project')
              )
              AND sp.scope = 'project'
            """)
    List<String> selectProjectPermissionsByUserAndProject(@Param("userId") Long userId, @Param("projectId") Long projectId);

    /**
     * 查询用户通过组继承的所有项目级权限（不限项目）
     * 包含精确绑定项目的角色 + 全局作用域的项目角色
     * 仅返回 scope='project' 的权限
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN user_group_role ugr ON ugr.role_id = rp.role_id
            INNER JOIN user_group_member ugm ON ugm.group_id = ugr.group_id
            INNER JOIN sys_role sr ON sr.id = ugr.role_id
            INNER JOIN sys_permission sp ON sp.code = rp.permission
            WHERE ugm.user_id = #{userId}
              AND sr.role_type = 'project'
              AND sp.scope = 'project'
            """)
    List<String> selectAllProjectPermissionsByUserId(@Param("userId") Long userId);

    /**
     * 查询组的所有角色分配中涉及的用户 ID（用于缓存失效）
     */
    @Select("""
            SELECT DISTINCT ugm.user_id
            FROM user_group_member ugm
            WHERE ugm.group_id = #{groupId}
            """)
    List<Long> selectUserIdsByGroupId(@Param("groupId") Long groupId);

    /**
     * 查询拥有指定角色的所有组 ID（用于角色权限变更时的缓存失效）
     */
    @Select("""
            SELECT DISTINCT ugr.group_id
            FROM user_group_role ugr
            WHERE ugr.role_id = #{roleId}
            """)
    List<Long> selectGroupIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询通过组继承指定角色的所有用户 ID（用于角色权限变更时的缓存失效）
     * 路径: user_group_role (role_id) → user_group_member (group_id → user_id)
     */
    @Select("""
            SELECT DISTINCT ugm.user_id
            FROM user_group_member ugm
            INNER JOIN user_group_role ugr ON ugr.group_id = ugm.group_id
            WHERE ugr.role_id = #{roleId}
            """)
    List<Long> selectUserIdsByRoleIdViaGroup(@Param("roleId") Long roleId);

    /**
     * 查询指定项目中通过用户组获得角色的所有组角色分配记录
     * 仅查询精确绑定到项目的记录（project_id = 指定值）
     */
    @Select("""
            SELECT ugr.*
            FROM user_group_role ugr
            WHERE ugr.project_id = #{projectId}
            """)
    List<UserGroupRole> selectGroupRolesByProjectId(@Param("projectId") Long projectId);

    /**
     * 查询用户通过组获得的所有项目 ID（用于项目列表可见性）
     * 路径: user_group_member(user_id) → user_group_role(group_id, project_id IS NOT NULL)
     * 仅返回精确绑定到项目的角色分配（不含全局作用域）
     */
    @Select("""
            SELECT DISTINCT ugr.project_id
            FROM user_group_role ugr
            INNER JOIN user_group_member ugm ON ugm.group_id = ugr.group_id
            WHERE ugm.user_id = #{userId}
              AND ugr.project_id IS NOT NULL
            """)
    List<Long> selectProjectIdsByUserIdViaGroups(@Param("userId") Long userId);

    /**
     * 查询用户通过组继承拥有指定权限的项目 ID 列表
     * 包含精确绑定项目的角色 + 全局作用域的项目角色（全局作用域时返回所有项目 ID）
     * 用于数据范围过滤
     */
    @Select("""
            SELECT DISTINCT ugr.project_id
            FROM user_group_role ugr
            INNER JOIN user_group_member ugm ON ugm.group_id = ugr.group_id
            INNER JOIN role_permission rp ON rp.role_id = ugr.role_id
            INNER JOIN sys_role sr ON sr.id = ugr.role_id
            INNER JOIN sys_permission sp ON sp.code = rp.permission
            WHERE ugm.user_id = #{userId}
              AND rp.permission = #{permission}
              AND ugr.project_id IS NOT NULL
              AND sr.role_type = 'project'
              AND sp.scope = 'project'
            """)
    List<Long> selectProjectIdsWithPermissionViaGroups(@Param("userId") Long userId, @Param("permission") String permission);

    /**
     * 检查用户是否通过组继承了全局作用域的项目角色中的指定权限
     * 全局作用域 = ugr.project_id IS NULL AND role_type = 'project'
     * 如果有，说明该用户在所有项目中都拥有该权限
     */
    @Select("""
            SELECT EXISTS(
                SELECT 1
                FROM user_group_role ugr
                INNER JOIN user_group_member ugm ON ugm.group_id = ugr.group_id
                INNER JOIN role_permission rp ON rp.role_id = ugr.role_id
                INNER JOIN sys_role sr ON sr.id = ugr.role_id
                INNER JOIN sys_permission sp ON sp.code = rp.permission
                WHERE ugm.user_id = #{userId}
                  AND rp.permission = #{permission}
                  AND ugr.project_id IS NULL
                  AND sr.role_type = 'project'
                  AND sp.scope = 'project'
                LIMIT 1
            )
            """)
    boolean hasGlobalScopePermissionViaGroups(@Param("userId") Long userId, @Param("permission") String permission);
}
