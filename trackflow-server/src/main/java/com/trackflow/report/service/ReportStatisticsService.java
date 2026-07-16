package com.trackflow.report.service;

import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.mapper.ReportStatisticsMapper;
import com.trackflow.report.vo.*;
import com.trackflow.sprint.service.SprintService;
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
        String cacheKey = buildCacheKey(userId, projectId, sprintId, startDate, endDate);
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
        List<Map<String, Object>> rows = reportStatisticsMapper.selectStatusDistribution(projectIds, sprintId);

        List<StatusDistributionVO.StatusItem> items = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> row : rows) {
            StatusDistributionVO.StatusItem item = new StatusDistributionVO.StatusItem();
            item.setName((String) row.get("status_name"));
            long cnt = toLong(row.get("cnt"));
            item.setValue(cnt);
            item.setColor((String) row.get("status_color"));
            item.setCategory((String) row.get("status_category"));
            items.add(item);
            total += cnt;
        }

        StatusDistributionVO vo = new StatusDistributionVO();
        vo.setItems(items);
        vo.setTotal((int) total);
        return vo;
    }

    private PriorityDistributionVO buildPriorityDistribution(List<Long> projectIds, Long sprintId) {
        List<Map<String, Object>> rows = reportStatisticsMapper.selectPriorityDistribution(projectIds, sprintId);

        Map<String, Long> grouped = new HashMap<>();
        long total = 0;
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("priority_name");
            long cnt = toLong(row.get("cnt"));
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
        List<Map<String, Object>> rows = reportStatisticsMapper.selectTypeDistribution(projectIds, sprintId);

        Map<String, String> typeColors = Map.of(
                "Bug", "#f85149",
                "Task", "#58a6ff",
                "Feature", "#3fb950",
                "Story", "#a371f7",
                "Improvement", "#d29922"
        );

        List<TypeDistributionVO.TypeItem> items = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> row : rows) {
            TypeDistributionVO.TypeItem item = new TypeDistributionVO.TypeItem();
            String name = (String) row.get("type_name");
            long cnt = toLong(row.get("cnt"));
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
        List<Map<String, Object>> rows = reportStatisticsMapper.selectWorkload(projectIds, sprintId, safeClosedIds);

        List<WorkloadVO.WorkloadItem> items = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> row : rows) {
            WorkloadVO.WorkloadItem item = new WorkloadVO.WorkloadItem();
            Long assigneeId = toLongOrNull(row.get("assignee_id"));
            String name = (String) row.get("assignee_name");
            long itemTotal = toLong(row.get("total"));
            long doneCount = toLong(row.get("done_count"));

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
        Map<String, Object> row = reportStatisticsMapper.selectOverview(
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

        long total = toLong(row.get("total"));
        long open = toLong(row.get("open_count"));
        long closed = toLong(row.get("closed_count"));
        long unassigned = toLong(row.get("unassigned"));
        long overdue = toLong(row.get("overdue"));

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

        List<Map<String, Object>> createdRows = reportStatisticsMapper.selectCreatedTrend(projectIds, start, end);
        List<Map<String, Object>> resolvedRows = reportStatisticsMapper.selectResolvedTrend(projectIds, start, end);

        // 转为 Map 方便按日期查找
        Map<LocalDate, Long> createdByDay = new HashMap<>();
        for (Map<String, Object> row : createdRows) {
            LocalDate day = toLocalDate(row.get("day"));
            if (day != null) createdByDay.put(day, toLong(row.get("cnt")));
        }
        Map<LocalDate, Long> resolvedByDay = new HashMap<>();
        for (Map<String, Object> row : resolvedRows) {
            LocalDate day = toLocalDate(row.get("day"));
            if (day != null) resolvedByDay.put(day, toLong(row.get("cnt")));
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
        List<Map<String, Object>> rows = reportStatisticsMapper.selectProjectComparison(
                projectIds, safeClosedIds, LocalDateTime.now());

        List<ProjectComparisonVO.ProjectStatItem> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ProjectComparisonVO.ProjectStatItem item = new ProjectComparisonVO.ProjectStatItem();
            item.setName((String) row.get("project_name"));
            item.setKey((String) row.get("project_key"));
            long total = toLong(row.get("total"));
            long open = toLong(row.get("open_count"));
            long closed = toLong(row.get("closed_count"));
            long overdue = toLong(row.get("overdue"));
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

    private CumulativeFlowVO buildCumulativeFlow(List<Long> projectIds, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> rows = reportStatisticsMapper.selectCumulativeFlow(projectIds, start, end);

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

        for (Map<String, Object> row : rows) {
            String day = (String) row.get("day");
            String statusName = (String) row.get("status_name");
            String color = (String) row.get("status_color");
            int sortOrder = toInt(row.get("sort_order"));
            long cnt = toLong(row.get("cnt"));

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

        List<Map<String, Object>> rows = reportStatisticsMapper.selectResolutionTimeTrend(
                projectIds, start, end, useWeekGrouping);

        List<String> dates = new ArrayList<>();
        List<Double> avgHours = new ArrayList<>();
        List<Double> medianHours = new ArrayList<>();
        List<Double> p90Hours = new ArrayList<>();
        List<Long> resolvedCount = new ArrayList<>();

        if (!useWeekGrouping) {
            // 按天分组时填充所有日期（含无数据的日子）
            Map<String, Map<String, Object>> rowsByDay = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                rowsByDay.put((String) row.get("period"), row);
            }

            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                String dayStr = current.toString();
                dates.add(dayStr);
                Map<String, Object> row = rowsByDay.get(dayStr);
                if (row != null) {
                    avgHours.add(toDouble(row.get("avg_hours")));
                    medianHours.add(toDouble(row.get("median_hours")));
                    p90Hours.add(toDouble(row.get("p90_hours")));
                    resolvedCount.add(toLong(row.get("resolved_count")));
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
            for (Map<String, Object> row : rows) {
                dates.add((String) row.get("period"));
                avgHours.add(toDouble(row.get("avg_hours")));
                medianHours.add(toDouble(row.get("median_hours")));
                p90Hours.add(toDouble(row.get("p90_hours")));
                resolvedCount.add(toLong(row.get("resolved_count")));
            }
        }

        // 分组明细
        List<ResolutionTimeVO.GroupDetail> groupDetails = new ArrayList<>();
        if (groupBy != null) {
            List<Map<String, Object>> groupRows = reportStatisticsMapper.selectResolutionTimeByGroup(
                    projectIds, start, end, groupBy);
            for (Map<String, Object> row : groupRows) {
                ResolutionTimeVO.GroupDetail detail = new ResolutionTimeVO.GroupDetail();
                detail.setName((String) row.get("group_name"));
                detail.setAvgHours(toDouble(row.get("avg_hours")));
                detail.setMedianHours(toDouble(row.get("median_hours")));
                detail.setCount(toLong(row.get("cnt")));
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

    private String buildCacheKey(Long userId, Long projectId, Long sprintId, LocalDate startDate, LocalDate endDate) {
        return CACHE_PREFIX + userId + ":"
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

    // ─── Type conversion helpers ─────────────────────────────────────────

    private long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Long l) return l;
        if (obj instanceof Integer i) return i.longValue();
        if (obj instanceof Number n) return n.longValue();
        return 0L;
    }

    private Long toLongOrNull(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long l) return l;
        if (obj instanceof Integer i) return i.longValue();
        if (obj instanceof Number n) return n.longValue();
        return null;
    }

    private int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Integer i) return i;
        if (obj instanceof Number n) return n.intValue();
        return 0;
    }

    private Double toDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Double d) return d;
        if (obj instanceof Number n) return n.doubleValue();
        return null;
    }

    private LocalDate toLocalDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDate ld) return ld;
        if (obj instanceof java.sql.Date sd) return sd.toLocalDate();
        if (obj instanceof java.util.Date d) return new java.sql.Date(d.getTime()).toLocalDate();
        return null;
    }
}
