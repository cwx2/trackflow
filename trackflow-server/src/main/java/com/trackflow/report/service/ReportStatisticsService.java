package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.vo.*;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.sprint.service.SprintService;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表统计服务 — 提供图表所需的各种聚合数据
 * TODO: 大数据量时应改为 SQL 聚合查询，当前全量 selectList 仅适用于中小项目
 */
@Service
@RequiredArgsConstructor
public class ReportStatisticsService {

    private final IssueMapper issueMapper;
    private final IssueActivityMapper activityMapper;
    private final IssueStatusMapper statusMapper;
    private final SysUserMapper userMapper;
    private final SprintMapper sprintMapper;
    private final SprintService sprintService;
    private final StatusCacheHelper statusCacheHelper;
    private final ProjectService projectService;
    private final ProjectMapper projectMapper;

    /**
     * 获取仪表盘全量数据（一次请求，前端缓存分发）
     * @param projectId null 表示聚合用户可访问的全部项目
     * @param userId 当前用户ID，用于确定可访问项目范围
     */
    public DashboardVO getDashboardData(Long projectId, Long sprintId, LocalDate startDate, LocalDate endDate, Long userId) {
        List<Long> projectIds = resolveProjectIds(projectId, userId);
        List<Issue> issues = queryIssues(projectIds, sprintId);
        Set<Long> closedIds = getClosedStatusIds();

        DashboardVO dashboard = new DashboardVO();
        dashboard.setStatusDistribution(buildStatusDistribution(issues));
        dashboard.setPriorityDistribution(buildPriorityDistribution(issues));
        dashboard.setTypeDistribution(buildTypeDistribution(issues));
        dashboard.setWorkload(buildWorkload(issues, closedIds));
        dashboard.setTrend(buildTrend(projectIds, startDate, endDate));
        if (sprintId != null) {
            dashboard.setBurndown(buildBurndown(projectId, sprintId));
        }
        dashboard.setOverview(buildOverview(issues, closedIds));
        // 跨项目对比：仅"全部项目"模式下（projectId == null 且有多个项目）返回
        if (projectId == null) {
            dashboard.setProjectComparison(buildProjectComparison(issues, projectIds, closedIds));
        }
        // 累积流图和解决时间分析
        dashboard.setCumulativeFlow(buildCumulativeFlow(projectIds, startDate, endDate));
        dashboard.setResolutionTime(buildResolutionTime(projectIds, startDate, endDate, null));
        return dashboard;
    }

    // ─── Public endpoints (single chart) ────────────────────────────────

    public StatusDistributionVO getStatusDistribution(Long projectId, Long sprintId) {
        return buildStatusDistribution(queryIssues(List.of(projectId), sprintId));
    }

    public PriorityDistributionVO getPriorityDistribution(Long projectId, Long sprintId) {
        return buildPriorityDistribution(queryIssues(List.of(projectId), sprintId));
    }

    public TypeDistributionVO getTypeDistribution(Long projectId, Long sprintId) {
        return buildTypeDistribution(queryIssues(List.of(projectId), sprintId));
    }

    public WorkloadVO getWorkload(Long projectId, Long sprintId) {
        return buildWorkload(queryIssues(List.of(projectId), sprintId), getClosedStatusIds());
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

    // ─── Internal build methods ─────────────────────────────────────────

    private StatusDistributionVO buildStatusDistribution(List<Issue> issues) {
        List<IssueStatus> statuses = statusMapper.selectList(new LambdaQueryWrapper<IssueStatus>()
                .orderByAsc(IssueStatus::getSortOrder));

        Map<Long, Long> grouped = issues.stream()
                .collect(Collectors.groupingBy(Issue::getStatusId, Collectors.counting()));

        List<StatusDistributionVO.StatusItem> items = new ArrayList<>();
        for (IssueStatus status : statuses) {
            long count = grouped.getOrDefault(status.getId(), 0L);
            if (count > 0) {
                StatusDistributionVO.StatusItem item = new StatusDistributionVO.StatusItem();
                item.setName(status.getName());
                item.setValue(count);
                item.setColor(status.getColor());
                item.setCategory(status.getCategory());
                items.add(item);
            }
        }

        StatusDistributionVO vo = new StatusDistributionVO();
        vo.setItems(items);
        vo.setTotal(issues.size());
        return vo;
    }

    private PriorityDistributionVO buildPriorityDistribution(List<Issue> issues) {
        List<String> priorityOrder = List.of("Critical", "High", "Normal", "Low");
        Map<String, String> priorityColors = Map.of(
                "Critical", "#f85149",
                "High", "#d29922",
                "Normal", "#58a6ff",
                "Low", "#6b7280"
        );

        Map<String, Long> grouped = issues.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getPriority() != null ? i.getPriority() : "Normal",
                        Collectors.counting()));

        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        List<String> colors = new ArrayList<>();

        for (String priority : priorityOrder) {
            long count = grouped.getOrDefault(priority, 0L);
            labels.add(priority);
            data.add(count);
            colors.add(priorityColors.getOrDefault(priority, "#6b7280"));
        }

        PriorityDistributionVO vo = new PriorityDistributionVO();
        vo.setLabels(labels);
        vo.setData(data);
        vo.setColors(colors);
        vo.setTotal(issues.size());
        return vo;
    }

    private TypeDistributionVO buildTypeDistribution(List<Issue> issues) {
        Map<String, String> typeColors = Map.of(
                "Bug", "#f85149",
                "Task", "#58a6ff",
                "Feature", "#3fb950",
                "Story", "#a371f7",
                "Improvement", "#d29922"
        );

        Map<String, Long> grouped = issues.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getIssueType() != null ? i.getIssueType() : "Task",
                        Collectors.counting()));

        List<TypeDistributionVO.TypeItem> items = new ArrayList<>();
        for (Map.Entry<String, Long> entry : grouped.entrySet()) {
            TypeDistributionVO.TypeItem item = new TypeDistributionVO.TypeItem();
            item.setName(entry.getKey());
            item.setValue(entry.getValue());
            item.setColor(typeColors.getOrDefault(entry.getKey(), "#6b7280"));
            items.add(item);
        }
        items.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        TypeDistributionVO vo = new TypeDistributionVO();
        vo.setItems(items);
        vo.setTotal(issues.size());
        return vo;
    }

    private WorkloadVO buildWorkload(List<Issue> issues, Set<Long> closedIds) {
        Map<Long, Long> grouped = issues.stream()
                .filter(i -> i.getAssigneeId() != null)
                .collect(Collectors.groupingBy(Issue::getAssigneeId, Collectors.counting()));

        long unassigned = issues.stream().filter(i -> i.getAssigneeId() == null).count();

        Set<Long> userIds = grouped.keySet();
        Map<Long, String> nameMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));

        List<WorkloadVO.WorkloadItem> items = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : grouped.entrySet()) {
            WorkloadVO.WorkloadItem item = new WorkloadVO.WorkloadItem();
            item.setName(nameMap.getOrDefault(entry.getKey(), "未知用户"));
            item.setValue(entry.getValue());
            long doneCount = issues.stream()
                    .filter(i -> entry.getKey().equals(i.getAssigneeId()))
                    .filter(i -> closedIds.contains(i.getStatusId()))
                    .count();
            item.setDone(doneCount);
            item.setInProgress(entry.getValue() - doneCount);
            items.add(item);
        }
        items.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        if (unassigned > 0) {
            WorkloadVO.WorkloadItem unassignedItem = new WorkloadVO.WorkloadItem();
            unassignedItem.setName("未分配");
            unassignedItem.setValue(unassigned);
            unassignedItem.setDone(0L);
            unassignedItem.setInProgress(unassigned);
            items.add(unassignedItem);
        }

        WorkloadVO vo = new WorkloadVO();
        vo.setItems(items);
        vo.setTotal(issues.size());
        return vo;
    }

    private OverviewVO buildOverview(List<Issue> issues, Set<Long> closedIds) {
        long total = issues.size();
        long open = issues.stream().filter(i -> !closedIds.contains(i.getStatusId())).count();
        long closed = total - open;
        long unassigned = issues.stream().filter(i -> i.getAssigneeId() == null).count();
        long overdue = issues.stream()
                .filter(i -> i.getDueDate() != null && i.getDueDate().isBefore(LocalDate.now()))
                .filter(i -> !closedIds.contains(i.getStatusId()))
                .count();

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

        LambdaQueryWrapper<Issue> createdWrapper = new LambdaQueryWrapper<Issue>()
                .isNull(Issue::getDeletedAt)
                .ge(Issue::getCreatedAt, start)
                .le(Issue::getCreatedAt, end);
        if (projectIds != null && !projectIds.isEmpty()) {
            if (projectIds.size() == 1) {
                createdWrapper.eq(Issue::getProjectId, projectIds.get(0));
            } else {
                createdWrapper.in(Issue::getProjectId, projectIds);
            }
        }
        List<Issue> createdIssues = issueMapper.selectList(createdWrapper);

        LambdaQueryWrapper<Issue> resolvedWrapper = new LambdaQueryWrapper<Issue>()
                .isNull(Issue::getDeletedAt)
                .isNotNull(Issue::getResolvedAt)
                .ge(Issue::getResolvedAt, start)
                .le(Issue::getResolvedAt, end);
        if (projectIds != null && !projectIds.isEmpty()) {
            if (projectIds.size() == 1) {
                resolvedWrapper.eq(Issue::getProjectId, projectIds.get(0));
            } else {
                resolvedWrapper.in(Issue::getProjectId, projectIds);
            }
        }
        List<Issue> resolvedIssues = issueMapper.selectList(resolvedWrapper);

        Map<LocalDate, Long> createdByDay = createdIssues.stream()
                .collect(Collectors.groupingBy(i -> i.getCreatedAt().toLocalDate(), Collectors.counting()));

        Map<LocalDate, Long> resolvedByDay = resolvedIssues.stream()
                .collect(Collectors.groupingBy(i -> i.getResolvedAt().toLocalDate(), Collectors.counting()));

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
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null || sprint.getStartDate() == null || sprint.getEndDate() == null) {
            BurndownVO empty = new BurndownVO();
            empty.setDates(List.of());
            empty.setIdeal(List.of());
            empty.setActual(List.of());
            empty.setSprintName(sprint != null ? sprint.getName() : "");
            empty.setTotalIssues(0);
            return empty;
        }

        // 委托给 SprintService 的 scope-aware 算法
        var sprintBurndown = sprintService.getBurndownData(sprintId);

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

    // ─── Private helpers ────────────────────────────────────────────────

    /**
     * 构建累积流图数据
     * 算法：重建每天每个状态的工单数量快照
     * 1. 找出时间范围内相关项目的所有工单（创建于 endDate 之前）
     * 2. 获取所有状态变更活动记录
     * 3. 对每一天，回放活动记录计算各状态的工单数
     */
    private CumulativeFlowVO buildCumulativeFlow(List<Long> projectIds, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        // 获取所有非 closed 类型的状态（用于显示）+ 按 sort_order 排序
        List<IssueStatus> allStatuses = statusMapper.selectList(new LambdaQueryWrapper<IssueStatus>()
                .orderByAsc(IssueStatus::getSortOrder));
        Map<String, IssueStatus> statusByName = allStatuses.stream()
                .collect(Collectors.toMap(IssueStatus::getName, s -> s, (a, b) -> a));
        Map<Long, IssueStatus> statusById = allStatuses.stream()
                .collect(Collectors.toMap(IssueStatus::getId, s -> s, (a, b) -> a));

        // 查询在 endDate 之前创建的、属于指定项目的工单
        LambdaQueryWrapper<Issue> issueWrapper = new LambdaQueryWrapper<Issue>()
                .isNull(Issue::getDeletedAt)
                .le(Issue::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        if (projectIds != null && !projectIds.isEmpty()) {
            if (projectIds.size() == 1) {
                issueWrapper.eq(Issue::getProjectId, projectIds.get(0));
            } else {
                issueWrapper.in(Issue::getProjectId, projectIds);
            }
        }
        List<Issue> issues = issueMapper.selectList(issueWrapper);

        if (issues.isEmpty()) {
            CumulativeFlowVO empty = new CumulativeFlowVO();
            empty.setDates(List.of());
            empty.setSeries(List.of());
            return empty;
        }

        Set<Long> issueIds = issues.stream().map(Issue::getId).collect(Collectors.toSet());

        // 查询这些工单的所有状态变更活动
        LambdaQueryWrapper<IssueActivity> activityWrapper = new LambdaQueryWrapper<IssueActivity>()
                .in(IssueActivity::getIssueId, issueIds)
                .eq(IssueActivity::getFieldName, "status")
                .le(IssueActivity::getCreatedAt, endDate.plusDays(1).atStartOfDay())
                .orderByAsc(IssueActivity::getCreatedAt);
        List<IssueActivity> activities = activityMapper.selectList(activityWrapper);

        // 按 issueId 分组活动记录
        Map<Long, List<IssueActivity>> activitiesByIssue = activities.stream()
                .collect(Collectors.groupingBy(IssueActivity::getIssueId));

        // 计算每天结束时各状态的工单数
        List<String> dates = new ArrayList<>();
        // 使用 LinkedHashMap 保持 sort_order 顺序
        Map<String, List<Long>> seriesData = new LinkedHashMap<>();
        // 只展示期间内实际出现过的状态
        Set<String> appearedStatuses = new LinkedHashSet<>();

        // 先确定哪些状态出现过
        for (Issue issue : issues) {
            IssueStatus st = statusById.get(issue.getStatusId());
            if (st != null) appearedStatuses.add(st.getName());
        }
        for (IssueActivity act : activities) {
            if (act.getOldValue() != null) appearedStatuses.add(act.getOldValue());
            if (act.getNewValue() != null) appearedStatuses.add(act.getNewValue());
        }

        // 按 sort_order 排列状态名
        List<String> orderedStatusNames = allStatuses.stream()
                .map(IssueStatus::getName)
                .filter(appearedStatuses::contains)
                .collect(Collectors.toList());

        for (String statusName : orderedStatusNames) {
            seriesData.put(statusName, new ArrayList<>());
        }

        // 对每一天计算快照
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            dates.add(current.toString());
            LocalDateTime dayEnd = current.plusDays(1).atStartOfDay();

            // 统计每个工单在该天结束时的状态
            Map<String, Long> statusCounts = new HashMap<>();
            for (String name : orderedStatusNames) {
                statusCounts.put(name, 0L);
            }

            for (Issue issue : issues) {
                // 工单还未创建
                if (issue.getCreatedAt().isAfter(dayEnd)) continue;

                // 确定该工单在 dayEnd 时刻的状态
                String currentStatus = getIssueStatusAtTime(issue, activitiesByIssue.get(issue.getId()), dayEnd, statusById);
                if (currentStatus != null && statusCounts.containsKey(currentStatus)) {
                    statusCounts.merge(currentStatus, 1L, Long::sum);
                }
            }

            for (String statusName : orderedStatusNames) {
                seriesData.get(statusName).add(statusCounts.getOrDefault(statusName, 0L));
            }

            current = current.plusDays(1);
        }

        // 构建返回结果
        List<CumulativeFlowVO.StatusSeries> seriesList = new ArrayList<>();
        for (String statusName : orderedStatusNames) {
            IssueStatus status = statusByName.get(statusName);
            CumulativeFlowVO.StatusSeries series = new CumulativeFlowVO.StatusSeries();
            series.setName(statusName);
            series.setColor(status != null ? status.getColor() : "#6b7280");
            series.setData(seriesData.get(statusName));
            seriesList.add(series);
        }

        CumulativeFlowVO vo = new CumulativeFlowVO();
        vo.setDates(dates);
        vo.setSeries(seriesList);
        return vo;
    }

    /**
     * 确定某工单在给定时刻的状态
     */
    private String getIssueStatusAtTime(Issue issue, List<IssueActivity> issueActivities,
                                        LocalDateTime atTime, Map<Long, IssueStatus> statusById) {
        // 初始状态：如果没有活动记录在 atTime 之前，使用创建时的初始状态
        // 初始状态通过回溯第一条活动的 old_value 获取，若无活动则用当前状态
        String initialStatus;
        if (issueActivities != null && !issueActivities.isEmpty()) {
            initialStatus = issueActivities.get(0).getOldValue();
        } else {
            IssueStatus st = statusById.get(issue.getStatusId());
            initialStatus = st != null ? st.getName() : "Open";
        }

        if (issueActivities == null || issueActivities.isEmpty()) {
            return initialStatus;
        }

        // 回放到 atTime 为止的所有状态变更
        String status = initialStatus;
        for (IssueActivity act : issueActivities) {
            if (act.getCreatedAt().isBefore(atTime)) {
                status = act.getNewValue();
            } else {
                break;
            }
        }
        return status;
    }

    /**
     * 构建解决时间分析数据
     * 计算工单从创建到解决（resolved_at）的耗时统计
     *
     * @param groupBy 分组方式：null（不分组）、"type"、"priority"、"assignee"
     */
    private ResolutionTimeVO buildResolutionTime(List<Long> projectIds, LocalDate startDate, LocalDate endDate, String groupBy) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        // 查询时间范围内已解决的工单
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<Issue>()
                .isNull(Issue::getDeletedAt)
                .isNotNull(Issue::getResolvedAt)
                .ge(Issue::getResolvedAt, start)
                .le(Issue::getResolvedAt, end);
        if (projectIds != null && !projectIds.isEmpty()) {
            if (projectIds.size() == 1) {
                wrapper.eq(Issue::getProjectId, projectIds.get(0));
            } else {
                wrapper.in(Issue::getProjectId, projectIds);
            }
        }
        List<Issue> resolvedIssues = issueMapper.selectList(wrapper);

        // 按周分组计算趋势
        Map<LocalDate, List<Double>> hoursByWeek = new TreeMap<>();
        for (Issue issue : resolvedIssues) {
            double hours = ChronoUnit.MINUTES.between(issue.getCreatedAt(), issue.getResolvedAt()) / 60.0;
            // 按 resolvedAt 所在周的周一分组
            LocalDate weekStart = issue.getResolvedAt().toLocalDate()
                    .with(java.time.DayOfWeek.MONDAY);
            hoursByWeek.computeIfAbsent(weekStart, k -> new ArrayList<>()).add(hours);
        }

        // 如果不足 4 周数据，改为按天分组
        boolean useDayGrouping = hoursByWeek.size() < 4;
        List<String> dates = new ArrayList<>();
        List<Double> avgHours = new ArrayList<>();
        List<Double> medianHours = new ArrayList<>();
        List<Double> p90Hours = new ArrayList<>();
        List<Long> resolvedCount = new ArrayList<>();

        if (useDayGrouping) {
            Map<LocalDate, List<Double>> hoursByDay = new TreeMap<>();
            for (Issue issue : resolvedIssues) {
                double hours = ChronoUnit.MINUTES.between(issue.getCreatedAt(), issue.getResolvedAt()) / 60.0;
                LocalDate day = issue.getResolvedAt().toLocalDate();
                hoursByDay.computeIfAbsent(day, k -> new ArrayList<>()).add(hours);
            }
            // 填充所有日期（包括无数据的日子）
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                dates.add(current.toString());
                List<Double> dayHours = hoursByDay.getOrDefault(current, List.of());
                if (dayHours.isEmpty()) {
                    avgHours.add(null);
                    medianHours.add(null);
                    p90Hours.add(null);
                    resolvedCount.add(0L);
                } else {
                    avgHours.add(round2(average(dayHours)));
                    medianHours.add(round2(percentile(dayHours, 50)));
                    p90Hours.add(round2(percentile(dayHours, 90)));
                    resolvedCount.add((long) dayHours.size());
                }
                current = current.plusDays(1);
            }
        } else {
            for (Map.Entry<LocalDate, List<Double>> entry : hoursByWeek.entrySet()) {
                dates.add(entry.getKey().toString());
                List<Double> weekHours = entry.getValue();
                avgHours.add(round2(average(weekHours)));
                medianHours.add(round2(percentile(weekHours, 50)));
                p90Hours.add(round2(percentile(weekHours, 90)));
                resolvedCount.add((long) weekHours.size());
            }
        }

        // 分组明细
        List<ResolutionTimeVO.GroupDetail> groupDetails = new ArrayList<>();
        if (groupBy != null && !resolvedIssues.isEmpty()) {
            groupDetails = buildResolutionGroupDetails(resolvedIssues, groupBy);
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

    private List<ResolutionTimeVO.GroupDetail> buildResolutionGroupDetails(List<Issue> issues, String groupBy) {
        Map<String, List<Double>> grouped = new LinkedHashMap<>();

        Map<Long, String> assigneeNameMap = null;
        if ("assignee".equals(groupBy)) {
            Set<Long> assigneeIds = issues.stream()
                    .map(Issue::getAssigneeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!assigneeIds.isEmpty()) {
                assigneeNameMap = userMapper.selectBatchIds(assigneeIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));
            } else {
                assigneeNameMap = Map.of();
            }
        }

        for (Issue issue : issues) {
            String key;
            switch (groupBy) {
                case "type":
                    key = issue.getIssueType() != null ? issue.getIssueType() : "Task";
                    break;
                case "priority":
                    key = issue.getPriority() != null ? issue.getPriority() : "Normal";
                    break;
                case "assignee":
                    if (issue.getAssigneeId() == null) {
                        key = "未分配";
                    } else {
                        key = assigneeNameMap.getOrDefault(issue.getAssigneeId(), "未知用户");
                    }
                    break;
                default:
                    key = "全部";
            }
            double hours = ChronoUnit.MINUTES.between(issue.getCreatedAt(), issue.getResolvedAt()) / 60.0;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(hours);
        }

        List<ResolutionTimeVO.GroupDetail> details = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : grouped.entrySet()) {
            ResolutionTimeVO.GroupDetail detail = new ResolutionTimeVO.GroupDetail();
            detail.setName(entry.getKey());
            detail.setAvgHours(round2(average(entry.getValue())));
            detail.setMedianHours(round2(percentile(entry.getValue(), 50)));
            detail.setCount((long) entry.getValue().size());
            details.add(detail);
        }
        // 按解决工单数降序
        details.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        return details;
    }

    // ─── Statistics helpers ──────────────────────────────────────────────

    private double average(List<Double> values) {
        if (values.isEmpty()) return 0;
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double percentile(List<Double> values, int p) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private Double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 构建跨项目对比数据 — 按项目分组统计工单数、完成率等
     */
    private ProjectComparisonVO buildProjectComparison(List<Issue> issues, List<Long> projectIds, Set<Long> closedIds) {
        // 按项目分组
        Map<Long, List<Issue>> byProject = issues.stream()
                .filter(i -> i.getProjectId() != null)
                .collect(Collectors.groupingBy(Issue::getProjectId));

        // 获取项目信息（名称、key）
        Set<Long> allProjectIds = byProject.keySet();
        Map<Long, Project> projectMap;
        if (allProjectIds.isEmpty()) {
            projectMap = Map.of();
        } else {
            projectMap = projectMapper.selectBatchIds(allProjectIds).stream()
                    .collect(Collectors.toMap(Project::getId, p -> p, (a, b) -> a));
        }

        List<ProjectComparisonVO.ProjectStatItem> items = new ArrayList<>();
        for (Map.Entry<Long, List<Issue>> entry : byProject.entrySet()) {
            List<Issue> projectIssues = entry.getValue();
            Project project = projectMap.get(entry.getKey());
            if (project == null) continue;

            long total = projectIssues.size();
            long closed = projectIssues.stream()
                    .filter(i -> closedIds.contains(i.getStatusId()))
                    .count();
            long open = total - closed;
            long overdue = projectIssues.stream()
                    .filter(i -> i.getDueDate() != null && i.getDueDate().isBefore(LocalDate.now()))
                    .filter(i -> !closedIds.contains(i.getStatusId()))
                    .count();

            ProjectComparisonVO.ProjectStatItem item = new ProjectComparisonVO.ProjectStatItem();
            item.setName(project.getName());
            item.setKey(project.getKey());
            item.setTotal(total);
            item.setOpen(open);
            item.setClosed(closed);
            item.setCompletionRate(total > 0 ? Math.round(closed * 100.0 / total) : 0);
            item.setOverdue(overdue);
            items.add(item);
        }

        // 按工单总数降序
        items.sort((a, b) -> Long.compare(b.getTotal(), a.getTotal()));

        ProjectComparisonVO vo = new ProjectComparisonVO();
        vo.setItems(items);
        return vo;
    }

    /**
     * 将单个 projectId 或 null 解析为可访问项目 ID 列表。
     * null projectId 表示"全部项目"——调用 ProjectService.getAccessibleProjectIds 获取权限范围。
     */
    private List<Long> resolveProjectIds(Long projectId, Long userId) {
        if (projectId != null) {
            return List.of(projectId);
        }
        // null 表示系统管理员无限制
        List<Long> accessible = projectService.getAccessibleProjectIds(userId);
        return accessible; // null for system admin means all projects
    }

    private List<Issue> queryIssues(List<Long> projectIds, Long sprintId) {
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<>();
        if (projectIds != null && !projectIds.isEmpty()) {
            if (projectIds.size() == 1) {
                wrapper.eq(Issue::getProjectId, projectIds.get(0));
            } else {
                wrapper.in(Issue::getProjectId, projectIds);
            }
        }
        // projectIds == null means system admin, no project filter
        wrapper.isNull(Issue::getDeletedAt);
        if (sprintId != null) {
            wrapper.eq(Issue::getSprintId, sprintId);
        }
        return issueMapper.selectList(wrapper);
    }

    private Set<Long> getClosedStatusIds() {
        return statusCacheHelper.getClosedStatusIds();
    }
}
