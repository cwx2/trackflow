package com.trackflow.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报表统计专用 Mapper — 所有聚合在 SQL 层完成，不加载原始记录到内存
 */
@Mapper
public interface ReportStatisticsMapper {

    /**
     * 状态分布：GROUP BY status_id，JOIN issue_status 获取名称和颜色
     */
    List<Map<String, Object>> selectStatusDistribution(
            @Param("projectIds") List<Long> projectIds,
            @Param("sprintId") Long sprintId);

    /**
     * 优先级分布：GROUP BY priority
     */
    List<Map<String, Object>> selectPriorityDistribution(
            @Param("projectIds") List<Long> projectIds,
            @Param("sprintId") Long sprintId);

    /**
     * 类型分布：GROUP BY issue_type
     */
    List<Map<String, Object>> selectTypeDistribution(
            @Param("projectIds") List<Long> projectIds,
            @Param("sprintId") Long sprintId);

    /**
     * 工作负载：GROUP BY assignee_id，统计总数、已关闭数
     */
    List<Map<String, Object>> selectWorkload(
            @Param("projectIds") List<Long> projectIds,
            @Param("sprintId") Long sprintId,
            @Param("closedStatusIds") List<Long> closedStatusIds);

    /**
     * 概览统计：单条 SQL 一次返回 total/open/closed/unassigned/overdue
     */
    Map<String, Object> selectOverview(
            @Param("projectIds") List<Long> projectIds,
            @Param("sprintId") Long sprintId,
            @Param("closedStatusIds") List<Long> closedStatusIds,
            @Param("now") LocalDateTime now);

    /**
     * 趋势-创建：按日期分组计数 created_at
     */
    List<Map<String, Object>> selectCreatedTrend(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 趋势-解决：按日期分组计数 resolved_at
     */
    List<Map<String, Object>> selectResolvedTrend(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 跨项目对比：GROUP BY project_id
     */
    List<Map<String, Object>> selectProjectComparison(
            @Param("projectIds") List<Long> projectIds,
            @Param("closedStatusIds") List<Long> closedStatusIds,
            @Param("now") LocalDateTime now);

    /**
     * 累积流图：利用 generate_series + 窗口函数，一条 SQL 返回每日各状态工单数
     * 返回行：(day, status_name, status_color, sort_order, cnt)
     */
    List<Map<String, Object>> selectCumulativeFlow(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 解决时间：查询已解决工单的创建和解决时间，在 SQL 层计算耗时
     * 返回行：(period, avg_hours, median_hours, p90_hours, resolved_count)
     */
    List<Map<String, Object>> selectResolutionTimeTrend(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("useWeekGrouping") boolean useWeekGrouping);

    /**
     * 解决时间分组明细：按 type/priority/assignee 分组统计解决耗时
     */
    List<Map<String, Object>> selectResolutionTimeByGroup(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("groupBy") String groupBy);

    // ─── 时间报表聚合查询 ─────────────────────────────────────────

    /**
     * 按人员分组汇总工时（分钟）
     * 返回行：(user_id, user_name, total_minutes)
     */
    List<Map<String, Object>> selectTimeByUser(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 按项目分组汇总工时（分钟）
     * 返回行：(project_id, project_name, total_minutes)
     */
    List<Map<String, Object>> selectTimeByProject(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 按工作类型分组汇总工时（分钟）
     * 返回行：(work_type, total_minutes)
     */
    List<Map<String, Object>> selectTimeByWorkType(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 每日工时趋势
     * 返回行：(work_date, total_minutes)
     */
    List<Map<String, Object>> selectTimeTrend(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 交叉维度：按项目+人员分组汇总工时
     * 返回行：(project_name, user_name, total_minutes)
     */
    List<Map<String, Object>> selectTimeCrossProjectUser(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 预估对比报表：查询有预估工时的工单的 estimated_hours vs spent_hours
     * 返回行：(issue_id, issue_key, title, project_name, assignee_name, estimated_hours, spent_hours)
     */
    List<Map<String, Object>> selectEstimationComparison(
            @Param("projectIds") List<Long> projectIds);
}
