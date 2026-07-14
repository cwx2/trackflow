package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.Issue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface IssueMapper extends BaseMapper<Issue> {

    /**
     * 一次 JOIN 查询 Issue 详情（含关联表名称）
     * 消除 N+1：project_name, status, assignee_name, reporter_name, sprint_name, parent_key
     */
    Map<String, Object> selectDetailById(@Param("id") Long id);

    /**
     * 评论列表 JOIN 用户表（一次查询）
     */
    List<Map<String, Object>> selectCommentsWithUser(@Param("issueId") Long issueId);

    /**
     * 活动列表 JOIN 用户表（一次查询）
     */
    List<Map<String, Object>> selectActivitiesWithUser(@Param("issueId") Long issueId);

    /**
     * Dashboard 活动流：查询指定 issue 列表的最近活动记录，JOIN 用户表和 Issue 表
     */
    List<Map<String, Object>> selectDashboardActivities(@Param("issueIds") List<Long> issueIds, @Param("limit") int limit);

    /**
     * Dashboard 活动流（基于项目范围）：通过 JOIN project_member 子查询，避免传递大量 issue ID
     */
    List<Map<String, Object>> selectDashboardActivitiesByProjects(@Param("projectIds") List<Long> projectIds, @Param("limit") int limit);
}
