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
}
