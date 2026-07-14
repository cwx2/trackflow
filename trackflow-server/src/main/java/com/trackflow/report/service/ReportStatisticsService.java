package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.report.vo.*;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private final IssueStatusMapper statusMapper;
    private final SysUserMapper userMapper;
    private final SprintMapper sprintMapper;

    /**
     * 获取仪表盘全量数据（一次请求，前端缓存分发）
     */
    public DashboardVO getDashboardData(Long projectId, Long sprintId, LocalDate startDate, LocalDate endDate) {
        List<Issue> issues = queryIssues(projectId, sprintId);
        Set<Long> closedIds = getClosedStatusIds();

        DashboardVO dashboard = new DashboardVO();
        dashboard.setStatusDistribution(buildStatusDistribution(issues));
        dashboard.setPriorityDistribution(buildPriorityDistribution(issues));
        dashboard.setTypeDistribution(buildTypeDistribution(issues));
        dashboard.setWorkload(buildWorkload(issues, closedIds));
        dashboard.setTrend(buildTrend(projectId, startDate, endDate));
        if (sprintId != null) {
            dashboard.setBurndown(buildBurndown(projectId, sprintId));
        }
        dashboard.setOverview(buildOverview(issues, closedIds));
        return dashboard;
    }

    // ─── Public endpoints (single chart) ────────────────────────────────

    public StatusDistributionVO getStatusDistribution(Long projectId, Long sprintId) {
        return buildStatusDistribution(queryIssues(projectId, sprintId));
    }

    public PriorityDistributionVO getPriorityDistribution(Long projectId, Long sprintId) {
        return buildPriorityDistribution(queryIssues(projectId, sprintId));
    }

    public TypeDistributionVO getTypeDistribution(Long projectId, Long sprintId) {
        return buildTypeDistribution(queryIssues(projectId, sprintId));
    }

    public WorkloadVO getWorkload(Long projectId, Long sprintId) {
        return buildWorkload(queryIssues(projectId, sprintId), getClosedStatusIds());
    }

    public TrendVO getTrend(Long projectId, LocalDate startDate, LocalDate endDate) {
        return buildTrend(projectId, startDate, endDate);
    }

    public BurndownVO getBurndown(Long projectId, Long sprintId) {
        return buildBurndown(projectId, sprintId);
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

    private TrendVO buildTrend(Long projectId, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Issue> createdIssues = issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .isNull(Issue::getDeletedAt)
                .ge(Issue::getCreatedAt, start)
                .le(Issue::getCreatedAt, end));

        List<Issue> resolvedIssues = issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .isNull(Issue::getDeletedAt)
                .isNotNull(Issue::getResolvedAt)
                .ge(Issue::getResolvedAt, start)
                .le(Issue::getResolvedAt, end));

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

        List<Issue> issues = issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .eq(Issue::getSprintId, sprintId)
                .isNull(Issue::getDeletedAt));

        int totalIssues = issues.size();
        LocalDate sprintStart = sprint.getStartDate();
        LocalDate sprintEnd = sprint.getEndDate();
        LocalDate today = LocalDate.now();
        LocalDate endForActual = today.isBefore(sprintEnd) ? today : sprintEnd;

        long totalDays = sprintStart.until(sprintEnd).getDays();
        if (totalDays <= 0) totalDays = 1;

        Map<LocalDate, Long> resolvedByDay = issues.stream()
                .filter(i -> i.getResolvedAt() != null)
                .collect(Collectors.groupingBy(i -> i.getResolvedAt().toLocalDate(), Collectors.counting()));

        List<String> dates = new ArrayList<>();
        List<Double> ideal = new ArrayList<>();
        List<Long> actual = new ArrayList<>();

        long remaining = totalIssues;
        double idealRemaining = totalIssues;
        double idealDecrement = (double) totalIssues / totalDays;

        LocalDate current = sprintStart;
        while (!current.isAfter(sprintEnd)) {
            dates.add(current.toString());
            ideal.add(Math.max(0, Math.round(idealRemaining * 10.0) / 10.0));
            idealRemaining -= idealDecrement;

            if (!current.isAfter(endForActual)) {
                remaining -= resolvedByDay.getOrDefault(current, 0L);
                actual.add(Math.max(0, remaining));
            }
            current = current.plusDays(1);
        }

        BurndownVO vo = new BurndownVO();
        vo.setDates(dates);
        vo.setIdeal(ideal);
        vo.setActual(actual);
        vo.setSprintName(sprint.getName());
        vo.setTotalIssues(totalIssues);
        return vo;
    }

    // ─── Private helpers ────────────────────────────────────────────────

    private List<Issue> queryIssues(Long projectId, Long sprintId) {
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Issue::getProjectId, projectId);
        wrapper.isNull(Issue::getDeletedAt);
        if (sprintId != null) {
            wrapper.eq(Issue::getSprintId, sprintId);
        }
        return issueMapper.selectList(wrapper);
    }

    private Set<Long> getClosedStatusIds() {
        return statusMapper.selectList(new LambdaQueryWrapper<IssueStatus>()
                        .eq(IssueStatus::getIsClosed, true))
                .stream()
                .map(IssueStatus::getId)
                .collect(Collectors.toSet());
    }
}
