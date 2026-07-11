package com.trackflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.system.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 查询拥有指定角色的所有用户 ID
     */
    @Select("SELECT user_id FROM user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
}
