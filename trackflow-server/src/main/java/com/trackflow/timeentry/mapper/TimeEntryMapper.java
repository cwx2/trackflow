package com.trackflow.timeentry.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.timeentry.entity.TimeEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface TimeEntryMapper extends BaseMapper<TimeEntry> {

    /**
     * 汇总指定 Issue 的工时总分钟数
     */
    Integer sumDurationByIssueId(@Param("issueId") Long issueId);

    /**
     * 原子更新 Issue 的 spent_hours（子查询方式，防止并发 lost update）
     */
    int atomicRefreshSpentHours(@Param("issueId") Long issueId);

    /**
     * 全量校准所有 issue 的 spent_hours（管理员自愈操作）
     */
    int recalculateAllSpentHours();

    /**
     * 查询工时条目，JOIN issue 表获取 issueKey，JOIN attribute 表获取 work type
     * 支持按项目和工作类型（activityId）筛选
     * allowedProjectIds 不为 null 时，限制只返回这些项目中的工时
     */
    List<Map<String, Object>> selectEntriesWithIssueKey(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("projectId") Long projectId,
            @Param("activityId") Long activityId,
            @Param("workTypeAttributeId") Long workTypeAttributeId,
            @Param("allowedProjectIds") List<Long> allowedProjectIds
    );

    /**
     * 按项目聚合：查询用户有权访问的所有项目工时（项目视图概览）
     * ongoing 记录仅返回当前用户自己的，他人的 ongoing 记录不可见
     */
    List<Map<String, Object>> selectEntriesByProjectForUser(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("workTypeAttributeId") Long workTypeAttributeId
    );

    /**
     * 查询指定项目在日期范围内的所有工时（项目视图明细）
     * ongoing 记录仅返回当前用户自己的，他人的 ongoing 记录不可见
     */
    List<Map<String, Object>> selectEntriesByProject(
            @Param("projectId") Long projectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("workTypeAttributeId") Long workTypeAttributeId,
            @Param("currentUserId") Long currentUserId
    );

    /**
     * 统计项目的工时记录总数（不含 ongoing）
     */
    int countByProjectId(@Param("projectId") Long projectId);

    /**
     * 统计项目涉及的不同用户数
     */
    int countDistinctUsersByProjectId(@Param("projectId") Long projectId);

    /**
     * 统计项目中正在进行的计时器数量
     */
    int countActiveTimersByProjectId(@Param("projectId") Long projectId);

    /**
     * 查询项目中所有活跃的计时器（ongoing=true）
     */
    List<TimeEntry> selectActiveTimersByProjectId(@Param("projectId") Long projectId);
}
