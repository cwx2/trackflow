package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.result.ActivityRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface IssueMapper extends BaseMapper<Issue> {

    /**
     * 查询项目中已使用的状态 ID（DISTINCT，高性能）
     */
    Set<Long> selectDistinctStatusIdsByProject(@Param("projectId") Long projectId);

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
    List<ActivityRow> selectActivitiesWithUser(@Param("issueId") Long issueId);

    /**
     * Dashboard 活动流：查询指定 issue 列表的最近活动记录，JOIN 用户表和 Issue 表
     */
    List<ActivityRow> selectDashboardActivities(@Param("issueIds") List<Long> issueIds, @Param("limit") int limit);

    /**
     * Dashboard 活动流（基于项目范围）：通过 JOIN project_member 子查询，避免传递大量 issue ID
     */
    List<ActivityRow> selectDashboardActivitiesByProjects(@Param("projectIds") List<Long> projectIds, @Param("limit") int limit);

    /**
     * 查询回收站工单列表（已删除的），绕过 MyBatis-Plus 逻辑删除过滤
     */
    Page<Map<String, Object>> selectTrashPage(Page<?> page, @Param("projectId") Long projectId);

    /**
     * 直接按 ID 查询工单（忽略 deleted_at 过滤），用于恢复操作
     */
    Map<String, Object> selectByIdIgnoreDeleted(@Param("id") Long id);

    /**
     * 将工单的 deleted_at 置 NULL（恢复操作）
     */
    int restoreById(@Param("id") Long id);

    /**
     * 物理删除工单（永久删除）
     */
    int permanentDeleteById(@Param("id") Long id);

    /**
     * 查询指定父工单的所有子任务（带状态和负责人名称，单次 JOIN 查询）
     */
    List<Map<String, Object>> selectChildrenByParentId(@Param("parentId") Long parentId);

    /**
     * 查询指定项目中超过保留期限的已删除工单 ID 列表（用于定时清理）
     */
    List<Map<String, Object>> selectExpiredTrash(@Param("projectId") Long projectId, @Param("cutoff") java.time.LocalDateTime cutoff);

    /**
     * 断开子工单与父工单的引用（将 parent_id 置 NULL）
     * 用于父工单软删除时清理子工单的引用关系
     */
    int clearParentId(@Param("parentId") Long parentId);

    /**
     * 查询项目中各状态的工单数量（用于看板列设置的智能推荐）
     */
    List<Map<String, Object>> selectIssueCountByStatus(@Param("projectId") Long projectId);

    /**
     * 看板列聚合查询：一次性返回每个状态的工单数量和是否在工作流中。
     * <p>
     * 使用 CTE 合并原 selectDistinctStatusIdsByProject + selectIssueCountByStatus + selectWorkflowStatusIds
     * 三条独立查询为一条 SQL，减少 DB 往返次数。
     * <p>
     * 返回行包含: status_id (Long), issue_count (int), in_workflow (boolean)
     */
    List<Map<String, Object>> selectBoardColumnAggregation(@Param("projectId") Long projectId);

    /**
     * 燃尽图投影查询：只返回 id, created_at, resolved_at（不加载 title/description 等大字段）。
     * 用于 getBurndownData 性能优化。
     */
    List<Map<String, Object>> selectBurndownProjection(@Param("sprintId") Long sprintId);

    /**
     * 批量查询工单的 created_at 时间（消除 N+1）。
     * 用于获取已移出 Sprint 的工单进入时间。
     */
    List<Map<String, Object>> selectCreatedAtByIds(@Param("issueIds") List<Long> issueIds);
}
