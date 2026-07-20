package com.trackflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.system.entity.GlobalMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 全局项目角色分配 Mapper
 */
@Mapper
public interface GlobalMemberMapper extends BaseMapper<GlobalMember> {

    /**
     * 查询所有全局分配记录（用于新项目创建时批量插入 project_member）
     */
    @Select("SELECT user_id, role_id FROM global_member")
    List<GlobalMember> selectAllGlobalMembers();

    /**
     * 查询指定用户的全局角色 ID 列表
     */
    @Select("SELECT role_id FROM global_member WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询拥有指定角色的全局分配用户 ID 列表
     */
    @Select("SELECT user_id FROM global_member WHERE role_id = #{roleId}")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询用户在指定项目中通过全局分配获得的权限
     * （全局分配的角色等效于在每个项目中都有该角色）
     */
    @Select("""
            SELECT DISTINCT rp.permission
            FROM role_permission rp
            INNER JOIN global_member gm ON gm.role_id = rp.role_id
            WHERE gm.user_id = #{userId}
            """)
    List<String> selectGlobalMemberPermissions(@Param("userId") Long userId);

    /**
     * 检查用户是否有指定角色的全局分配
     */
    @Select("SELECT COUNT(1) FROM global_member WHERE user_id = #{userId} AND role_id = #{roleId}")
    int countByUserAndRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
