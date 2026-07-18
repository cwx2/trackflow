package com.trackflow.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trackflow.dashboard.vo.DashboardActivityVO;
import com.trackflow.dashboard.vo.DashboardSummaryVO;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.mapper.result.ActivityRow;
import com.trackflow.issue.service.StatusCacheHelper;
import com.trackflow.issue.vo.IssueVO;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.report.service.ReportStatisticsService;
import com.trackflow.report.vo.OverviewVO;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final IssueMapper issueMapper;
    private final IssueStatusMapper statusMapper;
    private final IssueConverter issueConverter;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final SysUserMapper sysUserMapper;
    private final StatusCacheHelper statusCacheHelper;
    private final ReportStatisticsService reportStatisticsService;

    /**
     * 获取仪表盘统计概览。
     * <p>
     * 重叠指标（总工单数、逾期等全局统计）委托给 ReportStatisticsService.buildOverview()，
     * 确保统计口径与报表页面一致。
     * <p>
     * 个人视角指标（分配给我、我报告的、即将到期）保持独立计算。
     */
    public DashboardSummaryVO getSummary(Long userId) {
        DashboardSummaryVO vo = new DashboardSummaryVO();

        // 获取用户所在的项目 ID 列表
        List<Long> userProjectIds = projectMemberMapper.selectProjectIdsByUserId(userId);

        // 获取用户的主要角色
        String primaryRole = determinePrimaryRole(userId, userProjectIds);
        vo.setPrimaryRoleCode(primaryRole);

        // ─── 统一状态判定（使用 StatusCacheHelper，与 ReportStatisticsService 口径一致） ───
        Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();
        Set<Long> openStatusIds = statusCacheHelper.getOpenStatusIds();

        // 细分：从开放状态中区分 open / in_progress（个人视角需要）
        List<IssueStatus> statuses = statusMapper.selectList(null);
        Set<Long> pureOpenStatusIds = statuses.stream()
                .filter(s -> "open".equals(s.getCategory()))
                .map(IssueStatus::getId).collect(Collectors.toSet());
        Set<Long> inProgressStatusIds = statuses.stream()
                .filter(s -> "in_progress".equals(s.getCategory()))
                .map(IssueStatus::getId).collect(Collectors.toSet());
        Set<Long> testingStatusIds = statuses.stream()
                .filter(s -> "testing".equals(s.getCode()))
                .map(IssueStatus::getId).collect(Collectors.toSet());

        // ─── 委托给 ReportStatisticsService 的全局统计 ───────────────────────
        if (!userProjectIds.isEmpty()) {
            OverviewVO overview = reportStatisticsService.getOverview(userProjectIds, null);
            vo.setTotalIssues(overview.getTotal());
        } else {
            vo.setTotalIssues(0L);
        }

        // ─── 个人视角统计（DashboardService 独有） ────────────────────────────

        // 分配给我的待处理
        vo.setAssignedOpen(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .eq("assignee_id", userId)
                .in("status_id", pureOpenStatusIds)));

        // 分配给我的进行中
        vo.setAssignedInProgress(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .eq("assignee_id", userId)
                .in("status_id", inProgressStatusIds)));

        // 本周已完成：分配给我或我报告的，且本周变为关闭状态
        LocalDateTime weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        vo.setCompletedThisWeek(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .and(w -> w.eq("assignee_id", userId).or().eq("reporter_id", userId))
                .in("status_id", closedStatusIds)
                .ge("updated_at", weekStart)));

        // 即将到期（7天内）：我所在项目范围内，分配给我或我报告的
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysLater = today.plusDays(7);
        if (!userProjectIds.isEmpty()) {
            vo.setDueSoon(issueMapper.selectCount(new QueryWrapper<Issue>()
                    .isNull("deleted_at")
                    .in("project_id", userProjectIds)
                    .and(w -> w.eq("assignee_id", userId).or().eq("reporter_id", userId))
                    .notIn("status_id", closedStatusIds)
                    .ge("due_date", today)
                    .le("due_date", sevenDaysLater)));

            // 逾期未完成：我所在项目范围内，分配给我或我报告的
            vo.setOverdue(issueMapper.selectCount(new QueryWrapper<Issue>()
                    .isNull("deleted_at")
                    .in("project_id", userProjectIds)
                    .and(w -> w.eq("assignee_id", userId).or().eq("reporter_id", userId))
                    .notIn("status_id", closedStatusIds)
                    .lt("due_date", today)));
        } else {
            vo.setDueSoon(0L);
            vo.setOverdue(0L);
        }

        // 我报告的未解决
        vo.setReportedByMeOpen(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .eq("reporter_id", userId)
                .notIn("status_id", closedStatusIds)));

        // 待测试工单数（Testing 状态，用户所在项目范围内）
        if (!userProjectIds.isEmpty() && !testingStatusIds.isEmpty()) {
            vo.setTestingCount(issueMapper.selectCount(new QueryWrapper<Issue>()
                    .isNull("deleted_at")
                    .in("project_id", userProjectIds)
                    .in("status_id", testingStatusIds)));
        } else {
            vo.setTestingCount(0L);
        }

        // 活跃项目数
        vo.setActiveProjects(projectMapper.selectCount(new QueryWrapper<Project>()
                .eq("status", "Active")));

        // ─── 周对比数据（基于上周同一天的快照逻辑近似） ─────
        LocalDateTime lastWeekStart = weekStart.minusWeeks(1);
        LocalDateTime lastWeekEnd = weekStart; // 上周结束 = 本周开始

        // 上周完成数量
        vo.setLastWeekCompleted(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .and(w -> w.eq("assignee_id", userId).or().eq("reporter_id", userId))
                .in("status_id", closedStatusIds)
                .ge("updated_at", lastWeekStart)
                .lt("updated_at", lastWeekEnd)));

        // 上周逾期近似
        LocalDate lastWeekToday = today.minusWeeks(1);
        if (!userProjectIds.isEmpty()) {
            vo.setLastWeekOverdue(issueMapper.selectCount(new QueryWrapper<Issue>()
                    .isNull("deleted_at")
                    .in("project_id", userProjectIds)
                    .and(w -> w.eq("assignee_id", userId).or().eq("reporter_id", userId))
                    .notIn("status_id", closedStatusIds)
                    .lt("due_date", lastWeekToday)));
        } else {
            vo.setLastWeekOverdue(0L);
        }

        // 上周同期 open/inProgress（占位，前端用 completed 做对比）
        vo.setLastWeekOpen(vo.getAssignedOpen());
        vo.setLastWeekInProgress(vo.getAssignedInProgress());

        return vo;
    }

    /**
     * 分配给我的工单（按优先级+更新时间排序）
     */
    public List<IssueVO> getAssignedToMe(Long userId, int limit) {
        // 使用 StatusCacheHelper 获取关闭状态（统一口径）
        Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();

        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at")
                .eq("assignee_id", userId)
                .notIn("status_id", closedStatusIds)
                .last("ORDER BY CASE priority WHEN 'Critical' THEN 1 WHEN 'High' THEN 2 WHEN 'Normal' THEN 3 WHEN 'Low' THEN 4 ELSE 5 END, updated_at DESC LIMIT " + Math.min(limit, 50));

        List<Issue> issues = issueMapper.selectList(wrapper);
        List<IssueVO> voList = issueConverter.toVOList(issues);

        // 填充 assigneeName
        fillAssigneeNames(issues, voList);

        return voList;
    }

    /**
     * 即将到期工单（用户所在项目范围内，分配给我或我报告的）
     */
    public List<IssueVO> getOverdueIssues(Long userId, int days, int limit) {
        // 使用 StatusCacheHelper 获取关闭状态（统一口径）
        Set<Long> closedStatusIds = statusCacheHelper.getClosedStatusIds();

        List<Long> userProjectIds = projectMemberMapper.selectProjectIdsByUserId(userId);
        if (userProjectIds.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(days);

        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at")
                .in("project_id", userProjectIds)
                .and(w -> w.eq("assignee_id", userId).or().eq("reporter_id", userId))
                .notIn("status_id", closedStatusIds)
                .isNotNull("due_date")
                .le("due_date", deadline)
                .orderByAsc("due_date")
                .last("LIMIT " + Math.min(limit, 50));

        List<Issue> issues = issueMapper.selectList(wrapper);
        List<IssueVO> voList = issueConverter.toVOList(issues);
        fillAssigneeNames(issues, voList);

        return voList;
    }

    /**
     * 最近活动流（用户所在项目的变更记录）
     * 使用 JOIN 方式基于 project_id 过滤，避免将大量 issue ID 加载到内存再传入 IN 子句。
     */
    public List<DashboardActivityVO> getRecentActivity(Long userId, int limit) {
        List<Long> userProjectIds = projectMemberMapper.selectProjectIdsByUserId(userId);

        List<ActivityRow> rows;
        if (!userProjectIds.isEmpty()) {
            // 基于项目范围的活动查询（通过 SQL JOIN 过滤，无需先加载 issue ID 列表）
            rows = issueMapper.selectDashboardActivitiesByProjects(userProjectIds, limit);
        } else {
            // 退化为仅查分配给我或我创建的工单活动
            List<Long> relatedIssueIds = issueMapper.selectList(new QueryWrapper<Issue>()
                    .isNull("deleted_at")
                    .and(w -> w.eq("assignee_id", userId).or().eq("reporter_id", userId))
                    .select("id"))
                    .stream().map(Issue::getId).toList();

            if (relatedIssueIds.isEmpty()) {
                return List.of();
            }
            rows = issueMapper.selectDashboardActivities(relatedIssueIds, limit);
        }

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        return rows.stream().map(row -> {
            DashboardActivityVO vo = new DashboardActivityVO();
            vo.setId(String.valueOf(row.getId()));
            vo.setIssueId(String.valueOf(row.getIssueId()));
            vo.setIssueKey(row.getIssueKey());
            vo.setIssueTitle(row.getIssueTitle());
            vo.setUserId(String.valueOf(row.getUserId()));
            vo.setUserName(row.getUserName());
            vo.setAction(row.getAction());
            vo.setFieldName(row.getFieldName());
            vo.setOldValue(row.getOldValue());
            vo.setNewValue(row.getNewValue());
            vo.setCreatedAt(row.getCreatedAt());
            return vo;
        }).toList();
    }

    /**
     * 确定用户的主要角色代码。
     * 优先级：tester > developer > tech_lead > product_manager > project_admin > observer
     */
    private String determinePrimaryRole(Long userId, List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return null;
        }
        // 一次查出用户在所有项目中的角色 ID（避免 N+1）
        List<Long> roleIds = projectMemberMapper.selectList(
                new QueryWrapper<ProjectMember>()
                        .eq("user_id", userId)
                        .select("role_id"))
                .stream()
                .map(ProjectMember::getRoleId)
                .distinct()
                .toList();

        // 角色优先级映射（role_id → code based on sys_role table）
        // 4=tester, 3=developer, 7=tech_lead, 6=product_manager, 2=project_admin, 5=observer, 1=system_admin
        if (roleIds.contains(4L)) return "tester";
        if (roleIds.contains(3L)) return "developer";
        if (roleIds.contains(7L)) return "tech_lead";
        if (roleIds.contains(6L)) return "product_manager";
        if (roleIds.contains(2L)) return "project_admin";
        if (roleIds.contains(1L)) return "system_admin";
        if (roleIds.contains(5L)) return "observer";
        return null;
    }

    private void fillAssigneeNames(List<Issue> issues, List<IssueVO> voList) {
        List<Long> assigneeIds = issues.stream()
                .map(Issue::getAssigneeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!assigneeIds.isEmpty()) {
            Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(assigneeIds).stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
            for (int i = 0; i < issues.size(); i++) {
                Issue issue = issues.get(i);
                if (issue.getAssigneeId() != null) {
                    SysUser user = userMap.get(issue.getAssigneeId());
                    if (user != null) {
                        voList.get(i).setAssigneeName(user.getDisplayName());
                        voList.get(i).setAssigneeAvatarUrl(user.getAvatarUrl());
                    }
                }
            }
        }
    }
}
