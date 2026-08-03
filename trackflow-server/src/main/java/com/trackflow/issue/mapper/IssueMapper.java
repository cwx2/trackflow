package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.result.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
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
    IssueDetailRow selectDetailById(@Param("id") Long id);

    /**
     * 评论列表 JOIN 用户表（一次查询）
     */
    List<CommentRow> selectCommentsWithUser(@Param("issueId") Long issueId);

    /**
     * 活动列表 JOIN 用户表（一次查询）
     */
    List<ActivityRow> selectActivitiesWithUser(@Param("issueId") Long issueId);

    /**
     * 活动列表分页查询（按时间倒序，最新在前）
     */
    List<ActivityRow> selectActivitiesWithUserPaged(@Param("issueId") Long issueId,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    /**
     * 活动记录总数（排除 no-op 变更）
     */
    long countActivities(@Param("issueId") Long issueId);

    /**
     * Dashboard 活动流：查询指定 issue 列表的最近活动记录，JOIN 用户表和 Issue 表
     */
    List<ActivityRow> selectDashboardActivities(@Param("issueIds") List<Long> issueIds, @Param("limit") int limit);

    /**
     * Dashboard 活动流（基于项目范围）：通过 JOIN project_member 子查询，避免传递大量 issue ID
     */
    List<ActivityRow> selectDashboardActivitiesByProjects(@Param("projectIds") List<Long> projectIds, @Param("limit") int limit);

    /**
     * Widget 活动流：支持多维筛选（项目/活动类型/用户），用于 Activity Feed Widget
     */
    List<ActivityRow> selectWidgetActivities(
            @Param("projectIds") List<Long> projectIds,
            @Param("actions") List<String> actions,
            @Param("userIds") List<Long> userIds,
            @Param("limit") int limit);

    /**
     * 查询回收站工单列表（已删除的），绕过 MyBatis-Plus 逻辑删除过滤
     */
    Page<TrashRow> selectTrashPage(Page<?> page, @Param("projectId") Long projectId);

    /**
     * 直接按 ID 查询工单（忽略 deleted_at 过滤），用于恢复操作
     */
    DeletedIssueRow selectByIdIgnoreDeleted(@Param("id") Long id);

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
    List<ChildIssueRow> selectChildrenByParentId(@Param("parentId") Long parentId);

    /**
     * 查询指定项目中超过保留期限的已删除工单 ID 列表（用于定时清理）
     */
    List<ExpiredTrashRow> selectExpiredTrash(@Param("projectId") Long projectId, @Param("cutoff") java.time.LocalDateTime cutoff);

    /**
     * 断开子工单与父工单的引用（将 parent_id 置 NULL）
     * 用于父工单软删除时清理子工单的引用关系
     */
    int clearParentId(@Param("parentId") Long parentId);

    /**
     * 查询项目中各状态的工单数量（用于看板列设置的智能推荐）
     */
    List<StatusCountRow> selectIssueCountByStatus(@Param("projectId") Long projectId);

    /**
     * 查询项目中各优先级的工单数量（用于看板 columnField=priority 模式）
     */
    List<PriorityCountRow> selectIssueCountByPriority(@Param("projectId") Long projectId);

    /**
     * 看板列聚合查询：一次性返回每个状态的工单数量和是否在工作流中。
     * <p>
     * 使用 CTE 合并原 selectDistinctStatusIdsByProject + selectIssueCountByStatus + selectWorkflowStatusIds
     * 三条独立查询为一条 SQL，减少 DB 往返次数。
     * <p>
     * 返回行包含: status_id (Long), issue_count (int), in_workflow (boolean)
     */
    List<ColumnAggregationRow> selectBoardColumnAggregation(@Param("projectId") Long projectId);

    /**
     * 燃尽图投影查询：只返回 id, created_at, resolved_at（不加载 title/description 等大字段）。
     * 用于 getBurndownData 性能优化。
     * 自动排除作为泳道标识的 Epic 工单（issue_type='Epic' 且 child_count > 0）。
     */
    List<BurndownRow> selectBurndownProjection(@Param("sprintId") Long sprintId);

    /**
     * 批量查询工单的 issue_type 和 child_count（消除 N+1）。
     * 用于燃尽图过滤泳道标识工单（Epic 且有子工单的工单）。
     */
    List<IssueTypeRow> selectTypeByIds(@Param("issueIds") List<Long> issueIds);

    /**
     * 批量查询工单的 created_at 时间（消除 N+1）。
     * 用于获取已移出 Sprint 的工单进入时间。
     */
    List<IssueCreatedAtRow> selectCreatedAtByIds(@Param("issueIds") List<Long> issueIds);

    /**
     * 批量查询工单的 estimated_hours（消除 N+1）。
     * 用于估时模式燃尽图的 scope 动态追踪。
     */
    List<IssueEstimatedHoursRow> selectEstimatedHoursByIds(@Param("issueIds") List<Long> issueIds);

    /**
     * 批量查询指定 Issue 的自定义字段值（用于 estimation 燃尽图）。
     * 从 custom_field_value 表查询 field_def_id = fieldId 的记录，value 为 TEXT 存储的数值。
     * 返回结果中 issueId 即为工单 ID，estimatedHours 为解析后的浮点数（NULL 则跳过）。
     */
    List<IssueEstimatedHoursRow> selectCustomFieldValuesByIds(
            @Param("issueIds") List<Long> issueIds,
            @Param("fieldId") Long fieldId);

    /**
     * 看板卡片精简查询：单次 JOIN 查出卡片渲染所需的全部字段（status_name, assignee_name, sprint_name）。
     * 替代循环分页 + 逐页 fillXxxInfo 的重量级方式。
     * <p>
     * 当项目存在手动排序时，通过 LEFT JOIN issue_manual_order 表在服务端直接按手动排序截断，
     * 确保 LIMIT 截断丢弃的是"排序靠后"的卡片，而非"创建时间早"的卡片（REQ-274）。
     *
     * @param projectId         项目 ID（必填）
     * @param statusIds         可见列的状态 ID 集合（可选，columnField=status 时使用）
     * @param priorities        可见列的优先级值集合（可选，columnField=priority 时使用）
     * @param sprintId          Sprint 过滤（可选）
     * @param assigneeId        负责人过滤（可选）
     * @param keyword           关键词搜索（可选，搜标题/key）
     * @param excludeDoneBefore 排除此日期前已完成的工单（可选）
     * @param limit             最大返回数量（安全上限）
     * @param manualOrderContextType 手动排序上下文类型（可选，如 'project'）
     * @param manualOrderContextId   手动排序上下文 ID（可选，如 projectId）
     * @param swimlaneField     泳道分组字段（可选，REQ-386：服务端泳道过滤）
     * @param swimlaneValues    泳道选中值列表（可选，REQ-386：服务端泳道过滤）
     */
    List<BoardCardRow> selectBoardCards(
            @Param("projectId") Long projectId,
            @Param("statusIds") List<Long> statusIds,
            @Param("priorities") List<String> priorities,
            @Param("sprintId") Long sprintId,
            @Param("assigneeId") Long assigneeId,
            @Param("keyword") String keyword,
            @Param("excludeDoneBefore") java.time.LocalDateTime excludeDoneBefore,
            @Param("limit") int limit,
            @Param("manualOrderContextType") String manualOrderContextType,
            @Param("manualOrderContextId") Long manualOrderContextId,
            @Param("swimlaneField") String swimlaneField,
            @Param("swimlaneValues") List<String> swimlaneValues
    );

    /**
     * 跨项目看板卡片查询 — 查询多个项目中的工单，按状态合并展示。
     * <p>
     * 与 selectBoardCards 相同，但将 project_id = ? 改为 project_id IN (...)，
     * 同时查出项目 key 用于卡片标识。
     * 不支持手动排序（跨项目场景无跨项目统一排序），始终按创建时间倒序。
     *
     * @param projectIds        项目 ID 列表（必填，包含主项目和所有关联项目）
     * @param statusIds         可见列的状态 ID 集合（可选）
     * @param priorities        可见列的优先级值集合（可选）
     * @param sprintId          Sprint 过滤（可选）
     * @param assigneeId        负责人过滤（可选）
     * @param keyword           关键词搜索（可选）
     * @param excludeDoneBefore 排除此日期前已完成的工单（可选）
     * @param limit             最大返回数量
     * @param swimlaneField     泳道分组字段（可选）
     * @param swimlaneValues    泳道选中值列表（可选）
     */
    List<BoardCardRow> selectBoardCardsMultiProject(
            @Param("projectIds") List<Long> projectIds,
            @Param("statusIds") List<Long> statusIds,
            @Param("priorities") List<String> priorities,
            @Param("sprintId") Long sprintId,
            @Param("assigneeId") Long assigneeId,
            @Param("keyword") String keyword,
            @Param("excludeDoneBefore") java.time.LocalDateTime excludeDoneBefore,
            @Param("limit") int limit,
            @Param("swimlaneField") String swimlaneField,
            @Param("swimlaneValues") List<String> swimlaneValues
    );

    /**
     * 工作流影响分析：按状态 ID 列表聚合工单数量（单次 GROUP BY 查询，消除 N+1）。
     *
     * @param statusIds 需要统计的状态 ID 列表
     * @param projectId 项目 ID（null 表示全局统计）
     * @param issueType 工单类型（null 表示所有类型）
     * @return 每个状态的工单数量
     */
    List<StatusCountRow> selectIssueCountByStatuses(
            @Param("statusIds") List<Long> statusIds,
            @Param("projectId") Long projectId,
            @Param("issueType") String issueType
    );
}
