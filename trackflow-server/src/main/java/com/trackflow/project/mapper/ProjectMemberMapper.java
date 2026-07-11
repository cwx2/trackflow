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
}
