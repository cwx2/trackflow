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
     * 路径: user_group_member → user_group_role (project_id IS NULL) → role_permission
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN user_group_role ugr ON ugr.role_id = rp.role_id
            INNER JOIN user_group_member ugm ON ugm.group_id = ugr.group_id
            WHERE ugm.user_id = #{userId}
              AND ugr.project_id IS NULL
            """)
    List<String> selectGlobalPermissionsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户通过组继承的项目级权限
     * 路径: user_group_member → user_group_role (project_id = ?) → role_permission
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN user_group_role ugr ON ugr.role_id = rp.role_id
            INNER JOIN user_group_member ugm ON ugm.group_id = ugr.group_id
            WHERE ugm.user_id = #{userId}
              AND ugr.project_id = #{projectId}
            """)
    List<String> selectProjectPermissionsByUserAndProject(@Param("userId") Long userId, @Param("projectId") Long projectId);

    /**
     * 查询用户通过组继承的所有项目级权限（不限项目）
     * 用于 hasPermissionInAnyProject 检查
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN user_group_role ugr ON ugr.role_id = rp.role_id
            INNER JOIN user_group_member ugm ON ugm.group_id = ugr.group_id
            WHERE ugm.user_id = #{userId}
              AND ugr.project_id IS NOT NULL
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
}
