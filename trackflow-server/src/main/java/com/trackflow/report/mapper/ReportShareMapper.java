package com.trackflow.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.report.entity.ReportShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报表共享 Mapper
 */
@Mapper
public interface ReportShareMapper extends BaseMapper<ReportShare> {

    /**
     * 查询用户通过直接共享或组共享可访问的报表 ID 列表
     */
    @Select("""
            SELECT DISTINCT rs.report_id FROM report_share rs
            WHERE (rs.target_type = 'user' AND rs.target_id = #{userId})
               OR (rs.target_type = 'group' AND rs.target_id IN (
                   SELECT group_id FROM user_group_member WHERE user_id = #{userId}
               ))
            """)
    List<Long> selectAccessibleReportIds(@Param("userId") Long userId);

    /**
     * 检查用户是否对某报表有访问权限（直接或通过组）
     */
    @Select("""
            SELECT COUNT(*) FROM report_share rs
            WHERE rs.report_id = #{reportId}
              AND ((rs.target_type = 'user' AND rs.target_id = #{userId})
                OR (rs.target_type = 'group' AND rs.target_id IN (
                    SELECT group_id FROM user_group_member WHERE user_id = #{userId}
                )))
            """)
    int countAccessByUser(@Param("reportId") Long reportId, @Param("userId") Long userId);

    /**
     * 检查用户是否对某报表有编辑权限
     */
    @Select("""
            SELECT COUNT(*) FROM report_share rs
            WHERE rs.report_id = #{reportId}
              AND rs.permission = 'edit'
              AND ((rs.target_type = 'user' AND rs.target_id = #{userId})
                OR (rs.target_type = 'group' AND rs.target_id IN (
                    SELECT group_id FROM user_group_member WHERE user_id = #{userId}
                )))
            """)
    int countEditAccessByUser(@Param("reportId") Long reportId, @Param("userId") Long userId);
}
