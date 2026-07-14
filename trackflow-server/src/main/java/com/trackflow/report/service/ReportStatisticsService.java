package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
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
     * 一次查询 issues + closedStatusIds，避免重复查询
     */
    public Map<String, Object> getDashboardData(Long projectId, Long sprintId, LocalDate startDate, LocalDate endDate) {
        List<Issue> issues = queryIssues(projectId, sprintId);
        Set<Long> closedIds = getClosedStatusIds();

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("statusDistribution", buildStatusDistribution(issues));
        dashboard.put("priorityDistribution", buildPriorityDistribution(issues));
        dashboard.put("typeDistribution", buildTypeDistribution(issues));
        dashboard.put("workload", buildWorkload(issues, closedIds));
        dashboard.put("trend", getTrend(projectId, startDate, endDate));
        if (sprintId != null) {
            dashboard.put("burndown", getBurndown(projectId, sprintId));
        }
        dashboard.put("overview", buildOverview(issues, closedIds));
        return dashboard;
    }

    // ─── Public endpoints (single chart) ────────────────────────────────

    public Map<String, Object> getStatusDistribution(Long projectId, Long sprintId) {
        return buildStatusDistribution(queryIssues(projectId, sprintId));
    }

    public Map<String, Object> getPriorityDistribution(Long projectId, Long sprintId) {
        return buildPriorityDistribution(queryIssues(projectId, sprintId));
    }

    public Map<String, Object> getTypeDistribution(Long projectId, Long sprintId) {
        return buildTypeDistribution(queryIssues(projectId, sprintId));
    }

    public Map<String, Object> getWorkload(Long projectId, Long sprintId) {
        return buildWorkload(queryIssues(projectId, sprintId), getClosedStatusIds());
    }

    // ─── Internal build methods ─────────────────────────────────────────

    private Map<String, Object> buildStatusDistribution(List<Issue> issues) {
        List<IssueStatus> statuses = statusMapper.selectList(new LambdaQueryWrapper<IssueStatus>()
                .orderByAsc(IssueStatus::getSortOrder));

        Map<Long, Long> grouped = issues.stream()
                .collect(Collectors.groupingBy(Issue::getStatusId, Collectors.counting()));

        List<Map<String, Object>> items = new ArrayList<>();
        for (IssueStatus status : statuses) {
            long count = grouped.getOrDefault(status.getId(), 0L);
            if (count > 0) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", status.getName());
                item.put("value", count);
                item.put("color", status.getColor());
                item.put("category", status.getCategory());
                items.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", issues.size());
        return result;
    }

    private Map<String, Object> buildPriorityDistribution(List<Issue> issues) {
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels", labels);
        result.put("data", data);
        result.put("colors", colors);
        result.put("total", issues.size());
        return result;
    }

    private Map<String, Object> buildTypeDistribution(List<Issue> issues) {
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

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, Long> entry : grouped.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("value", entry.getValue());
            item.put("color", typeColors.getOrDefault(entry.getKey(), "#6b7280"));
            items.add(item);
        }
        items.sort((a, b) -> Long.compare((Long) b.get("value"), (Long) a.get("value")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", issues.size());
        return result;
    }

    private Map<String, Object> buildWorkload(List<Issue> issues, Set<Long> closedIds) {
        Map<Long, Long> grouped = issues.stream()
                .filter(i -> i.getAssigneeId() != null)
                .collect(Collectors.groupingBy(Issue::getAssigneeId, Collectors.counting()));

        long unassigned = issues.stream().filter(i -> i.getAssigneeId() == null).count();

        Set<Long> userIds = grouped.keySet();
        Map<Long, String> nameMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : grouped.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", nameMap.getOrDefault(entry.getKey(), "未知用户"));
            item.put("value", entry.getValue());
            long doneCount = issues.stream()
                    .filter(i -> entry.getKey().equals(i.getAssigneeId()))
                    .filter(i -> closedIds.contains(i.getStatusId()))
                    .count();
            item.put("done", doneCount);
            item.put("inProgress", entry.getValue() - doneCount);
            items.add(item);
        }
        items.sort((a, b) -> Long.compare((Long) b.get("value"), (Long) a.get("value")));

        if (unassigned > 0) {
            Map<String, Object> unassignedItem = new LinkedHashMap<>();
            unassignedItem.put("name", "未分配");
            unassignedItem.put("value", unassigned);
            unassignedItem.put("done", 0L);
            unassignedItem.put("inProgress", unassigned);
            items.add(unassignedItem);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", issues.size());
        return result;
    }

    private Map<String, Object> buildOverview(List<Issue> issues, Set<Long> closedIds) {
        long total = issues.size();
        long open = issues.stream().filter(i -> !closedIds.contains(i.getStatusId())).count();
        long closed = total - open;
        long unassigned = issues.stream().filter(i -> i.getAssigneeId() == null).count();
        long overdue = issues.stream()
                .filter(i -> i.getDueDate() != null && i.getDueDate().isBefore(LocalDate.now()))
                .filter(i -> !closedIds.contains(i.getStatusId()))
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("open", open);
        result.put("closed", closed);
        result.put("unassigned", unassigned);
        result.put("overdue", overdue);
        result.put("completionRate", total > 0 ? Math.round(closed * 100.0 / total) : 0);
        return result;
    }

    // ─── Trend & Burndown (separate queries by nature) ──────────────────

    /**
     * 工单趋势（每日新建/关闭 — 折线图）
     */
    public Map<String, Object> getTrend(Long projectId, LocalDate startDate, LocalDate endDate) {
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("created", createdData);
        result.put("resolved", resolvedData);
        return result;
    }

    /**
     * Sprint 燃尽图数据
     */
    public Map<String, Object> getBurndown(Long projectId, Long sprintId) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null || sprint.getStartDate() == null || sprint.getEndDate() == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("dates", List.of());
            empty.put("ideal", List.of());
            empty.put("actual", List.of());
            empty.put("sprintName", sprint != null ? sprint.getName() : "");
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("ideal", ideal);
        result.put("actual", actual);
        result.put("sprintName", sprint.getName());
        result.put("totalIssues", totalIssues);
        return result;
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
