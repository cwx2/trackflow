package com.trackflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.system.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    /**
     * 查询用户的所有全局权限（通过 user_role → role_permission）
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户在指定项目中的权限（通过 project_member → role_permission）
     * 仅返回 scope='project' 的权限——项目级分配时全局权限不应生效
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN project_member pm ON pm.role_id = rp.role_id
            INNER JOIN sys_permission sp ON sp.code = rp.permission
            WHERE pm.user_id = #{userId} AND pm.project_id = #{projectId}
              AND sp.scope = 'project'
            """)
    List<String> selectPermissionsByUserAndProject(@Param("userId") Long userId, @Param("projectId") Long projectId);

    /**
     * 检查用户是否在任何项目中拥有指定权限
     * 用于导航级别的权限判断（如：是否在任意项目中拥有 project:manage_workflow）
     * 仅检查 scope='project' 的权限
     */
    @Select("""
            SELECT EXISTS(
                SELECT 1
                FROM role_permission rp
                INNER JOIN project_member pm ON pm.role_id = rp.role_id
                INNER JOIN sys_permission sp ON sp.code = rp.permission
                WHERE pm.user_id = #{userId} AND rp.permission = #{permission}
                  AND sp.scope = 'project'
                LIMIT 1
            )
            """)
    boolean hasPermissionInAnyProject(@Param("userId") Long userId, @Param("permission") String permission);

    /**
     * 一次性查询用户在所有项目中的去重权限集合（通过 project_member → role_permission）
     * 用于导航权限聚合计算，替代多次 hasPermissionInAnyProject 串行调用
     * 仅返回 scope='project' 的权限
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN project_member pm ON pm.role_id = rp.role_id
            INNER JOIN sys_permission sp ON sp.code = rp.permission
            WHERE pm.user_id = #{userId}
              AND sp.scope = 'project'
            """)
    List<String> selectAllProjectPermissionsByUserId(@Param("userId") Long userId);

    /**
     * 查询指定角色的项目级权限（用于 NonMember / Anonymous 内置角色）
     * 仅返回 scope='project' 的权限，确保项目级别不会获得全局权限
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN sys_permission sp ON sp.code = rp.permission
            WHERE rp.role_id = #{roleId}
              AND sp.scope = 'project'
            """)
    List<String> selectPermissionsByRoleId(@Param("roleId") Long roleId);
}
