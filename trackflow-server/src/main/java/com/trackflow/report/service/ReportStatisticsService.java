package com.trackflow.report.service;

import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.query.engine.QueryExecutor;
import com.trackflow.report.mapper.ReportStatisticsMapper;
import com.trackflow.report.mapper.result.*;
import com.trackflow.report.vo.*;
import com.trackflow.sprint.service.SprintService;
import com.trackflow.workitemattr.service.WorkItemAttributeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 报表统计服务 — SQL 聚合 + Redis 缓存
 * <p>
 * 所有统计计算在数据库层完成（GROUP BY），不加载原始工单到内存。
 * Dashboard 数据使用 Redis 短期缓存（90 秒 TTL），避免重复计算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportStatisticsService {

    private final ReportStatisticsMapper reportStatisticsMapper;
    private final SprintService sprintService;
    private final StatusCacheHelper statusCacheHelper;
    private final IssueStatusMapper issueStatusMapper;
    private final ProjectService projectService;
    private final WorkItemAttributeService workItemAttributeService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final QueryExecutor queryExecutor;

    private static final String CACHE_PREFIX = "report:dashboard:";
    private static final long CACHE_TTL_SECONDS = 90;

    /** resolution-time 端点允许的 groupBy 值（对应 Mapper XML 中的 &lt;when&gt; 分支） */
    private static final Set<String> RESOLUTION_TIME_GROUP_BY_VALUES = Set.of("type", "priority", "assignee");

    /** 趋势图最大允许天数（防止超大范围请求导致 DoS） */
    private static final int TREND_MAX_DAYS = 365;
    /** 解决时间按天模式最大允许天数 */
    private static final int RESOLUTION_TIME_MAX_DAYS = 180;

    // ─── Dashboard (main entry) ──────────────────────────────────────────

    /**
     * 获取仪表盘全量数据（一次请求，前端缓存分发）
     * 支持 Redis 短期缓存（90s TTL）
     */
    public DashboardVO getDashboardData(Long projectId, Long sprintId, LocalDate startDate, LocalDate endDate, Long userId, List<Long> issueIds) {
        List<Long> projectIds = resolveProjectIds(projectId, userId);

        // 当有 Issue filter 时，跳过缓存（filter 组合太多，缓存命中率低）
        if (issueIds == null) {
            // 尝试从缓存获取（key 包含 projectIds hash，确保不同权限用户缓存隔离）
            String cacheKey = buildCacheKey(projectId, sprintId, startDate, endDate, projectIds);
            DashboardVO cached = getFromCache(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        // 缓存未命中，构建数据
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        List<Long> closedStatusIds = new ArrayList<>(statusCacheHelper.getClosedStatusIds());

        DashboardVO dashboard = new DashboardVO();
        dashboard.setStatusDistribution(buildStatusDistribution(projectIds, sprintId, issueIds));
        dashboard.setPriorityDistribution(buildPriorityDistribution(projectIds, sprintId, issueIds));
        dashboard.setTypeDistribution(buildTypeDistribution(projectIds, sprintId, issueIds));
        dashboard.setWorkload(buildWorkload(projectIds, sprintId, closedStatusIds, issueIds));
        dashboard.setTrend(buildTrend(projectIds, startDate, endDate, issueIds));
        if (sprintId != null) {
            dashboard.setBurndown(buildBurndown(projectId, sprintId));
        }
        dashboard.setOverview(buildOverview(projectIds, sprintId, closedStatusIds, issueIds));
        // 跨项目对比：仅"全部项目"模式下（projectId == null 且有多个项目）返回
        if (projectId == null) {
            dashboard.setProjectComparison(buildProjectComparison(projectIds, closedStatusIds, issueIds));
        }
        // 累积流图和解决时间分析
        dashboard.setCumulativeFlow(buildCumulativeFlow(projectIds, startDate, endDate, issueIds));
        dashboard.setResolutionTime(buildResolutionTime(projectIds, startDate, endDate, null, issueIds));

        // 只有无 filter 时才写入缓存
        if (issueIds == null) {
            String cacheKey = buildCacheKey(projectId, sprintId, startDate, endDate, projectIds);
            putToCache(cacheKey, dashboard);
        }

        return dashboard;
    }

    // ─── Public endpoints (single chart) ────────────────────────────────

    /**
     * 获取概览统计（总数/开放/已关闭/未分配/逾期/完成率）。
     * <p>
     * 供 DashboardService 等跨模块调用，确保统计口径统一。
     *
     * @param projectIds      项目 ID 列表
     * @param sprintId        Sprint 过滤（可选）
     * @return 概览统计 VO
     */
    public OverviewVO getOverview(List<Long> projectIds, Long sprintId) {
        List<Long> closedStatusIds = new ArrayList<>(statusCacheHelper.getClosedStatusIds());
        return buildOverview(projectIds, sprintId, closedStatusIds, null);
    }

    public StatusDistributionVO getStatusDistribution(Long projectId, Long sprintId) {
        return buildStatusDistribution(List.of(projectId), sprintId, null);
    }

    public PriorityDistributionVO getPriorityDistribution(Long projectId, Long sprintId) {
        return buildPriorityDistribution(List.of(projectId), sprintId, null);
    }

    public TypeDistributionVO getTypeDistribution(Long projectId, Long sprintId) {
        return buildTypeDistribution(List.of(projectId), sprintId, null);
    }

    public WorkloadVO getWorkload(Long projectId, Long sprintId) {
        List<Long> closedStatusIds = new ArrayList<>(statusCacheHelper.getClosedStatusIds());
        return buildWorkload(List.of(projectId), sprintId, closedStatusIds, null);
    }

    public TrendVO getTrend(Long projectId, LocalDate startDate, LocalDate endDate) {
        return buildTrend(List.of(projectId), startDate, endDate, null);
    }

    public BurndownVO getBurndown(Long projectId, Long sprintId) {
        return buildBurndown(projectId, sprintId, "issue_count");
    }

    public BurndownVO getBurndown(Long projectId, Long sprintId, String calculation) {
        return buildBurndown(projectId, sprintId, calculation);
    }

    public CumulativeFlowVO getCumulativeFlow(Long projectId, LocalDate startDate, LocalDate endDate) {
        return buildCumulativeFlow(projectId != null ? List.of(projectId) : null, startDate, endDate, null);
    }

    public ResolutionTimeVO getResolutionTime(Long projectId, LocalDate startDate, LocalDate endDate, String groupBy) {
        return buildResolutionTime(projectId != null ? List.of(projectId) : null, startDate, endDate, groupBy, null);
    }

    // ─── 可创建报表体系桥接方法（供 ReportService 调用） ─────────

    /**
     * 获取燃尽图数据（供可创建报表使用）
     */
    public BurndownVO getBurndownData(Long projectId, Long sprintId) {
        return buildBurndown(projectId, sprintId);
    }

    /**
     * 获取累积流图数据（支持多项目范围）
     */
    public CumulativeFlowVO getCumulativeFlowData(List<Long> projectIds, LocalDate startDate, LocalDate endDate) {
        return buildCumulativeFlow(projectIds, startDate, endDate, null);
    }

    /**
     * 获取累积流图数据（支持多项目范围 + Issue 筛选）
     */
    public CumulativeFlowVO getCumulativeFlowData(List<Long> projectIds, LocalDate startDate, LocalDate endDate, List<Long> issueIds) {
        return buildCumulativeFlow(projectIds, startDate, endDate, issueIds);
    }

    /**
     * 获取解决时间分析数据（支持多项目范围 + 分组）
     */
    public ResolutionTimeVO getResolutionTimeData(List<Long> projectIds, LocalDate startDate, LocalDate endDate, String groupBy) {
        return buildResolutionTime(projectIds, startDate, endDate, groupBy, null);
    }

    /**
     * 获取解决时间分析数据（支持多项目范围 + 分组 + Issue 筛选）
     */
    public ResolutionTimeVO getResolutionTimeData(List<Long> projectIds, LocalDate startDate, LocalDate endDate, String groupBy, List<Long> issueIds) {
        return buildResolutionTime(projectIds, startDate, endDate, groupBy, issueIds);
    }

    // ─── Average Issue Age ───────────────────────────────────────────────

    /**
     * 获取平均工单年龄（状态停留时间）趋势数据
     *
     * @param projectIds      项目范围
     * @param startDate       开始日期
     * @param endDate         结束日期
     * @param trackedStatuses 要追踪的状态名称列表
     * @param granularity     时间粒度：day/week/month
     * @param movingPeriod    滑动窗口大小（天数）
     * @param issueIds        Issue 筛选范围（可选）
     */
    public AverageIssueAgeVO getAverageIssueAgeData(List<Long> projectIds, LocalDate startDate, LocalDate endDate,
                                                     List<String> trackedStatuses, String granularity,
                                                     Integer movingPeriod, List<Long> issueIds) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);
        if (trackedStatuses == null || trackedStatuses.isEmpty()) {
            // 默认追踪所有非关闭状态
            trackedStatuses = List.of("Open", "In Progress", "Code Review", "Testing");
        }
        if (granularity == null || granularity.isBlank()) granularity = "day";
        if (movingPeriod == null || movingPeriod < 1) movingPeriod = 7;

        // 防止超大日期范围
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > TREND_MAX_DAYS) {
            startDate = endDate.minusDays(TREND_MAX_DAYS);
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<IssueAgeTrendRow> rows = reportStatisticsMapper.selectIssueAgeTrend(
                projectIds, start, end, trackedStatuses, granularity, issueIds);

        // 构建结果
        List<String> dates = new ArrayList<>();
        List<Double> avgAgeHours = new ArrayList<>();
        List<Long> outflowCount = new ArrayList<>();
        List<Long> stayingCount = new ArrayList<>();

        for (IssueAgeTrendRow row : rows) {
            dates.add(row.getPeriod());
            avgAgeHours.add(row.getAvgAgeHours());
            outflowCount.add(row.getOutflowCount() != null ? row.getOutflowCount() : 0L);
            stayingCount.add(row.getStayingCount() != null ? row.getStayingCount() : 0L);
        }

        // 计算 Moving Average / Min / Max
        List<Double> movingAvgHours = computeMovingAverage(avgAgeHours, movingPeriod);
        List<Double> movingMinHours = computeMovingMin(avgAgeHours, movingPeriod);
        List<Double> movingMaxHours = computeMovingMax(avgAgeHours, movingPeriod);

        AverageIssueAgeVO vo = new AverageIssueAgeVO();
        vo.setDates(dates);
        vo.setAvgAgeHours(avgAgeHours);
        vo.setMovingAvgHours(movingAvgHours);
        vo.setMovingMinHours(movingMinHours);
        vo.setMovingMaxHours(movingMaxHours);
        vo.setOutflowCount(outflowCount);
        vo.setStayingCount(stayingCount);
        vo.setTrackedStatuses(trackedStatuses);
        return vo;
    }

    /**
     * 计算滑动平均值
     */
    private List<Double> computeMovingAverage(List<Double> values, int window) {
        List<Double> result = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - window + 1);
            double sum = 0;
            int count = 0;
            for (int j = start; j <= i; j++) {
                if (values.get(j) != null) {
                    sum += values.get(j);
                    count++;
                }
            }
            result.add(count > 0 ? sum / count : null);
        }
        return result;
    }

    /**
     * 计算滑动最小值
     */
    private List<Double> computeMovingMin(List<Double> values, int window) {
        List<Double> result = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - window + 1);
            Double min = null;
            for (int j = start; j <= i; j++) {
                if (values.get(j) != null) {
                    if (min == null || values.get(j) < min) {
                        min = values.get(j);
                    }
                }
            }
            result.add(min);
        }
        return result;
    }

    /**
     * 计算滑动最大值
     */
    private List<Double> computeMovingMax(List<Double> values, int window) {
        List<Double> result = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - window + 1);
            Double max = null;
            for (int j = start; j <= i; j++) {
                if (values.get(j) != null) {
                    if (max == null || values.get(j) > max) {
                        max = values.get(j);
                    }
                }
            }
            result.add(max);
        }
        return result;
    }

    /**
     * 获取状态转换统计数据
     * 基于 issue_activity 表中的状态变更事件进行聚合
     */
    public List<com.trackflow.report.vo.ReportExecuteResultVO.StateTransitionItem> getStateTransitionData(
            List<Long> projectIds, LocalDateTime start, LocalDateTime end) {
        return getStateTransitionData(projectIds, start, end, null);
    }

    /**
     * 获取状态转换统计数据（支持 Issue 筛选）
     * 基于 issue_activity 表中的状态变更事件进行聚合
     */
    public List<com.trackflow.report.vo.ReportExecuteResultVO.StateTransitionItem> getStateTransitionData(
            List<Long> projectIds, LocalDateTime start, LocalDateTime end, List<Long> issueIds) {
        List<StateTransitionRow> rows = reportStatisticsMapper.selectStateTransitions(projectIds, start, end, issueIds);

        List<com.trackflow.report.vo.ReportExecuteResultVO.StateTransitionItem> items = new ArrayList<>();
        for (StateTransitionRow row : rows) {
            com.trackflow.report.vo.ReportExecuteResultVO.StateTransitionItem item =
                    new com.trackflow.report.vo.ReportExecuteResultVO.StateTransitionItem();
            item.setFromStatus(row.getFromStatus());
            item.setToStatus(row.getToStatus());
            item.setCount(row.getTransitionCount());
            item.setAvgDurationHours(row.getAvgDurationHours());
            items.add(item);
        }
        return items;
    }

    // ─── Internal build methods (SQL aggregation) ────────────────────────

    /**
     * 获取比率对比报表数据（双线趋势图）。
     * 根据报表类型返回不同的指标对比数据：
     * - FIXED_VS_REPORTED: 修复数 vs 新报告数
     * - VERIFIED_VS_REOPENED: 验证通过数 vs 重新打开数
     * - RESOLVED_VS_NEW: 解决数 vs (新建+重开)数
     *
     * @param reportType   报表类型
     * @param projectIds   项目范围
     * @param startDate    开始日期
     * @param endDate      结束日期
     * @param issueIds     Issue 筛选（可选）
     * @return 包含日期序列和两条数据线的 ReportExecuteResultVO
     */
    public ReportExecuteResultVO getRateComparisonData(
            com.trackflow.report.entity.ReportType reportType,
            List<Long> projectIds,
            LocalDate startDate, LocalDate endDate,
            List<Long> issueIds) {

        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);
        if (ChronoUnit.DAYS.between(startDate, endDate) > TREND_MAX_DAYS) {
            startDate = endDate.minusDays(TREND_MAX_DAYS);
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        // 收集各类别状态的 name 和 displayName（issue_activity 中两种格式都可能存在）
        List<IssueStatus> allStatuses = issueStatusMapper.selectList(null);

        Map<LocalDate, Long> lineAByDay;
        Map<LocalDate, Long> lineBByDay;
        String lineAName;
        String lineBName;
        String lineAColor;
        String lineBColor;

        switch (reportType) {
            case FIXED_VS_REPORTED -> {
                // Line A: Fixed (转换到 done 类状态)
                List<String> doneNames = collectStatusNames(allStatuses, "done");
                List<RateComparisonRow> fixedRows = reportStatisticsMapper.selectFixedTrend(
                        projectIds, start, end, doneNames, issueIds);
                lineAByDay = rowsToMap(fixedRows);
                lineAName = "修复数";
                lineAColor = "#3fb950";

                // Line B: Reported (新创建的 Issue)
                List<TrendRow> createdRows = reportStatisticsMapper.selectCreatedTrend(
                        projectIds, start, end, issueIds);
                lineBByDay = trendRowsToMap(createdRows);
                lineBName = "报告数";
                lineBColor = "#f85149";
            }
            case VERIFIED_VS_REOPENED -> {
                // Line A: Verified (从测试状态转换到 done 状态)
                List<String> testingNames = collectStatusNamesByCode(allStatuses, Set.of("testing", "no_test"));
                List<String> doneNames = collectStatusNames(allStatuses, "done");
                List<RateComparisonRow> verifiedRows = reportStatisticsMapper.selectVerifiedTrend(
                        projectIds, start, end, testingNames, doneNames, issueIds);
                lineAByDay = rowsToMap(verifiedRows);
                lineAName = "验证通过数";
                lineAColor = "#3fb950";

                // Line B: Reopened (转换到 reopened 状态)
                List<String> reopenedNames = collectStatusNamesByCode(allStatuses, Set.of("reopened"));
                List<RateComparisonRow> reopenedRows = reportStatisticsMapper.selectReopenedTrend(
                        projectIds, start, end, reopenedNames, issueIds);
                lineBByDay = rowsToMap(reopenedRows);
                lineBName = "重新打开数";
                lineBColor = "#f85149";
            }
            case RESOLVED_VS_NEW -> {
                // Line A: Resolved (resolved_at 被设置)
                List<TrendRow> resolvedRows = reportStatisticsMapper.selectResolvedTrend(
                        projectIds, start, end, issueIds);
                lineAByDay = trendRowsToMap(resolvedRows);
                lineAName = "解决数";
                lineAColor = "#3fb950";

                // Line B: New + Reopened
                List<TrendRow> createdRows = reportStatisticsMapper.selectCreatedTrend(
                        projectIds, start, end, issueIds);
                Map<LocalDate, Long> createdByDay = trendRowsToMap(createdRows);

                List<String> reopenedNames = collectStatusNamesByCode(allStatuses, Set.of("reopened"));
                List<RateComparisonRow> reopenedRows = reportStatisticsMapper.selectReopenedTrend(
                        projectIds, start, end, reopenedNames, issueIds);
                Map<LocalDate, Long> reopenedByDay = rowsToMap(reopenedRows);

                // 合并 created + reopened
                lineBByDay = new HashMap<>(createdByDay);
                reopenedByDay.forEach((day, cnt) -> lineBByDay.merge(day, cnt, Long::sum));
                lineBName = "新增+重开数";
                lineBColor = "#f85149";
            }
            default -> throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "不支持的比率对比报表类型: " + reportType.getValue());
        }

        // 构建结果
        ReportExecuteResultVO result = new ReportExecuteResultVO();
        result.setChartType("line");
        result.setCategory("timeline");

        List<String> dates = new ArrayList<>();
        List<Number> lineAData = new ArrayList<>();
        List<Number> lineBData = new ArrayList<>();
        long totalA = 0;
        long totalB = 0;

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            dates.add(current.toString());
            long aVal = lineAByDay.getOrDefault(current, 0L);
            long bVal = lineBByDay.getOrDefault(current, 0L);
            lineAData.add(aVal);
            lineBData.add(bVal);
            totalA += aVal;
            totalB += bVal;
            current = current.plusDays(1);
        }

        result.setDates(dates);

        ReportExecuteResultVO.TimeSeriesData seriesA = new ReportExecuteResultVO.TimeSeriesData();
        seriesA.setName(lineAName);
        seriesA.setColor(lineAColor);
        seriesA.setData(lineAData);
        seriesA.setSeriesType("line");

        ReportExecuteResultVO.TimeSeriesData seriesB = new ReportExecuteResultVO.TimeSeriesData();
        seriesB.setName(lineBName);
        seriesB.setColor(lineBColor);
        seriesB.setData(lineBData);
        seriesB.setSeriesType("line");

        result.setSeries(List.of(seriesA, seriesB));

        // 概览 summary
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalLineA", totalA);
        summary.put("totalLineB", totalB);
        if (totalB > 0) {
            summary.put("ratio", Math.round((double) totalA / totalB * 100.0) / 100.0);
        }
        result.setSummary(summary);

        return result;
    }

    /**
     * 收集指定 category 下所有状态的 name 和 displayName（去重、非空）。
     * issue_activity 表中 old_value/new_value 可能存储 name 或 displayName。
     */
    private List<String> collectStatusNames(List<IssueStatus> allStatuses, String category) {
        Set<String> names = new HashSet<>();
        for (IssueStatus status : allStatuses) {
            if (category.equals(status.getCategory())) {
                names.add(status.getName());
                if (status.getDisplayName() != null && !status.getDisplayName().isBlank()) {
                    names.add(status.getDisplayName());
                }
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * 收集指定 code 集合下所有状态的 name 和 displayName。
     */
    private List<String> collectStatusNamesByCode(List<IssueStatus> allStatuses, Set<String> codes) {
        Set<String> names = new HashSet<>();
        for (IssueStatus status : allStatuses) {
            if (codes.contains(status.getCode())) {
                names.add(status.getName());
                if (status.getDisplayName() != null && !status.getDisplayName().isBlank()) {
                    names.add(status.getDisplayName());
                }
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * 将 RateComparisonRow 列表转为 day->count 的 Map
     */
    private Map<LocalDate, Long> rowsToMap(List<RateComparisonRow> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (RateComparisonRow row : rows) {
            if (row.getDay() != null) {
                map.put(row.getDay(), row.getCnt() != null ? row.getCnt() : 0L);
            }
        }
        return map;
    }

    /**
     * 将 TrendRow 列表转为 day->count 的 Map
     */
    private Map<LocalDate, Long> trendRowsToMap(List<TrendRow> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (TrendRow row : rows) {
            if (row.getDay() != null) {
                map.put(row.getDay(), row.getCnt() != null ? row.getCnt() : 0L);
            }
        }
        return map;
    }

    private StatusDistributionVO buildStatusDistribution(List<Long> projectIds, Long sprintId, List<Long> issueIds) {
        List<StatusDistributionRow> rows = reportStatisticsMapper.selectStatusDistribution(projectIds, sprintId, issueIds);

        List<StatusDistributionVO.StatusItem> items = new ArrayList<>();
        long total = 0;
        for (StatusDistributionRow row : rows) {
            StatusDistributionVO.StatusItem item = new StatusDistributionVO.StatusItem();
            item.setName(row.getStatusName());
            long cnt = row.getCnt() != null ? row.getCnt() : 0L;
            item.setValue(cnt);
            item.setColor(row.getStatusColor());
            item.setCategory(row.getStatusCategory());
            items.add(item);
            total += cnt;
        }

        StatusDistributionVO vo = new StatusDistributionVO();
        vo.setItems(items);
        vo.setTotal((int) total);
        return vo;
    }

    private PriorityDistributionVO buildPriorityDistribution(List<Long> projectIds, Long sprintId, List<Long> issueIds) {
        List<PriorityDistributionRow> rows = reportStatisticsMapper.selectPriorityDistribution(projectIds, sprintId, issueIds);

        Map<String, Long> grouped = new HashMap<>();
        long total = 0;
        for (PriorityDistributionRow row : rows) {
            String name = row.getPriorityName();
            long cnt = row.getCnt() != null ? row.getCnt() : 0L;
            grouped.put(name, cnt);
            total += cnt;
        }

        List<String> priorityOrder = List.of("Critical", "High", "Normal", "Low");
        Map<String, String> priorityColors = Map.of(
                "Critical", "#f85149",
                "High", "#d29922",
                "Normal", "#58a6ff",
                "Low", "#6b7280"
        );

        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        List<String> colors = new ArrayList<>();

        for (String priority : priorityOrder) {
            labels.add(priority);
            data.add(grouped.getOrDefault(priority, 0L));
            colors.add(priorityColors.getOrDefault(priority, "#6b7280"));
        }

        PriorityDistributionVO vo = new PriorityDistributionVO();
        vo.setLabels(labels);
        vo.setData(data);
        vo.setColors(colors);
        vo.setTotal((int) total);
        return vo;
    }

    private TypeDistributionVO buildTypeDistribution(List<Long> projectIds, Long sprintId, List<Long> issueIds) {
        List<TypeDistributionRow> rows = reportStatisticsMapper.selectTypeDistribution(projectIds, sprintId, issueIds);

        Map<String, String> typeColors = Map.of(
                "Bug", "#f85149",
                "Task", "#58a6ff",
                "Feature", "#3fb950",
                "Story", "#a371f7",
                "Improvement", "#d29922"
        );

        List<TypeDistributionVO.TypeItem> items = new ArrayList<>();
        long total = 0;
        for (TypeDistributionRow row : rows) {
            TypeDistributionVO.TypeItem item = new TypeDistributionVO.TypeItem();
            String name = row.getTypeName();
            long cnt = row.getCnt() != null ? row.getCnt() : 0L;
            item.setName(name);
            item.setValue(cnt);
            item.setColor(typeColors.getOrDefault(name, "#6b7280"));
            items.add(item);
            total += cnt;
        }

        TypeDistributionVO vo = new TypeDistributionVO();
        vo.setItems(items);
        vo.setTotal((int) total);
        return vo;
    }

    private WorkloadVO buildWorkload(List<Long> projectIds, Long sprintId, List<Long> closedStatusIds, List<Long> issueIds) {
        // 如果没有关闭状态，传一个不可能的 ID 避免 SQL 语法错误
        List<Long> safeClosedIds = closedStatusIds.isEmpty() ? List.of(-1L) : closedStatusIds;
        List<WorkloadRow> rows = reportStatisticsMapper.selectWorkload(projectIds, sprintId, safeClosedIds, issueIds);

        List<WorkloadVO.WorkloadItem> items = new ArrayList<>();
        long total = 0;
        for (WorkloadRow row : rows) {
            WorkloadVO.WorkloadItem item = new WorkloadVO.WorkloadItem();
            Long assigneeId = row.getAssigneeId();
            String name = row.getAssigneeName();
            long itemTotal = row.getTotal() != null ? row.getTotal() : 0L;
            long doneCount = row.getDoneCount() != null ? row.getDoneCount() : 0L;

            item.setName(assigneeId != null ? (name != null ? name : "未知用户") : "未分配");
            item.setValue(itemTotal);
            item.setDone(doneCount);
            item.setInProgress(itemTotal - doneCount);
            items.add(item);
            total += itemTotal;
        }

        WorkloadVO vo = new WorkloadVO();
        vo.setItems(items);
        vo.setTotal((int) total);
        return vo;
    }

    private OverviewVO buildOverview(List<Long> projectIds, Long sprintId, List<Long> closedStatusIds, List<Long> issueIds) {
        List<Long> safeClosedIds = closedStatusIds.isEmpty() ? List.of(-1L) : closedStatusIds;
        OverviewRow row = reportStatisticsMapper.selectOverview(
                projectIds, sprintId, safeClosedIds, LocalDateTime.now(), issueIds);

        if (row == null) {
            OverviewVO vo = new OverviewVO();
            vo.setTotal(0L);
            vo.setOpen(0L);
            vo.setClosed(0L);
            vo.setUnassigned(0L);
            vo.setOverdue(0L);
            vo.setCompletionRate(0L);
            return vo;
        }

        long total = row.getTotal() != null ? row.getTotal() : 0L;
        long open = row.getOpenCount() != null ? row.getOpenCount() : 0L;
        long closed = row.getClosedCount() != null ? row.getClosedCount() : 0L;
        long unassigned = row.getUnassigned() != null ? row.getUnassigned() : 0L;
        long overdue = row.getOverdue() != null ? row.getOverdue() : 0L;

        OverviewVO vo = new OverviewVO();
        vo.setTotal(total);
        vo.setOpen(open);
        vo.setClosed(closed);
        vo.setUnassigned(unassigned);
        vo.setOverdue(overdue);
        vo.setCompletionRate(total > 0 ? Math.round(closed * 100.0 / total) : 0);
        return vo;
    }

    private TrendVO buildTrend(List<Long> projectIds, LocalDate startDate, LocalDate endDate, List<Long> issueIds) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        // 防止超大日期范围请求导致 DoS：截断到最近 TREND_MAX_DAYS 天
        if (ChronoUnit.DAYS.between(startDate, endDate) > TREND_MAX_DAYS) {
            startDate = endDate.minusDays(TREND_MAX_DAYS);
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<TrendRow> createdRows = reportStatisticsMapper.selectCreatedTrend(projectIds, start, end, issueIds);
        List<TrendRow> resolvedRows = reportStatisticsMapper.selectResolvedTrend(projectIds, start, end, issueIds);

        // 转为 Map 方便按日期查找
        Map<LocalDate, Long> createdByDay = new HashMap<>();
        for (TrendRow row : createdRows) {
            if (row.getDay() != null) {
                createdByDay.put(row.getDay(), row.getCnt() != null ? row.getCnt() : 0L);
            }
        }
        Map<LocalDate, Long> resolvedByDay = new HashMap<>();
        for (TrendRow row : resolvedRows) {
            if (row.getDay() != null) {
                resolvedByDay.put(row.getDay(), row.getCnt() != null ? row.getCnt() : 0L);
            }
        }

        // 填充所有日期（含无数据的日子）
        List<String> dates = new ArrayList<>();
        List<Long> createdData = new ArrayList<>();
        List<Long> resolvedData = new ArrayList<>();

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            dates.add(current.toString());
            createdData.add(createdByDay.getOrDefault(current, 0L));
            resolvedData.add(resolvedByDay.getOrDefault(current, 0L));
            current = current.plusDays(1);
        }

        TrendVO vo = new TrendVO();
        vo.setDates(dates);
        vo.setCreated(createdData);
        vo.setResolved(resolvedData);
        return vo;
    }

    private BurndownVO buildBurndown(Long projectId, Long sprintId) {
        return buildBurndown(projectId, sprintId, "issue_count");
    }

    private BurndownVO buildBurndown(Long projectId, Long sprintId, String calculation) {
        // 委托给 SprintService 的 scope-aware 算法（已优化）
        com.trackflow.sprint.vo.BurndownVO sprintBurndown = sprintService.getBurndownData(sprintId, calculation);

        BurndownVO vo = new BurndownVO();
        vo.setDates(sprintBurndown.getDates());
        vo.setIdeal(sprintBurndown.getIdealLine());
        vo.setActual(sprintBurndown.getActualLine().stream()
                .map(Integer::longValue)
                .collect(Collectors.toList()));
        vo.setSprintName(sprintBurndown.getSprintName());
        vo.setTotalIssues(sprintBurndown.getTotalIssues());
        vo.setMode(calculation);
        return vo;
    }

    private ProjectComparisonVO buildProjectComparison(List<Long> projectIds, List<Long> closedStatusIds, List<Long> issueIds) {
        List<Long> safeClosedIds = closedStatusIds.isEmpty() ? List.of(-1L) : closedStatusIds;
        List<ProjectComparisonRow> rows = reportStatisticsMapper.selectProjectComparison(
                projectIds, safeClosedIds, LocalDateTime.now(), issueIds);

        List<ProjectComparisonVO.ProjectStatItem> items = new ArrayList<>();
        for (ProjectComparisonRow row : rows) {
            ProjectComparisonVO.ProjectStatItem item = new ProjectComparisonVO.ProjectStatItem();
            item.setName(row.getProjectName());
            item.setKey(row.getProjectKey());
            long total = row.getTotal() != null ? row.getTotal() : 0L;
            long open = row.getOpenCount() != null ? row.getOpenCount() : 0L;
            long closed = row.getClosedCount() != null ? row.getClosedCount() : 0L;
            long overdue = row.getOverdue() != null ? row.getOverdue() : 0L;
            item.setTotal(total);
            item.setOpen(open);
            item.setClosed(closed);
            item.setOverdue(overdue);
            item.setCompletionRate(total > 0 ? Math.round(closed * 100.0 / total) : 0);
            items.add(item);
        }

        ProjectComparisonVO vo = new ProjectComparisonVO();
        vo.setItems(items);
        return vo;
    }

    /**
     * 累积流图最大查询天数（超过 90 天性能显著下降）
     */
    private static final int CUMULATIVE_FLOW_MAX_DAYS = 90;

    private CumulativeFlowVO buildCumulativeFlow(List<Long> projectIds, LocalDate startDate, LocalDate endDate, List<Long> issueIds) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        // 限制查询范围不超过 90 天，防止大数据量下查询超时
        if (ChronoUnit.DAYS.between(startDate, endDate) > CUMULATIVE_FLOW_MAX_DAYS) {
            startDate = endDate.minusDays(CUMULATIVE_FLOW_MAX_DAYS);
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<CumulativeFlowRow> rows = reportStatisticsMapper.selectCumulativeFlow(projectIds, start, end, issueIds);

        if (rows.isEmpty()) {
            CumulativeFlowVO empty = new CumulativeFlowVO();
            empty.setDates(List.of());
            empty.setSeries(List.of());
            return empty;
        }

        // 将扁平行数据重组为 {day -> {statusName -> count}} + 收集有序状态列表
        Map<String, Map<String, Long>> dayStatusCounts = new LinkedHashMap<>();
        Map<String, String> statusColorMap = new LinkedHashMap<>();
        Map<String, Integer> statusSortMap = new LinkedHashMap<>();

        for (CumulativeFlowRow row : rows) {
            String day = row.getDay();
            String statusName = row.getStatusName();
            String color = row.getStatusColor();
            int sortOrder = row.getSortOrder() != null ? row.getSortOrder() : 0;
            long cnt = row.getCnt() != null ? row.getCnt() : 0L;

            dayStatusCounts.computeIfAbsent(day, k -> new LinkedHashMap<>())
                    .put(statusName, cnt);
            statusColorMap.putIfAbsent(statusName, color);
            statusSortMap.putIfAbsent(statusName, sortOrder);
        }

        // 按 sort_order 排列状态
        List<String> orderedStatuses = statusSortMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> dates = new ArrayList<>(dayStatusCounts.keySet());

        // 构建 series
        List<CumulativeFlowVO.StatusSeries> seriesList = new ArrayList<>();
        for (String statusName : orderedStatuses) {
            CumulativeFlowVO.StatusSeries series = new CumulativeFlowVO.StatusSeries();
            series.setName(statusName);
            series.setColor(statusColorMap.getOrDefault(statusName, "#6b7280"));

            List<Long> data = new ArrayList<>();
            for (String day : dates) {
                Map<String, Long> dayCounts = dayStatusCounts.get(day);
                data.add(dayCounts != null ? dayCounts.getOrDefault(statusName, 0L) : 0L);
            }
            series.setData(data);
            seriesList.add(series);
        }

        CumulativeFlowVO vo = new CumulativeFlowVO();
        vo.setDates(dates);
        vo.setSeries(seriesList);
        return vo;
    }

    private ResolutionTimeVO buildResolutionTime(List<Long> projectIds, LocalDate startDate, LocalDate endDate, String groupBy, List<Long> issueIds) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        // 防止超大日期范围请求导致 DoS：截断到最近 RESOLUTION_TIME_MAX_DAYS 天
        if (ChronoUnit.DAYS.between(startDate, endDate) > RESOLUTION_TIME_MAX_DAYS) {
            startDate = endDate.minusDays(RESOLUTION_TIME_MAX_DAYS);
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        // 判断是否用周分组：如果日期范围 >= 28 天则按周
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        boolean useWeekGrouping = daysBetween >= 28;

        List<ResolutionTimeTrendRow> rows = reportStatisticsMapper.selectResolutionTimeTrend(
                projectIds, start, end, useWeekGrouping, issueIds);

        List<String> dates = new ArrayList<>();
        List<Double> avgHours = new ArrayList<>();
        List<Double> medianHours = new ArrayList<>();
        List<Double> p90Hours = new ArrayList<>();
        List<Long> resolvedCount = new ArrayList<>();

        if (!useWeekGrouping) {
            // 按天分组时填充所有日期（含无数据的日子）
            Map<String, ResolutionTimeTrendRow> rowsByDay = new LinkedHashMap<>();
            for (ResolutionTimeTrendRow row : rows) {
                rowsByDay.put(row.getPeriod(), row);
            }

            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                String dayStr = current.toString();
                dates.add(dayStr);
                ResolutionTimeTrendRow row = rowsByDay.get(dayStr);
                if (row != null) {
                    avgHours.add(row.getAvgHours());
                    medianHours.add(row.getMedianHours());
                    p90Hours.add(row.getP90Hours());
                    resolvedCount.add(row.getResolvedCount() != null ? row.getResolvedCount() : 0L);
                } else {
                    avgHours.add(null);
                    medianHours.add(null);
                    p90Hours.add(null);
                    resolvedCount.add(0L);
                }
                current = current.plusDays(1);
            }
        } else {
            // 按周分组直接使用 SQL 返回的结果
            for (ResolutionTimeTrendRow row : rows) {
                dates.add(row.getPeriod());
                avgHours.add(row.getAvgHours());
                medianHours.add(row.getMedianHours());
                p90Hours.add(row.getP90Hours());
                resolvedCount.add(row.getResolvedCount() != null ? row.getResolvedCount() : 0L);
            }
        }

        // 分组明细 — 校验 groupBy 白名单（仅支持 type/priority/assignee）
        List<ResolutionTimeVO.GroupDetail> groupDetails = new ArrayList<>();
        if (groupBy != null) {
            if (!RESOLUTION_TIME_GROUP_BY_VALUES.contains(groupBy)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "不支持的分组维度: " + groupBy + "，允许值: " + String.join(", ", RESOLUTION_TIME_GROUP_BY_VALUES));
            }
            List<ResolutionTimeGroupRow> groupRows = reportStatisticsMapper.selectResolutionTimeByGroup(
                    projectIds, start, end, groupBy, issueIds);
            for (ResolutionTimeGroupRow row : groupRows) {
                ResolutionTimeVO.GroupDetail detail = new ResolutionTimeVO.GroupDetail();
                detail.setName(row.getGroupName());
                detail.setAvgHours(row.getAvgHours());
                detail.setMedianHours(row.getMedianHours());
                detail.setCount(row.getCnt() != null ? row.getCnt() : 0L);
                groupDetails.add(detail);
            }
        }

        ResolutionTimeVO vo = new ResolutionTimeVO();
        vo.setDates(dates);
        vo.setAvgHours(avgHours);
        vo.setMedianHours(medianHours);
        vo.setP90Hours(p90Hours);
        vo.setResolvedCount(resolvedCount);
        vo.setGroupDetails(groupDetails);
        return vo;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private List<Long> resolveProjectIds(Long projectId, Long userId) {
        if (projectId != null) {
            return List.of(projectId);
        }
        // 获取用户可访问项目 ID
        List<Long> ids = projectService.getAccessibleProjectIds(userId);
        if (ids == null) {
            // 系统管理员：改为显式查询所有活跃项目 ID（走索引而非全表扫描）
            ids = projectService.getAllActiveProjectIds();
        }
        // 防护：用户无任何可访问项目时，使用 sentinel 值确保 SQL IN (-1) 返回空集
        // 而非跳过项目过滤条件导致全表扫描数据泄露
        if (ids.isEmpty()) {
            return List.of(-1L);
        }
        return ids;
    }

    // ─── Redis cache ─────────────────────────────────────────────────────

    /**
     * 构建缓存 key（包含项目集合 hash，确保不同权限范围的用户缓存隔离）。
     * <p>
     * 格式：
     * - 单项目模式：report:dashboard:{projectId}:{sprintId|none}:{startDate}:{endDate}
     * - 全部项目模式：report:dashboard:all_{projectIdsHash}:{sprintId|none}:{startDate}:{endDate}
     * <p>
     * 设计决策：
     * - 单项目模式下 projectId 即可唯一标识数据范围，无需 hash
     * - 全部项目模式下不同用户可访问的项目集合不同，用排序后的 hashCode 区分
     * - 相同项目集合的用户共享缓存（减少 Redis 存储），不同集合互相隔离（安全）
     */
    private String buildCacheKey(Long projectId, Long sprintId, LocalDate startDate, LocalDate endDate, List<Long> projectIds) {
        String projectPart;
        if (projectId != null) {
            projectPart = String.valueOf(projectId);
        } else {
            // 用 projectIds 排序后的 hashCode 作为缓存隔离维度
            int hash = (projectIds == null || projectIds.isEmpty())
                    ? 0
                    : projectIds.stream().sorted().toList().hashCode();
            projectPart = "all_" + hash;
        }
        return CACHE_PREFIX + projectPart + ":"
                + (sprintId != null ? sprintId : "none") + ":"
                + (startDate != null ? startDate : "null") + ":"
                + (endDate != null ? endDate : "null");
    }

    private DashboardVO getFromCache(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json != null) {
                return objectMapper.readValue(json, DashboardVO.class);
            }
        } catch (Exception e) {
            log.debug("Report cache read failed (key={}): {}", cacheKey, e.getMessage());
        }
        return null;
    }

    private void putToCache(String cacheKey, DashboardVO data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Report cache write failed (key={}): {}", cacheKey, e.getMessage());
        }
    }

    // ─── 时间报表 ──────────────────────────────────────────────────────

    /**
     * 获取时间报表多维视图数据（Per Issue / Per User / Per Work Item）
     *
     * @param projectId 项目 ID（可选，null 表示全部项目）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param viewType  视图类型：issue / user / work_item
     * @param page      页码（从 1 开始）
     * @param pageSize  每页大小
     * @param userId    当前用户 ID
     */
    public TimeReportGroupedVO getTimeReportGrouped(
            Long projectId, LocalDate startDate, LocalDate endDate,
            String viewType, int page, int pageSize, Long userId) {

        List<Long> projectIds = resolveProjectIds(projectId, userId);

        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        String startStr = startDate.toString();
        String endStr = endDate.toString();
        int offset = (page - 1) * pageSize;

        TimeReportGroupedVO vo = new TimeReportGroupedVO();
        vo.setViewType(viewType);
        vo.setPage(page);
        vo.setPageSize(pageSize);

        switch (viewType) {
            case "issue" -> {
                long total = reportStatisticsMapper.countTimeByIssue(projectIds, startStr, endStr);
                List<TimeIssueGroupRow> rows =
                        reportStatisticsMapper.selectTimeByIssue(projectIds, startStr, endStr, pageSize, offset);

                int totalMinutes = rows.stream().mapToInt(r -> r.getTotalMinutes() != null ? r.getTotalMinutes() : 0).sum();
                vo.setTotalMinutes(totalMinutes);
                vo.setTotalCount(total);
                vo.setIssueGroups(rows.stream().map(r -> {
                    TimeReportGroupedVO.IssueGroupItem item = new TimeReportGroupedVO.IssueGroupItem();
                    item.setIssueId(r.getIssueId() != null ? String.valueOf(r.getIssueId()) : null);
                    item.setIssueKey(r.getIssueKey());
                    item.setTitle(r.getTitle());
                    item.setProjectName(r.getProjectName());
                    item.setStatusName(r.getStatusName());
                    item.setTotalMinutes(r.getTotalMinutes() != null ? r.getTotalMinutes() : 0);
                    item.setEntryCount(r.getEntryCount() != null ? r.getEntryCount() : 0);
                    return item;
                }).collect(Collectors.toList()));
            }
            case "user" -> {
                long total = reportStatisticsMapper.countTimeByUser(projectIds, startStr, endStr);
                List<TimeByUserRow> rows =
                        reportStatisticsMapper.selectTimeByUser(projectIds, startStr, endStr);

                int totalMinutes = rows.stream().mapToInt(r -> r.getTotalMinutes() != null ? r.getTotalMinutes() : 0).sum();
                vo.setTotalMinutes(totalMinutes);
                vo.setTotalCount(total);

                // 分页截取
                List<TimeByUserRow> pageRows =
                        rows.stream().skip(offset).limit(pageSize).collect(Collectors.toList());

                // 获取每个用户的项目分布（使用交叉数据）
                List<TimeCrossProjectUserRow> crossRows =
                        reportStatisticsMapper.selectTimeCrossProjectUser(projectIds, startStr, endStr);
                Map<String, List<TimeReportGroupedVO.ProjectSummaryItem>> userProjectMap = new HashMap<>();
                for (TimeCrossProjectUserRow crossRow : crossRows) {
                    TimeReportGroupedVO.ProjectSummaryItem pItem = new TimeReportGroupedVO.ProjectSummaryItem();
                    pItem.setProjectName(crossRow.getProjectName());
                    pItem.setMinutes(crossRow.getTotalMinutes() != null ? crossRow.getTotalMinutes() : 0);
                    userProjectMap.computeIfAbsent(crossRow.getUserName(), k -> new ArrayList<>()).add(pItem);
                }

                vo.setUserGroups(pageRows.stream().map(r -> {
                    TimeReportGroupedVO.UserGroupItem item = new TimeReportGroupedVO.UserGroupItem();
                    item.setUserId(r.getUserId() != null ? String.valueOf(r.getUserId()) : null);
                    item.setUserName(r.getUserName());
                    item.setTotalMinutes(r.getTotalMinutes() != null ? r.getTotalMinutes() : 0);
                    item.setByProject(userProjectMap.getOrDefault(r.getUserName(), List.of()));
                    return item;
                }).collect(Collectors.toList()));
            }
            case "work_item" -> {
                Long workTypeAttrId = workItemAttributeService.getWorkTypeAttributeId();
                long total = reportStatisticsMapper.countTimeWorkItems(projectIds, startStr, endStr);
                List<TimeWorkItemRow> rows =
                        reportStatisticsMapper.selectTimeWorkItems(projectIds, startStr, endStr, workTypeAttrId, pageSize, offset);

                int totalMinutes = rows.stream().mapToInt(r -> r.getMinutes() != null ? r.getMinutes() : 0).sum();
                vo.setTotalMinutes(totalMinutes);
                vo.setTotalCount(total);
                vo.setWorkItems(rows.stream().map(r -> {
                    TimeReportGroupedVO.WorkItemDetail detail = new TimeReportGroupedVO.WorkItemDetail();
                    detail.setEntryId(r.getEntryId() != null ? String.valueOf(r.getEntryId()) : null);
                    detail.setWorkDate(r.getWorkDate());
                    detail.setUserName(r.getUserName());
                    detail.setIssueKey(r.getIssueKey());
                    detail.setIssueTitle(r.getIssueTitle());
                    detail.setProjectName(r.getProjectName());
                    detail.setWorkType(r.getWorkType());
                    detail.setMinutes(r.getMinutes() != null ? r.getMinutes() : 0);
                    detail.setDescription(r.getDescription());
                    return detail;
                }).collect(Collectors.toList()));
            }
            default -> throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "不支持的视图类型: " + viewType + "，允许值: issue, user, work_item");
        }

        return vo;
    }

    /**
     * 获取时间报表数据：按人员/项目/工作类型汇总工时，含趋势和交叉维度
     */
    public TimeReportVO getTimeReport(Long projectId, LocalDate startDate, LocalDate endDate, Long userId) {
        List<Long> projectIds = resolveProjectIds(projectId, userId);

        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        String startStr = startDate.toString();
        String endStr = endDate.toString();

        TimeReportVO vo = new TimeReportVO();

        // 按人员
        List<TimeByUserRow> byUserRows = reportStatisticsMapper.selectTimeByUser(projectIds, startStr, endStr);
        int totalMinutes = byUserRows.stream().mapToInt(r -> r.getTotalMinutes() != null ? r.getTotalMinutes() : 0).sum();
        vo.setTotalMinutes(totalMinutes);
        vo.setByUser(byUserRows.stream().map(r -> {
            TimeReportVO.GroupItem item = new TimeReportVO.GroupItem();
            item.setName(r.getUserName());
            int minutes = r.getTotalMinutes() != null ? r.getTotalMinutes() : 0;
            item.setMinutes(minutes);
            item.setPercentage(totalMinutes > 0 ? Math.round(minutes * 1000.0 / totalMinutes) / 10.0 : 0);
            return item;
        }).collect(Collectors.toList()));

        // 按项目
        List<TimeByProjectRow> byProjectRows = reportStatisticsMapper.selectTimeByProject(projectIds, startStr, endStr);
        vo.setByProject(byProjectRows.stream().map(r -> {
            TimeReportVO.GroupItem item = new TimeReportVO.GroupItem();
            item.setName(r.getProjectName());
            int minutes = r.getTotalMinutes() != null ? r.getTotalMinutes() : 0;
            item.setMinutes(minutes);
            item.setPercentage(totalMinutes > 0 ? Math.round(minutes * 1000.0 / totalMinutes) / 10.0 : 0);
            return item;
        }).collect(Collectors.toList()));

        // 按工作类型
        List<TimeByWorkTypeRow> byTypeRows = reportStatisticsMapper.selectTimeByWorkType(
                projectIds, startStr, endStr, workItemAttributeService.getWorkTypeAttributeId());
        vo.setByWorkType(byTypeRows.stream().map(r -> {
            TimeReportVO.GroupItem item = new TimeReportVO.GroupItem();
            item.setName(r.getWorkType());
            int minutes = r.getTotalMinutes() != null ? r.getTotalMinutes() : 0;
            item.setMinutes(minutes);
            item.setPercentage(totalMinutes > 0 ? Math.round(minutes * 1000.0 / totalMinutes) / 10.0 : 0);
            return item;
        }).collect(Collectors.toList()));

        // 每日趋势（填充完整日期范围，缺失日期用 0）
        List<TimeTrendRow> trendRows = reportStatisticsMapper.selectTimeTrend(projectIds, startStr, endStr);
        Map<String, Integer> trendByDay = new HashMap<>();
        for (TimeTrendRow row : trendRows) {
            trendByDay.put(row.getWorkDate(), row.getTotalMinutes() != null ? row.getTotalMinutes() : 0);
        }
        List<String> trendDates = new ArrayList<>();
        List<Integer> trendMinutes = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            trendDates.add(current.toString());
            trendMinutes.add(trendByDay.getOrDefault(current.toString(), 0));
            current = current.plusDays(1);
        }
        vo.setTrendDates(trendDates);
        vo.setTrendMinutes(trendMinutes);

        // 交叉维度
        List<TimeCrossProjectUserRow> crossRows = reportStatisticsMapper.selectTimeCrossProjectUser(projectIds, startStr, endStr);
        vo.setCrossProjectUser(crossRows.stream().map(r -> {
            TimeReportVO.CrossDimensionItem item = new TimeReportVO.CrossDimensionItem();
            item.setProjectName(r.getProjectName());
            item.setUserName(r.getUserName());
            item.setMinutes(r.getTotalMinutes() != null ? r.getTotalMinutes() : 0);
            return item;
        }).collect(Collectors.toList()));

        return vo;
    }

    /**
     * 获取预估对比报表：estimation vs spent
     */
    public EstimationReportVO getEstimationReport(Long projectId, Long userId, int page, int pageSize) {
        List<Long> projectIds = resolveProjectIds(projectId, userId);

        // 1. 聚合汇总（不受分页影响，基于全量数据计算）
        List<EstimationSummaryRow> summaryRows = reportStatisticsMapper.selectEstimationSummary(projectIds);

        double totalEstimated = 0;
        double totalSpent = 0;
        List<EstimationReportVO.ProjectEstimationItem> byProject = new ArrayList<>();

        for (EstimationSummaryRow sr : summaryRows) {
            double est = sr.getEstimatedHoursSum() != null ? sr.getEstimatedHoursSum() : 0;
            double spt = sr.getSpentHoursSum() != null ? sr.getSpentHoursSum() : 0;
            totalEstimated += est;
            totalSpent += spt;

            EstimationReportVO.ProjectEstimationItem pi = new EstimationReportVO.ProjectEstimationItem();
            pi.setProjectName(sr.getProjectName());
            pi.setEstimatedHours(Math.round(est * 100.0) / 100.0);
            pi.setSpentHours(Math.round(spt * 100.0) / 100.0);
            pi.setDeviationRate(est > 0 ? Math.round((spt / est - 1) * 1000.0) / 1000.0 : 0);
            pi.setIssueCount(sr.getIssueCount() != null ? sr.getIssueCount() : 0);
            byProject.add(pi);
        }

        // 2. 明细分页查询
        long total = reportStatisticsMapper.countEstimationComparison(projectIds);
        int offset = (page - 1) * pageSize;
        List<EstimationComparisonRow> rows = reportStatisticsMapper.selectEstimationComparison(projectIds, pageSize, offset);

        List<EstimationReportVO.IssueEstimationItem> items = new ArrayList<>();
        for (EstimationComparisonRow row : rows) {
            double estimated = row.getEstimatedHours() != null ? row.getEstimatedHours() : 0;
            double spent = row.getSpentHours() != null ? row.getSpentHours() : 0;

            EstimationReportVO.IssueEstimationItem item = new EstimationReportVO.IssueEstimationItem();
            item.setIssueId(String.valueOf(row.getIssueId()));
            item.setIssueKey(row.getIssueKey());
            item.setIssueTitle(row.getTitle());
            item.setProjectName(row.getProjectName());
            item.setAssigneeName(row.getAssigneeName());
            item.setEstimatedHours(Math.round(estimated * 100.0) / 100.0);
            item.setSpentHours(Math.round(spent * 100.0) / 100.0);

            double deviation = estimated > 0 ? (spent / estimated - 1) : 0;
            item.setDeviationRate(Math.round(deviation * 1000.0) / 1000.0);
            if (Math.abs(deviation) <= 0.1) {
                item.setDeviation("on_track");
            } else if (deviation > 0) {
                item.setDeviation("over");
            } else {
                item.setDeviation("under");
            }
            items.add(item);
        }

        // 3. 组装响应
        EstimationReportVO vo = new EstimationReportVO();
        vo.setTotalEstimatedHours(Math.round(totalEstimated * 100.0) / 100.0);
        vo.setTotalSpentHours(Math.round(totalSpent * 100.0) / 100.0);
        vo.setOverallDeviationRate(totalEstimated > 0 ? Math.round((totalSpent / totalEstimated - 1) * 1000.0) / 1000.0 : 0);
        vo.setItems(items);
        vo.setPagination(new com.trackflow.common.model.PageResult.Pagination(page, pageSize, total));
        vo.setByProject(byProject);

        return vo;
    }

    // ─── Helper: 解析 Issue filter 为工单 ID 列表 ──────────────────────

    /**
     * 解析 Issue filter JSON 为匹配的工单 ID 列表
     *
     * @param filter               JSON 数组格式的筛选条件
     * @param projectId            项目ID（可选）
     * @param userId               当前用户ID
     * @param accessibleProjectIds 用户可访问的项目ID列表（projectId 非空时为 List.of(projectId)）
     * @return 匹配的工单ID列表，null 表示无筛选
     */
    @SuppressWarnings("unchecked")
    public List<Long> resolveIssueFilter(String filter, Long projectId, Long userId, List<Long> accessibleProjectIds) {
        if (filter == null || filter.isBlank()) {
            return null; // null 表示无筛选
        }
        try {
            List<Map<String, Object>> filters = objectMapper.readValue(filter,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            if (filters.isEmpty()) {
                return null;
            }
            return queryExecutor.executeFilterToIds(filters, accessibleProjectIds);
        } catch (Exception e) {
            // 无法解析的 filter 忽略，不筛选
            log.debug("Failed to parse issue filter: {}", filter, e);
            return null;
        }
    }

}

