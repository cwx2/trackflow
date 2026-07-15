package com.trackflow.timeentry.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.timeentry.entity.TimeEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TimeEntryMapper extends BaseMapper<TimeEntry> {

    /**
     * 汇总指定 Issue 的工时总分钟数
     */
    Integer sumDurationByIssueId(@Param("issueId") Long issueId);

    /**
     * 查询工时条目，JOIN issue 表获取 issueKey
     */
    List<Map<String, Object>> selectEntriesWithIssueKey(
            @Param("userId") Long userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    /**
     * 按项目聚合：查询用户有权访问的所有项目工时（项目视图概览）
     */
    List<Map<String, Object>> selectEntriesByProjectForUser(
            @Param("userId") Long userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    /**
     * 查询指定项目在日期范围内的所有工时（项目视图明细）
     */
    List<Map<String, Object>> selectEntriesByProject(
            @Param("projectId") Long projectId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );
}
