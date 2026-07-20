package com.trackflow.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.report.entity.DashboardShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 仪表盘共享 Mapper
 */
@Mapper
public interface DashboardShareMapper extends BaseMapper<DashboardShare> {

    /**
     * 查询用户通过直接共享或组共享可访问的仪表盘 ID 列表
     */
    @Select("""
            SELECT DISTINCT ds.dashboard_id FROM dashboard_share ds
            WHERE (ds.target_type = 'user' AND ds.target_id = #{userId})
               OR (ds.target_type = 'group' AND ds.target_id IN (
                   SELECT group_id FROM user_group_member WHERE user_id = #{userId}
               ))
            """)
    List<Long> selectAccessibleDashboardIds(@Param("userId") Long userId);

    /**
     * 检查用户是否对某仪表盘有访问权限（直接或通过组）
     */
    @Select("""
            SELECT COUNT(*) FROM dashboard_share ds
            WHERE ds.dashboard_id = #{dashboardId}
              AND ((ds.target_type = 'user' AND ds.target_id = #{userId})
                OR (ds.target_type = 'group' AND ds.target_id IN (
                    SELECT group_id FROM user_group_member WHERE user_id = #{userId}
                )))
            """)
    int countAccessByUser(@Param("dashboardId") Long dashboardId, @Param("userId") Long userId);

    /**
     * 检查用户是否对某仪表盘有编辑权限
     */
    @Select("""
            SELECT COUNT(*) FROM dashboard_share ds
            WHERE ds.dashboard_id = #{dashboardId}
              AND ds.permission = 'edit'
              AND ((ds.target_type = 'user' AND ds.target_id = #{userId})
                OR (ds.target_type = 'group' AND ds.target_id IN (
                    SELECT group_id FROM user_group_member WHERE user_id = #{userId}
                )))
            """)
    int countEditAccessByUser(@Param("dashboardId") Long dashboardId, @Param("userId") Long userId);
}
