package com.trackflow.report.service;

import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.project.service.ProjectService;
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
    private final ProjectService projectService;
    private final WorkItemAttributeService workItemAttributeService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "report:dashboard:";
    private static final long CACHE_TTL_SECONDS = 90;

    // ─── Dashboard (main entry) ──────────────────────────────────────────

    /**
     * 获取仪表盘全量数据（一次请求，前端缓存分发）
     * 支持 Redis 短期缓存（90s TTL）
     */
    public DashboardVO getDashboardData(Long projectId, Long sprintId, LocalDate startDate, LocalDate endDate, Long userId) {
        List<Long> projectIds = resolveProjectIds(projectId, userId);

        // 尝试从缓存获取
        String cacheKey = buildCacheKey(projectId, sprintId, startDate, endDate);
        DashboardVO cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，构建数据
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        List<Long> closedStatusIds = new ArrayList<>(statusCacheHelper.getClosedStatusIds());

        DashboardVO dashboard = new DashboardVO();
        dashboard.setStatusDistribution(buildStatusDistribution(projectIds, sprintId));
        dashboard.setPriorityDistribution(buildPriorityDistribution(projectIds, sprintId));
        dashboard.setTypeDistribution(buildTypeDistribution(projectIds, sprintId));
        dashboard.setWorkload(buildWorkload(projectIds, sprintId, closedStatusIds));
        dashboard.setTrend(buildTrend(projectIds, startDate, endDate));
        if (sprintId != null) {
            dashboard.setBurndown(buildBurndown(projectId, sprintId));
        }
        dashboard.setOverview(buildOverview(projectIds, sprintId, closedStatusIds));
        // 跨项目对比：仅"全部项目"模式下（projectId == null 且有多个项目）返回
        if (projectId == null) {
            dashboard.setProjectComparison(buildProjectComparison(projectIds, closedStatusIds));
        }
        // 累积流图和解决时间分析
        dashboard.setCumulativeFlow(buildCumulativeFlow(projectIds, startDate, endDate));
        dashboard.setResolutionTime(buildResolutionTime(projectIds, startDate, endDate, null));

        // 写入缓存
        putToCache(cacheKey, dashboard);

        return dashboard;
    }

    // ─── Public endpoints (single chart) ────────────────────────────────

    public StatusDistributionVO getStatusDistribution(Long projectId, Long sprintId) {
        return buildStatusDistribution(List.of(projectId), sprintId);
    }

    public PriorityDistributionVO getPriorityDistribution(Long projectId, Long sprintId) {
        return buildPriorityDistribution(List.of(projectId), sprintId);
    }

    public TypeDistributionVO getTypeDistribution(Long projectId, Long sprintId) {
        return buildTypeDistribution(List.of(projectId), sprintId);
    }

    public WorkloadVO getWorkload(Long projectId, Long sprintId) {
        List<Long> closedStatusIds = new ArrayList<>(statusCacheHelper.getClosedStatusIds());
        return buildWorkload(List.of(projectId), sprintId, closedStatusIds);
    }

    public TrendVO getTrend(Long projectId, LocalDate startDate, LocalDate endDate) {
        return buildTrend(List.of(projectId), startDate, endDate);
    }

    public BurndownVO getBurndown(Long projectId, Long sprintId) {
        return buildBurndown(projectId, sprintId);
    }

    public CumulativeFlowVO getCumulativeFlow(Long projectId, LocalDate startDate, LocalDate endDate) {
        return buildCumulativeFlow(projectId != null ? List.of(projectId) : null, startDate, endDate);
    }

    public ResolutionTimeVO getResolutionTime(Long projectId, LocalDate startDate, LocalDate endDate, String groupBy) {
        return buildResolutionTime(projectId != null ? List.of(projectId) : null, startDate, endDate, groupBy);
    }

    // ─── Internal build methods (SQL aggregation) ────────────────────────

    private StatusDistributionVO buildStatusDistribution(List<Long> projectIds, Long sprintId) {
        List<StatusDistributionRow> rows = reportStatisticsMapper.selectStatusDistribution(projectIds, sprintId);

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

    private PriorityDistributionVO buildPriorityDistribution(List<Long> projectIds, Long sprintId) {
        List<PriorityDistributionRow> rows = reportStatisticsMapper.selectPriorityDistribution(projectIds, sprintId);

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

    private TypeDistributionVO buildTypeDistribution(List<Long> projectIds, Long sprintId) {
        List<TypeDistributionRow> rows = reportStatisticsMapper.selectTypeDistribution(projectIds, sprintId);

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

    private WorkloadVO buildWorkload(List<Long> projectIds, Long sprintId, List<Long> closedStatusIds) {
        // 如果没有关闭状态，传一个不可能的 ID 避免 SQL 语法错误
        List<Long> safeClosedIds = closedStatusIds.isEmpty() ? List.of(-1L) : closedStatusIds;
        List<WorkloadRow> rows = reportStatisticsMapper.selectWorkload(projectIds, sprintId, safeClosedIds);

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

    private OverviewVO buildOverview(List<Long> projectIds, Long sprintId, List<Long> closedStatusIds) {
        List<Long> safeClosedIds = closedStatusIds.isEmpty() ? List.of(-1L) : closedStatusIds;
        OverviewRow row = reportStatisticsMapper.selectOverview(
                projectIds, sprintId, safeClosedIds, LocalDateTime.now());

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

    private TrendVO buildTrend(List<Long> projectIds, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<TrendRow> createdRows = reportStatisticsMapper.selectCreatedTrend(projectIds, start, end);
        List<TrendRow> resolvedRows = reportStatisticsMapper.selectResolvedTrend(projectIds, start, end);

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
        // 委托给 SprintService 的 scope-aware 算法（已优化）
        com.trackflow.sprint.vo.BurndownVO sprintBurndown = sprintService.getBurndownData(sprintId);

        BurndownVO vo = new BurndownVO();
        vo.setDates(sprintBurndown.getDates());
        vo.setIdeal(sprintBurndown.getIdealLine());
        vo.setActual(sprintBurndown.getActualLine().stream()
                .map(Integer::longValue)
                .collect(Collectors.toList()));
        vo.setSprintName(sprintBurndown.getSprintName());
        vo.setTotalIssues(sprintBurndown.getTotalIssues());
        return vo;
    }

    private ProjectComparisonVO buildProjectComparison(List<Long> projectIds, List<Long> closedStatusIds) {
        List<Long> safeClosedIds = closedStatusIds.isEmpty() ? List.of(-1L) : closedStatusIds;
        List<ProjectComparisonRow> rows = reportStatisticsMapper.selectProjectComparison(
                projectIds, safeClosedIds, LocalDateTime.now());

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

    private CumulativeFlowVO buildCumulativeFlow(List<Long> projectIds, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        // 限制查询范围不超过 90 天，防止大数据量下查询超时
        if (ChronoUnit.DAYS.between(startDate, endDate) > CUMULATIVE_FLOW_MAX_DAYS) {
            startDate = endDate.minusDays(CUMULATIVE_FLOW_MAX_DAYS);
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<CumulativeFlowRow> rows = reportStatisticsMapper.selectCumulativeFlow(projectIds, start, end);

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

    private ResolutionTimeVO buildResolutionTime(List<Long> projectIds, LocalDate startDate, LocalDate endDate, String groupBy) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        // 判断是否用周分组：如果日期范围 >= 28 天则按周
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        boolean useWeekGrouping = daysBetween >= 28;

        List<ResolutionTimeTrendRow> rows = reportStatisticsMapper.selectResolutionTimeTrend(
                projectIds, start, end, useWeekGrouping);

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

        // 分组明细
        List<ResolutionTimeVO.GroupDetail> groupDetails = new ArrayList<>();
        if (groupBy != null) {
            List<ResolutionTimeGroupRow> groupRows = reportStatisticsMapper.selectResolutionTimeByGroup(
                    projectIds, start, end, groupBy);
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
        // null 表示系统管理员无限制
        return projectService.getAccessibleProjectIds(userId);
    }

    // ─── Redis cache ─────────────────────────────────────────────────────

    /**
     * 构建缓存 key（按项目维度共享，不含 userId）。
     * <p>
     * 格式：report:dashboard:{projectId|all}:{sprintId|none}:{startDate}:{endDate}
     * <p>
     * 去掉 userId 的理由：
     * - 相同筛选条件下不同用户看到的统计数据相同（权限过滤在 resolveProjectIds 阶段完成）
     * - 减少 Redis 存储（N 个用户共享 1 份缓存而非各存 1 份）
     * - 事件驱动失效时只需按 projectId 维度精准清除
     */
    private String buildCacheKey(Long projectId, Long sprintId, LocalDate startDate, LocalDate endDate) {
        return CACHE_PREFIX
                + (projectId != null ? projectId : "all") + ":"
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

        // 每日趋势
        List<TimeTrendRow> trendRows = reportStatisticsMapper.selectTimeTrend(projectIds, startStr, endStr);
        vo.setTrendDates(trendRows.stream().map(TimeTrendRow::getWorkDate).collect(Collectors.toList()));
        vo.setTrendMinutes(trendRows.stream().map(r -> r.getTotalMinutes() != null ? r.getTotalMinutes() : 0).collect(Collectors.toList()));

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
    public EstimationReportVO getEstimationReport(Long projectId, Long userId) {
        List<Long> projectIds = resolveProjectIds(projectId, userId);

        List<EstimationComparisonRow> rows = reportStatisticsMapper.selectEstimationComparison(projectIds);

        EstimationReportVO vo = new EstimationReportVO();

        double totalEstimated = 0;
        double totalSpent = 0;
        List<EstimationReportVO.IssueEstimationItem> items = new ArrayList<>();
        Map<String, double[]> projectAgg = new LinkedHashMap<>();

        for (EstimationComparisonRow row : rows) {
            double estimated = row.getEstimatedHours() != null ? row.getEstimatedHours() : 0;
            double spent = row.getSpentHours() != null ? row.getSpentHours() : 0;

            totalEstimated += estimated;
            totalSpent += spent;

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

            // 按项目聚合
            String projName = row.getProjectName();
            projectAgg.computeIfAbsent(projName, k -> new double[3]);
            double[] agg = projectAgg.get(projName);
            agg[0] += estimated;
            agg[1] += spent;
            agg[2] += 1;
        }

        vo.setTotalEstimatedHours(Math.round(totalEstimated * 100.0) / 100.0);
        vo.setTotalSpentHours(Math.round(totalSpent * 100.0) / 100.0);
        vo.setOverallDeviationRate(totalEstimated > 0 ? Math.round((totalSpent / totalEstimated - 1) * 1000.0) / 1000.0 : 0);
        vo.setItems(items);

        List<EstimationReportVO.ProjectEstimationItem> byProject = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : projectAgg.entrySet()) {
            EstimationReportVO.ProjectEstimationItem pi = new EstimationReportVO.ProjectEstimationItem();
            pi.setProjectName(entry.getKey());
            pi.setEstimatedHours(Math.round(entry.getValue()[0] * 100.0) / 100.0);
            pi.setSpentHours(Math.round(entry.getValue()[1] * 100.0) / 100.0);
            pi.setDeviationRate(entry.getValue()[0] > 0 ? Math.round((entry.getValue()[1] / entry.getValue()[0] - 1) * 1000.0) / 1000.0 : 0);
            pi.setIssueCount((int) entry.getValue()[2]);
            byProject.add(pi);
        }
        vo.setByProject(byProject);

        return vo;
    }

}

