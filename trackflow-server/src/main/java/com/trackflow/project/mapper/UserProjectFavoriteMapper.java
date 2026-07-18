package com.trackflow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.project.entity.UserProjectFavorite;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserProjectFavoriteMapper extends BaseMapper<UserProjectFavorite> {

    /**
     * 查询用户收藏的所有项目ID
     */
    @Select("SELECT project_id FROM user_project_favorite WHERE user_id = #{userId}")
    List<Long> selectFavoriteProjectIds(@Param("userId") Long userId);

    /**
     * 删除指定用户对指定项目的收藏
     */
    @Delete("DELETE FROM user_project_favorite WHERE user_id = #{userId} AND project_id = #{projectId}")
    int deleteFavorite(@Param("userId") Long userId, @Param("projectId") Long projectId);

    /**
     * 检查用户是否已收藏指定项目
     */
    @Select("SELECT COUNT(1) FROM user_project_favorite WHERE user_id = #{userId} AND project_id = #{projectId}")
    int countByUserAndProject(@Param("userId") Long userId, @Param("projectId") Long projectId);
}
