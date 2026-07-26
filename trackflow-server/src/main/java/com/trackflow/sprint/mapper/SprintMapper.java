package com.trackflow.sprint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.vo.CompletionPreviewVO;
import com.trackflow.sprint.vo.SprintVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SprintMapper extends BaseMapper<Sprint> {

    /**
     * 查询项目的 Sprint 列表，带工单统计数据。
     * 使用 resultMap 直接映射为 SprintVO（Long→String ID 转换在 XML 中处理）。
     */
    List<SprintVO> selectSprintsWithStats(@Param("projectId") Long projectId);

    /**
     * 查询单个 Sprint，带工单统计数据。
     * 复用列表查询的统计逻辑，确保数据一致性。
     */
    SprintVO selectSprintWithStats(@Param("sprintId") Long sprintId);

    /**
     * 查询 Sprint 中未关闭的工单 ID 列表（通过 JOIN issue_status 判断 is_closed = false）
     */
    List<Long> selectOpenIssueIds(@Param("sprintId") Long sprintId);

    /**
     * 查询 Sprint 中未关闭的工单详细信息（用于完成预览）
     */
    List<CompletionPreviewVO.OpenIssueItem> selectOpenIssuesForCompletion(@Param("sprintId") Long sprintId);

    /**
     * 查询 Sprint 中按负责人分组的工单统计。
     * 返回 Map 列表，每项包含 user_id、display_name 及各状态统计数。
     */
    List<java.util.Map<String, Object>> selectAssigneeDistribution(@Param("sprintId") Long sprintId);

    /**
     * 查询 Sprint 工单每日记录工时（work_items 燃尽图模式）。
     * 按 work_date 分组汇总 time_entry.duration（分钟）。
     * 仅统计当前仍在 Sprint 中的工单（sprint_id = sprintId AND deleted_at IS NULL）。
     *
     * @param sprintId Sprint ID
     * @return 每日记录工时列表（work_date + total_minutes）
     */
    List<com.trackflow.issue.mapper.result.DailyLoggedMinutesRow> selectSprintDailyLoggedMinutes(
            @Param("sprintId") Long sprintId);

    /**
     * 查询项目最近已完成 Sprint 的速率统计数据。
     * 按完成时间倒序取最近 N 个 completed/archived 状态的 Sprint，
     * 包含各 Sprint 的规划工时和已完成工时（JOIN issue_status）。
     *
     * @param projectId 项目 ID
     * @param limit     最多取几个 Sprint（通常 3~5）
     * @return Sprint 速率列表（含工时统计），时间从旧到新
     */
    List<com.trackflow.sprint.vo.SprintVelocityVO.SprintVelocityItem> selectSprintVelocity(
            @Param("projectId") Long projectId,
            @Param("limit") int limit);
}
