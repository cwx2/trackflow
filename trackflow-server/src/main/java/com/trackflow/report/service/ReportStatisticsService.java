package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
