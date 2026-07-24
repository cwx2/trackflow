package com.trackflow.report.mapper;

import com.trackflow.report.dto.ReportQueryParams;
import com.trackflow.report.mapper.result.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表统计专用 Mapper — 所有聚合在 SQL 层完成，不加载原始记录到内存
 */
@Mapper
public interface ReportStatisticsMapper {

    /**
     * 状态分布：GROUP BY status_id，JOIN issue_status 获取名称和颜色
     */
    List<StatusDistributionRow> selectStatusDistribution(
            @Param("projectIds") List<Long> projectIds,
            @Param("sprintId") Long sprintId,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 优先级分布：GROUP BY priority
     */
    List<PriorityDistributionRow> selectPriorityDistribution(
            @Param("projectIds") List<Long> projectIds,
            @Param("sprintId") Long sprintId,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 类型分布：GROUP BY issue_type
     */
    List<TypeDistributionRow> selectTypeDistribution(
            @Param("projectIds") List<Long> projectIds,
            @Param("sprintId") Long sprintId,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 工作负载：GROUP BY assignee_id，统计总数、已关闭数
     */
    List<WorkloadRow> selectWorkload(
            @Param("projectIds") List<Long> projectIds,
            @Param("sprintId") Long sprintId,
            @Param("closedStatusIds") List<Long> closedStatusIds,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 概览统计：单条 SQL 一次返回 total/open/closed/unassigned/overdue
     */
    OverviewRow selectOverview(
            @Param("projectIds") List<Long> projectIds,
            @Param("sprintId") Long sprintId,
            @Param("closedStatusIds") List<Long> closedStatusIds,
            @Param("now") LocalDateTime now,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 趋势-创建：按日期分组计数 created_at
     */
    List<TrendRow> selectCreatedTrend(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 趋势-解决：按日期分组计数 resolved_at
     */
    List<TrendRow> selectResolvedTrend(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 跨项目对比：GROUP BY project_id
     */
    List<ProjectComparisonRow> selectProjectComparison(
            @Param("projectIds") List<Long> projectIds,
            @Param("closedStatusIds") List<Long> closedStatusIds,
            @Param("now") LocalDateTime now,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 累积流图：利用 generate_series + 窗口函数，一条 SQL 返回每日各状态工单数
     * 返回行：(day, status_name, status_color, sort_order, cnt)
     */
    List<CumulativeFlowRow> selectCumulativeFlow(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 解决时间：查询已解决工单的创建和解决时间，在 SQL 层计算耗时
     * 返回行：(period, avg_hours, median_hours, p90_hours, resolved_count)
     */
    List<ResolutionTimeTrendRow> selectResolutionTimeTrend(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("useWeekGrouping") boolean useWeekGrouping,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 解决时间分组明细：按 type/priority/assignee 分组统计解决耗时
     */
    List<ResolutionTimeGroupRow> selectResolutionTimeByGroup(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("groupBy") String groupBy,
            @Param("issueIds") List<Long> issueIds);

    // ─── 时间报表聚合查询 ─────────────────────────────────────────

    /**
     * 按人员分组汇总工时（分钟）
     * 返回行：(user_id, user_name, total_minutes)
     */
    List<TimeByUserRow> selectTimeByUser(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 按项目分组汇总工时（分钟）
     * 返回行：(project_id, project_name, total_minutes)
     */
    List<TimeByProjectRow> selectTimeByProject(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 按工作类型分组汇总工时（分钟）
     * 返回行：(work_type, total_minutes)
     */
    List<TimeByWorkTypeRow> selectTimeByWorkType(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("workTypeAttributeId") Long workTypeAttributeId);

    /**
     * 每日工时趋势
     * 返回行：(work_date, total_minutes)
     */
    List<TimeTrendRow> selectTimeTrend(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 交叉维度：按项目+人员分组汇总工时
     * 返回行：(project_name, user_name, total_minutes)
     */
    List<TimeCrossProjectUserRow> selectTimeCrossProjectUser(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 预估对比报表：查询有预估工时的工单的 estimated_hours vs spent_hours（分页）
     * 返回行：(issue_id, issue_key, title, project_name, assignee_name, estimated_hours, spent_hours)
     */
    List<EstimationComparisonRow> selectEstimationComparison(
            @Param("projectIds") List<Long> projectIds,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 预估对比报表：统计有预估工时的工单总数
     */
    long countEstimationComparison(@Param("projectIds") List<Long> projectIds);

    /**
     * 预估对比报表：聚合汇总（总预估、总实际、按项目分组）
     * 返回行：(project_name, estimated_hours_sum, spent_hours_sum, issue_count)
     */
    List<EstimationSummaryRow> selectEstimationSummary(@Param("projectIds") List<Long> projectIds);

    // ─── 比率对比报表查询 ─────────────────────────────────────────

    /**
     * 修复趋势：按日期统计转换到"已关闭"（done 类）状态的事件数
     * 基于 issue_activity 表中 field_name='status'，new_value 对应 done 类别的 status
     */
    List<RateComparisonRow> selectFixedTrend(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("doneStatusNames") List<String> doneStatusNames,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 验证趋势：按日期统计从"测试中"类状态转换到"已关闭"（done 类）状态的事件数
     * 代表测试通过验证的工单数
     */
    List<RateComparisonRow> selectVerifiedTrend(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("testingStatusNames") List<String> testingStatusNames,
            @Param("doneStatusNames") List<String> doneStatusNames,
            @Param("issueIds") List<Long> issueIds);

    /**
     * 重开趋势：按日期统计转换到"重新打开"状态的事件数
     */
    List<RateComparisonRow> selectReopenedTrend(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("reopenedStatusNames") List<String> reopenedStatusNames,
            @Param("issueIds") List<Long> issueIds);

    // ─── 报表执行引擎增强查询 ─────────────────────────────────────────

    /**
     * 通用单维度分组查询（带筛选 + 时间范围）
     * groupBy 维度在 SQL 中动态选择
     */
    List<ReportGroupRow> selectReportGrouped(@Param("params") ReportQueryParams params);

    /**
     * 双维度交叉分组查询
     * 返回行：(primary_label, secondary_label, cnt)
     */
    List<ReportCrossRow> selectReportCross(@Param("params") ReportQueryParams params);

    /**
     * 状态转换统计查询
     * 基于 issue_activity 表的 action='change_status' 事件聚合
     * 返回行：(from_status, to_status, transition_count, avg_duration_hours)
     */
    List<StateTransitionRow> selectStateTransitions(
            @Param("projectIds") List<Long> projectIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("issueIds") List<Long> issueIds);
}
