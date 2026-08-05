package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.query.engine.QueryExecutor;
import com.trackflow.report.entity.ReportConfig;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.entity.ReportType;
import com.trackflow.report.mapper.ReportStatisticsMapper;
import com.trackflow.report.mapper.result.*;
import com.trackflow.report.vo.*;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.workitemattr.service.WorkItemAttributeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表图表服务 — 负责时间线类、状态转换类、时间管理类报表的执行逻辑
 * <p>
 * 从 ReportService 中抽取，包含燃尽图、累积流图、解决时间分析、
 * 平均工单年龄、状态转换统计、时间报表、预估对比等纯计算逻辑。
 * 不含数据库 CRUD 和权限校验（由 ReportExecutionService 统一处理）。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportChartService {

    private final ReportStatisticsMapper reportStatisticsMapper;
    private final ReportStatisticsService reportStatisticsService;
    private final SprintMapper sprintMapper;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final QueryExecutor queryExecutor;
    private final WorkItemAttributeService workItemAttributeService;

    /**
     * 执行时间线类报表（燃尽图、累积流图、解决时间分析）
     */
    public ReportExecuteResultVO executeTimelineReport(ReportDefinition report, ReportType reportType, List<Long> projectIds) {
        Map<String, Object> rawConfig = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(rawConfig);

        ReportExecuteResultVO result = new ReportExecuteResultVO();
        result.setTitle(report.getName());
        result.setType(report.getType());
        result.setCategory("timeline");
        result.setCalculatedAt(LocalDateTime.now());
        result.setRefreshInterval(config.getRefreshInterval());

        LocalDateTime[] timeRange = config.resolveTimeRange();
        java.time.LocalDate startDate = timeRange != null ? timeRange[0].toLocalDate() : java.time.LocalDate.now().minusDays(29);
        java.time.LocalDate endDate = timeRange != null ? timeRange[1].toLocalDate() : java.time.LocalDate.now();

        Long sprintId = null;
        if (config.getFilters() != null && config.getFilters().getSprintId() != null) {
            try { sprintId = Long.parseLong(config.getFilters().getSprintId()); }
            catch (NumberFormatException ignore) {}
        }

        List<Long> issueIds = resolveIssueFilterIds(config, projectIds);

        switch (reportType) {
            case BURNDOWN, BURNDOWN_CHART -> {
                if (sprintId == null) sprintId = findActiveSprintId(projectIds);
                if (sprintId == null) {
                    result.setChartType("line");
                    result.setDates(List.of());
                    result.setSeries(List.of());
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("message", "没有找到可用的 Sprint");
                    result.setSummary(summary);
                    return result;
                }
                executeBurndownReport(result, report.getProjectId(), sprintId);
            }
            case CUMULATIVE_FLOW -> executeCumulativeFlowReport(result, projectIds, startDate, endDate, issueIds);
            case RESOLUTION_TIME -> executeResolutionTimeReport(result, projectIds, startDate, endDate, config, issueIds);
            case AVERAGE_ISSUE_AGE -> executeAverageIssueAgeReport(result, projectIds, startDate, endDate, config, issueIds);
            case FIXED_VS_REPORTED, VERIFIED_VS_REOPENED, RESOLVED_VS_NEW -> {
                ReportExecuteResultVO rateResult = reportStatisticsService.getRateComparisonData(
                        reportType, projectIds, startDate, endDate, issueIds);
                result.setChartType(rateResult.getChartType());
                result.setDates(rateResult.getDates());
                result.setSeries(rateResult.getSeries());
                result.setSummary(rateResult.getSummary());
            }
            default -> { return null; }
        }

        if (config.hasFilters() || config.getTimeRange() != null) {
            result.setAppliedFilters(buildFilterSummary(config));
        }
        return result;
    }

    /**
     * 执行状态转换统计报表
     */
    public ReportExecuteResultVO executeStateTransitionReport(ReportDefinition report, List<Long> projectIds) {
        Map<String, Object> rawConfig = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(rawConfig);

        ReportExecuteResultVO result = new ReportExecuteResultVO();
        result.setTitle(report.getName());
        result.setType(report.getType());
        result.setCategory("state_transition");
        result.setChartType("bar_horizontal");
        result.setCalculatedAt(LocalDateTime.now());
        result.setRefreshInterval(config.getRefreshInterval());

        LocalDateTime[] timeRange = config.resolveTimeRange();
        LocalDateTime start = timeRange != null ? timeRange[0] : LocalDateTime.now().minusDays(30);
        LocalDateTime end = timeRange != null ? timeRange[1] : LocalDateTime.now();

        List<Long> issueIds = resolveIssueFilterIds(config, projectIds);

        List<ReportExecuteResultVO.StateTransitionItem> transitions =
                reportStatisticsService.getStateTransitionData(projectIds, start, end, issueIds);

        result.setTransitions(transitions);

        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        long total = 0;
        for (ReportExecuteResultVO.StateTransitionItem item : transitions) {
            labels.add(item.getFromStatus() + " → " + item.getToStatus());
            data.add(item.getCount());
            total += item.getCount();
        }
        result.setLabels(labels);
        result.setData(data);
        result.setTotal(total);

        if (config.hasFilters() || config.getTimeRange() != null) {
            result.setAppliedFilters(buildFilterSummary(config));
        }
        return result;
    }

    /**
     * 执行时间管理类报表（时间报表、预估对比）
     */
    public ReportExecuteResultVO executeTimeManagementReport(ReportDefinition report, ReportType reportType, List<Long> projectIds) {
        Map<String, Object> rawConfig = parseConfig(report.getConfig());
        ReportConfig config = ReportConfig.fromMap(rawConfig);

        ReportExecuteResultVO result = new ReportExecuteResultVO();
        result.setTitle(report.getName());
        result.setType(report.getType());
        result.setCategory("time_management");
        result.setCalculatedAt(LocalDateTime.now());
        result.setRefreshInterval(config.getRefreshInterval());

        switch (reportType) {
            case TIME_REPORT -> executeTimeReport(result, config, projectIds);
            case ESTIMATION_REPORT -> executeEstimationReport(result, projectIds);
            default -> { return null; }
        }

        if (config.hasFilters() || config.getTimeRange() != null) {
            result.setAppliedFilters(buildFilterSummary(config));
        }
        return result;
    }

    // ─── 具体图表执行方法 ────────────────────────────────────

    private void executeBurndownReport(ReportExecuteResultVO result, Long projectId, Long sprintId) {
        result.setChartType("line");
        BurndownVO burndown = reportStatisticsService.getBurndownData(projectId, sprintId);
        result.setDates(burndown.getDates());
        result.setIdealLine(burndown.getIdeal());

        List<ReportExecuteResultVO.TimeSeriesData> series = new ArrayList<>();
        ReportExecuteResultVO.TimeSeriesData actualSeries = new ReportExecuteResultVO.TimeSeriesData();
        actualSeries.setName("剩余工单");
        actualSeries.setColor("#58a6ff");
        actualSeries.setData(burndown.getActual() != null
                ? burndown.getActual().stream().map(v -> (Number) v).collect(Collectors.toList()) : List.of());
        actualSeries.setSeriesType("line");
        series.add(actualSeries);

        ReportExecuteResultVO.TimeSeriesData idealSeries = new ReportExecuteResultVO.TimeSeriesData();
        idealSeries.setName("理想线");
        idealSeries.setColor("#6b7280");
        idealSeries.setData(burndown.getIdeal() != null
                ? burndown.getIdeal().stream().map(v -> (Number) v).collect(Collectors.toList()) : List.of());
        idealSeries.setSeriesType("line");
        series.add(idealSeries);

        result.setSeries(series);
        result.setTotal(burndown.getTotalIssues());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sprintName", burndown.getSprintName());
        summary.put("totalIssues", burndown.getTotalIssues());
        result.setSummary(summary);
    }

    private void executeCumulativeFlowReport(ReportExecuteResultVO result, List<Long> projectIds,
                                              java.time.LocalDate startDate, java.time.LocalDate endDate,
                                              List<Long> issueIds) {
        result.setChartType("stacked_area");
        CumulativeFlowVO cfd = reportStatisticsService.getCumulativeFlowData(projectIds, startDate, endDate, issueIds);
        result.setDates(cfd.getDates());

        List<ReportExecuteResultVO.TimeSeriesData> series = new ArrayList<>();
        if (cfd.getSeries() != null) {
            for (CumulativeFlowVO.StatusSeries statusSeries : cfd.getSeries()) {
                ReportExecuteResultVO.TimeSeriesData ts = new ReportExecuteResultVO.TimeSeriesData();
                ts.setName(statusSeries.getName());
                ts.setColor(statusSeries.getColor());
                ts.setData(statusSeries.getData() != null
                        ? statusSeries.getData().stream().map(v -> (Number) v).collect(Collectors.toList()) : List.of());
                ts.setSeriesType("area");
                series.add(ts);
            }
        }
        result.setSeries(series);
    }

    private void executeResolutionTimeReport(ReportExecuteResultVO result, List<Long> projectIds,
                                              java.time.LocalDate startDate, java.time.LocalDate endDate,
                                              ReportConfig config, List<Long> issueIds) {
        result.setChartType("line");
        String groupBy = config.getGroupBy();
        ResolutionTimeVO rt = reportStatisticsService.getResolutionTimeData(projectIds, startDate, endDate, groupBy, issueIds);
        result.setDates(rt.getDates());

        List<ReportExecuteResultVO.TimeSeriesData> series = new ArrayList<>();

        ReportExecuteResultVO.TimeSeriesData avgSeries = new ReportExecuteResultVO.TimeSeriesData();
        avgSeries.setName("平均解决时间(h)");
        avgSeries.setColor("#58a6ff");
        avgSeries.setData(rt.getAvgHours() != null ? rt.getAvgHours().stream().map(v -> (Number) v).collect(Collectors.toList()) : List.of());
        avgSeries.setSeriesType("line");
        series.add(avgSeries);

        ReportExecuteResultVO.TimeSeriesData medianSeries = new ReportExecuteResultVO.TimeSeriesData();
        medianSeries.setName("中位解决时间(h)");
        medianSeries.setColor("#3fb950");
        medianSeries.setData(rt.getMedianHours() != null ? rt.getMedianHours().stream().map(v -> (Number) v).collect(Collectors.toList()) : List.of());
        medianSeries.setSeriesType("line");
        series.add(medianSeries);

        ReportExecuteResultVO.TimeSeriesData p90Series = new ReportExecuteResultVO.TimeSeriesData();
        p90Series.setName("P90 解决时间(h)");
        p90Series.setColor("#d29922");
        p90Series.setData(rt.getP90Hours() != null ? rt.getP90Hours().stream().map(v -> (Number) v).collect(Collectors.toList()) : List.of());
        p90Series.setSeriesType("line");
        series.add(p90Series);

        result.setSeries(series);

        Map<String, Object> summary = new LinkedHashMap<>();
        long totalResolved = rt.getResolvedCount() != null ? rt.getResolvedCount().stream().mapToLong(Long::longValue).sum() : 0L;
        summary.put("totalResolved", totalResolved);
        if (rt.getGroupDetails() != null && !rt.getGroupDetails().isEmpty()) {
            summary.put("groupDetails", rt.getGroupDetails());
        }
        result.setSummary(summary);
        result.setTotal(totalResolved);
    }

    private void executeAverageIssueAgeReport(ReportExecuteResultVO result, List<Long> projectIds,
                                               java.time.LocalDate startDate, java.time.LocalDate endDate,
                                               ReportConfig config, List<Long> issueIds) {
        result.setChartType("line");
        List<String> trackedStatuses = config.getTrackedStatuses();
        String granularity = config.getGranularity();
        Integer movingPeriod = config.getMovingPeriod();

        AverageIssueAgeVO ageData = reportStatisticsService.getAverageIssueAgeData(
                projectIds, startDate, endDate, trackedStatuses, granularity, movingPeriod, issueIds);

        result.setDates(ageData.getDates());
        List<ReportExecuteResultVO.TimeSeriesData> series = new ArrayList<>();

        ReportExecuteResultVO.TimeSeriesData avgSeries = new ReportExecuteResultVO.TimeSeriesData();
        avgSeries.setName("平均停留时间(h)");
        avgSeries.setColor("#58a6ff");
        avgSeries.setData(ageData.getAvgAgeHours() != null ? ageData.getAvgAgeHours().stream().map(v -> (Number) v).collect(Collectors.toList()) : List.of());
        avgSeries.setSeriesType("line");
        series.add(avgSeries);

        ReportExecuteResultVO.TimeSeriesData movingAvgSeries = new ReportExecuteResultVO.TimeSeriesData();
        movingAvgSeries.setName("滑动平均(h)");
        movingAvgSeries.setColor("#3fb950");
        movingAvgSeries.setData(ageData.getMovingAvgHours() != null ? ageData.getMovingAvgHours().stream().map(v -> (Number) v).collect(Collectors.toList()) : List.of());
        movingAvgSeries.setSeriesType("line");
        series.add(movingAvgSeries);

        ReportExecuteResultVO.TimeSeriesData movingMinSeries = new ReportExecuteResultVO.TimeSeriesData();
        movingMinSeries.setName("滑动最小(h)");
        movingMinSeries.setColor("#6b7280");
        movingMinSeries.setData(ageData.getMovingMinHours() != null ? ageData.getMovingMinHours().stream().map(v -> (Number) v).collect(Collectors.toList()) : List.of());
        movingMinSeries.setSeriesType("line");
        series.add(movingMinSeries);

        ReportExecuteResultVO.TimeSeriesData movingMaxSeries = new ReportExecuteResultVO.TimeSeriesData();
        movingMaxSeries.setName("滑动最大(h)");
        movingMaxSeries.setColor("#d29922");
        movingMaxSeries.setData(ageData.getMovingMaxHours() != null ? ageData.getMovingMaxHours().stream().map(v -> (Number) v).collect(Collectors.toList()) : List.of());
        movingMaxSeries.setSeriesType("line");
        series.add(movingMaxSeries);

        result.setSeries(series);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("trackedStatuses", ageData.getTrackedStatuses());
        summary.put("granularity", granularity != null ? granularity : "day");
        summary.put("movingPeriod", movingPeriod != null ? movingPeriod : 7);
        long totalOutflow = ageData.getOutflowCount() != null ? ageData.getOutflowCount().stream().mapToLong(Long::longValue).sum() : 0L;
        long totalStaying = ageData.getStayingCount() != null && !ageData.getStayingCount().isEmpty()
                ? ageData.getStayingCount().get(ageData.getStayingCount().size() - 1) : 0L;
        summary.put("totalOutflow", totalOutflow);
        summary.put("currentStaying", totalStaying);
        summary.put("outflowByDate", ageData.getOutflowCount());
        summary.put("stayingByDate", ageData.getStayingCount());
        result.setSummary(summary);
    }

    private void executeTimeReport(ReportExecuteResultVO result, ReportConfig config, List<Long> projectIds) {
        result.setChartType("bar_horizontal");
        LocalDateTime[] timeRange = config.resolveTimeRange();
        java.time.LocalDate startDate = timeRange != null ? timeRange[0].toLocalDate() : java.time.LocalDate.now().minusDays(29);
        java.time.LocalDate endDate = timeRange != null ? timeRange[1].toLocalDate() : java.time.LocalDate.now();
        String startStr = startDate.toString();
        String endStr = endDate.toString();

        String groupBy = config.getGroupBy();
        if (!("project".equals(groupBy) || "work_type".equals(groupBy) || "issue".equals(groupBy))) {
            groupBy = "assignee";
        }

        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        int totalMinutes;

        if ("project".equals(groupBy)) {
            List<TimeByProjectRow> rows = reportStatisticsMapper.selectTimeByProject(projectIds, startStr, endStr);
            totalMinutes = rows.stream().mapToInt(r -> r.getTotalMinutes() != null ? r.getTotalMinutes() : 0).sum();
            for (TimeByProjectRow row : rows) { labels.add(row.getProjectName()); data.add((long) (row.getTotalMinutes() != null ? row.getTotalMinutes() : 0)); }
        } else if ("work_type".equals(groupBy)) {
            Long workTypeAttrId = workItemAttributeService.getWorkTypeAttributeId();
            List<TimeByWorkTypeRow> rows = reportStatisticsMapper.selectTimeByWorkType(projectIds, startStr, endStr, workTypeAttrId);
            totalMinutes = rows.stream().mapToInt(r -> r.getTotalMinutes() != null ? r.getTotalMinutes() : 0).sum();
            for (TimeByWorkTypeRow row : rows) { labels.add(row.getWorkType() != null ? row.getWorkType() : "未分类"); data.add((long) (row.getTotalMinutes() != null ? row.getTotalMinutes() : 0)); }
        } else if ("issue".equals(groupBy)) {
            List<TimeIssueGroupRow> rows = reportStatisticsMapper.selectTimeByIssue(projectIds, startStr, endStr, 50, 0);
            totalMinutes = rows.stream().mapToInt(r -> r.getTotalMinutes() != null ? r.getTotalMinutes() : 0).sum();
            for (TimeIssueGroupRow row : rows) { labels.add((row.getIssueKey() + " " + (row.getTitle() != null ? row.getTitle() : "")).trim()); data.add((long) (row.getTotalMinutes() != null ? row.getTotalMinutes() : 0)); }
        } else {
            List<TimeByUserRow> rows = reportStatisticsMapper.selectTimeByUser(projectIds, startStr, endStr);
            totalMinutes = rows.stream().mapToInt(r -> r.getTotalMinutes() != null ? r.getTotalMinutes() : 0).sum();
            for (TimeByUserRow row : rows) { labels.add(row.getUserName()); data.add((long) (row.getTotalMinutes() != null ? row.getTotalMinutes() : 0)); }
        }

        result.setLabels(labels);
        result.setData(data);
        result.setTotal(totalMinutes);

        // 每日工时趋势
        List<TimeTrendRow> trendRows = reportStatisticsMapper.selectTimeTrend(projectIds, startStr, endStr);
        Map<String, Integer> trendByDay = new HashMap<>();
        for (TimeTrendRow row : trendRows) { trendByDay.put(row.getWorkDate(), row.getTotalMinutes() != null ? row.getTotalMinutes() : 0); }
        List<String> trendDates = new ArrayList<>();
        List<Number> trendMinutes = new ArrayList<>();
        java.time.LocalDate current = startDate;
        while (!current.isAfter(endDate)) { trendDates.add(current.toString()); trendMinutes.add(trendByDay.getOrDefault(current.toString(), 0)); current = current.plusDays(1); }
        result.setDates(trendDates);

        ReportExecuteResultVO.TimeSeriesData trendSeries = new ReportExecuteResultVO.TimeSeriesData();
        trendSeries.setName("每日工时(分钟)");
        trendSeries.setColor("#58a6ff");
        trendSeries.setData(trendMinutes);
        trendSeries.setSeriesType("area");
        result.setSeries(List.of(trendSeries));

        String groupByLabel = switch (groupBy) { case "project" -> "按项目"; case "work_type" -> "按工作类型"; case "issue" -> "按工单"; default -> "按负责人"; };
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalMinutes", totalMinutes);
        summary.put("totalHours", Math.round(totalMinutes / 60.0 * 100.0) / 100.0);
        summary.put("groupBy", groupByLabel);
        summary.put("itemCount", labels.size());
        summary.put("dateRange", startStr + " ~ " + endStr);
        result.setSummary(summary);
    }

    private void executeEstimationReport(ReportExecuteResultVO result, List<Long> projectIds) {
        result.setChartType("bar_horizontal");
        List<EstimationSummaryRow> summaryRows = reportStatisticsMapper.selectEstimationSummary(projectIds);

        double totalEstimated = 0;
        double totalSpent = 0;
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();

        for (EstimationSummaryRow row : summaryRows) {
            double est = row.getEstimatedHoursSum() != null ? row.getEstimatedHoursSum() : 0;
            double spt = row.getSpentHoursSum() != null ? row.getSpentHoursSum() : 0;
            totalEstimated += est;
            totalSpent += spt;
            labels.add(row.getProjectName());
            data.add(Math.round(spt * 100) / 100L);
        }

        result.setLabels(labels);
        result.setData(data);
        result.setTotal(Math.round(totalSpent));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEstimatedHours", Math.round(totalEstimated * 100.0) / 100.0);
        summary.put("totalSpentHours", Math.round(totalSpent * 100.0) / 100.0);
        summary.put("overallDeviationRate", totalEstimated > 0 ? Math.round((totalSpent / totalEstimated - 1) * 1000.0) / 1000.0 : 0);
        summary.put("projectCount", summaryRows.size());
        result.setSummary(summary);
    }

    // ─── 工具方法 ────────────────────────────────────────

    private Long findActiveSprintId(List<Long> projectIds) {
        LambdaQueryWrapper<Sprint> query = new LambdaQueryWrapper<>();
        query.eq(Sprint::getStatus, SprintStatus.ACTIVE);
        if (projectIds != null && !projectIds.isEmpty()) { query.in(Sprint::getProjectId, projectIds); }
        query.last("LIMIT 1");
        Sprint sprint = sprintMapper.selectOne(query);
        return sprint != null ? sprint.getId() : null;
    }

    @SuppressWarnings("unchecked")
    List<Long> resolveIssueFilterIds(ReportConfig config, List<Long> projectIds) {
        String issueFilter = config.getIssueFilter();
        if (issueFilter == null || issueFilter.isBlank()) return null;
        try {
            List<Map<String, Object>> filters = objectMapper.readValue(issueFilter,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            if (filters.isEmpty()) return null;
            return queryExecutor.executeFilterToIds(filters, projectIds);
        } catch (Exception e) {
            log.warn("Failed to parse issueFilter in report config: {}", issueFilter, e);
            return null;
        }
    }

    Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { return Map.of(); }
    }

    Map<String, Object> buildFilterSummary(ReportConfig config) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (config.getTimeRange() != null) {
            ReportConfig.ReportTimeRange tr = config.getTimeRange();
            if ("dynamic".equals(tr.getType())) { summary.put("timeRange", tr.getPreset()); }
            else { summary.put("timeRange", tr.getStartDate() + " ~ " + tr.getEndDate()); }
            summary.put("timeField", tr.getField());
        }
        if (config.getIssueFilter() != null && !config.getIssueFilter().isBlank()) { summary.put("issueFilter", config.getIssueFilter()); }
        ReportConfig.ReportFilters filters = config.getFilters();
        if (filters != null) {
            if (filters.getStatuses() != null) summary.put("statuses", filters.getStatuses());
            if (filters.getStatusesExclude() != null) summary.put("statusesExclude", filters.getStatusesExclude());
            if (filters.getPriorities() != null) summary.put("priorities", filters.getPriorities());
            if (filters.getIssueTypes() != null) summary.put("issueTypes", filters.getIssueTypes());
            if (filters.getAssignees() != null) summary.put("assignees", filters.getAssignees());
            if (filters.getSprintId() != null) summary.put("sprintId", filters.getSprintId());
            if (filters.getStatusClosed() != null) summary.put("statusClosed", filters.getStatusClosed());
            if (Boolean.TRUE.equals(filters.getUnassigned())) summary.put("unassigned", true);
            if (Boolean.TRUE.equals(filters.getOverdue())) summary.put("overdue", true);
            if (Boolean.TRUE.equals(filters.getActiveSprint())) summary.put("activeSprint", true);
        }
        return summary;
    }
}
