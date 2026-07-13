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
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN project_member pm ON pm.role_id = rp.role_id
            WHERE pm.user_id = #{userId} AND pm.project_id = #{projectId}
            """)
    List<String> selectPermissionsByUserAndProject(@Param("userId") Long userId, @Param("projectId") Long projectId);

    /**
     * 检查用户是否在任何项目中拥有指定权限
     * 用于导航级别的权限判断（如：是否在任意项目中拥有 project:manage_workflow）
     */
    @Select("""
            SELECT COUNT(1) > 0
            FROM role_permission rp
            INNER JOIN project_member pm ON pm.role_id = rp.role_id
            WHERE pm.user_id = #{userId} AND rp.permission = #{permission}
            LIMIT 1
            """)
    boolean hasPermissionInAnyProject(@Param("userId") Long userId, @Param("permission") String permission);
}
