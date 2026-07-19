package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.IssueActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IssueActivityMapper extends BaseMapper<IssueActivity> {

    /**
     * 查询指定 Issue 最近一次 assignee 变更的旧值（即前一任负责人）。
     *
     * @param issueId Issue ID
     * @return 前一任负责人的用户 ID 字符串，无历史时返回 null
     */
    @Select("SELECT old_value FROM issue_activity " +
            "WHERE issue_id = #{issueId} AND field_name = 'assignee_id' AND old_value IS NOT NULL " +
            "ORDER BY created_at DESC LIMIT 1")
    String selectPreviousAssignee(@Param("issueId") Long issueId);

    /**
     * 查询指定 Issue 最近一次状态变更活动记录。
     *
     * @param issueId Issue ID
     * @return 最近一次状态变更的活动记录，无历史时返回 null
     */
    @Select("SELECT * FROM issue_activity " +
            "WHERE issue_id = #{issueId} AND field_name = 'status' " +
            "ORDER BY created_at DESC LIMIT 1")
    IssueActivity selectLastStatusChange(@Param("issueId") Long issueId);

    /**
     * 查询"移入"指定 Sprint 的活动记录（投影：只返回 issue_id 和 created_at）。
     * 利用部分索引 idx_activity_sprint_new_value 加速。
     *
     * @param sprintId Sprint ID（字符串形式）
     * @return 移入活动记录列表（仅 issueId + createdAt 有值）
     */
    @Select("SELECT issue_id, created_at FROM issue_activity " +
            "WHERE field_name = 'sprint' AND new_value = #{sprintId}")
    List<IssueActivity> selectMovedInBySprint(@Param("sprintId") String sprintId);

    /**
     * 查询"移出"指定 Sprint 的活动记录（投影：只返回 issue_id 和 created_at）。
     * 利用部分索引 idx_activity_sprint_old_value 加速。
     *
     * @param sprintId Sprint ID（字符串形式）
     * @return 移出活动记录列表（仅 issueId + createdAt 有值）
     */
    @Select("SELECT issue_id, created_at FROM issue_activity " +
            "WHERE field_name = 'sprint' AND old_value = #{sprintId}")
    List<IssueActivity> selectMovedOutBySprint(@Param("sprintId") String sprintId);
}
