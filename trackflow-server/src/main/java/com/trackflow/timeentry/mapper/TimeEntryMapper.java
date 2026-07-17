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
     */
    List<Map<String, Object>> selectEntriesWithIssueKey(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("projectId") Long projectId,
            @Param("activityId") Long activityId,
            @Param("workTypeAttributeId") Long workTypeAttributeId
    );

    /**
     * 按项目聚合：查询用户有权访问的所有项目工时（项目视图概览）
     */
    List<Map<String, Object>> selectEntriesByProjectForUser(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("workTypeAttributeId") Long workTypeAttributeId
    );

    /**
     * 查询指定项目在日期范围内的所有工时（项目视图明细）
     */
    List<Map<String, Object>> selectEntriesByProject(
            @Param("projectId") Long projectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("workTypeAttributeId") Long workTypeAttributeId
    );
}
