package com.trackflow.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trackflow.dashboard.vo.DashboardActivityVO;
import com.trackflow.dashboard.vo.DashboardSummaryVO;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.vo.IssueVO;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
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
    private final IssueActivityMapper activityMapper;
    private final IssueConverter issueConverter;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 获取仪表盘统计概览。
     * 统计范围：用户所在项目的所有工单（而非仅 assignee_id = userId）。
     * 对于有明确 assignee 的统计（待处理、进行中），仍基于 assignee_id。
     * 对于角色相关统计（如测试人员看 Testing 状态），基于项目范围。
     */
    public DashboardSummaryVO getSummary(Long userId) {
        DashboardSummaryVO vo = new DashboardSummaryVO();

        // 获取用户所在的项目 ID 列表
        List<Long> userProjectIds = projectMemberMapper.selectProjectIdsByUserId(userId);

        // 获取用户的主要角色
        String primaryRole = determinePrimaryRole(userId, userProjectIds);
        vo.setPrimaryRoleCode(primaryRole);

        // 获取状态分类
        List<IssueStatus> statuses = statusMapper.selectList(null);
        Set<Long> openStatusIds = statuses.stream()
                .filter(s -> "open".equals(s.getCategory()))
                .map(IssueStatus::getId).collect(Collectors.toSet());
        Set<Long> inProgressStatusIds = statuses.stream()
                .filter(s -> "in_progress".equals(s.getCategory()))
                .map(IssueStatus::getId).collect(Collectors.toSet());
        Set<Long> doneStatusIds = statuses.stream()
                .filter(s -> "done".equals(s.getCategory()) || "cancelled".equals(s.getCategory()))
                .map(IssueStatus::getId).collect(Collectors.toSet());
        // Testing 状态 ID（使用 code 字段匹配，比 name 更稳定，不受国际化影响）
        Set<Long> testingStatusIds = statuses.stream()
                .filter(s -> "testing".equals(s.getCode()))
                .map(IssueStatus::getId).collect(Collectors.toSet());

        // 分配给我的待处理
        vo.setAssignedOpen(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .eq("assignee_id", userId)
                .in("status_id", openStatusIds)));

        // 分配给我的进行中
        vo.setAssignedInProgress(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .eq("assignee_id", userId)
                .in("status_id", inProgressStatusIds)));

        // 本周已完成：分配给我或我报告的，且本周变为 done/cancelled 状态
        LocalDateTime weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        vo.setCompletedThisWeek(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .and(w -> w.eq("assignee_id", userId).or().eq("reporter_id", userId))
                .in("status_id", doneStatusIds)
                .ge("updated_at", weekStart)));

        // 即将到期（7天内）：我所在项目范围内，分配给我或我报告的
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysLater = today.plusDays(7);
        if (!userProjectIds.isEmpty()) {
            vo.setDueSoon(issueMapper.selectCount(new QueryWrapper<Issue>()
                    .isNull("deleted_at")
                    .in("project_id", userProjectIds)
                    .and(w -> w.eq("assignee_id", userId).or().eq("reporter_id", userId))
                    .notIn("status_id", doneStatusIds)
                    .ge("due_date", today)
                    .le("due_date", sevenDaysLater)));

            // 逾期未完成：我所在项目范围内，分配给我或我报告的
            vo.setOverdue(issueMapper.selectCount(new QueryWrapper<Issue>()
                    .isNull("deleted_at")
                    .in("project_id", userProjectIds)
                    .and(w -> w.eq("assignee_id", userId).or().eq("reporter_id", userId))
                    .notIn("status_id", doneStatusIds)
                    .lt("due_date", today)));
        } else {
            vo.setDueSoon(0L);
            vo.setOverdue(0L);
        }

        // 我报告的未解决
        vo.setReportedByMeOpen(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .eq("reporter_id", userId)
                .notIn("status_id", doneStatusIds)));

        // 待测试工单数（Testing 状态，用户所在项目范围内）
        if (!userProjectIds.isEmpty() && !testingStatusIds.isEmpty()) {
            vo.setTestingCount(issueMapper.selectCount(new QueryWrapper<Issue>()
                    .isNull("deleted_at")
                    .in("project_id", userProjectIds)
                    .in("status_id", testingStatusIds)));
        } else {
            vo.setTestingCount(0L);
        }

        // 总工单数（用户所在项目范围）
        if (!userProjectIds.isEmpty()) {
            vo.setTotalIssues(issueMapper.selectCount(new QueryWrapper<Issue>()
                    .isNull("deleted_at")
                    .in("project_id", userProjectIds)));
        } else {
            vo.setTotalIssues(0L);
        }

        // 活跃项目数
        vo.setActiveProjects(projectMapper.selectCount(new QueryWrapper<Project>()
                .eq("status", "Active")));

        return vo;
    }

    /**
     * 分配给我的工单（按优先级+更新时间排序）
     */
    public List<IssueVO> getAssignedToMe(Long userId, int limit) {
        // 获取未关闭的状态
        List<IssueStatus> statuses = statusMapper.selectList(null);
        Set<Long> closedStatusIds = statuses.stream()
                .filter(s -> "done".equals(s.getCategory()) || "cancelled".equals(s.getCategory()))
                .map(IssueStatus::getId).collect(Collectors.toSet());

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
        List<IssueStatus> statuses = statusMapper.selectList(null);
        Set<Long> closedStatusIds = statuses.stream()
                .filter(s -> "done".equals(s.getCategory()) || "cancelled".equals(s.getCategory()))
                .map(IssueStatus::getId).collect(Collectors.toSet());

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

        List<Map<String, Object>> rows;
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
            vo.setId(String.valueOf(row.get("id")));
            vo.setIssueId(String.valueOf(row.get("issue_id")));
            vo.setIssueKey((String) row.get("issue_key"));
            vo.setIssueTitle((String) row.get("issue_title"));
            vo.setUserId(String.valueOf(row.get("user_id")));
            vo.setUserName((String) row.get("user_name"));
            vo.setAction((String) row.get("action"));
            vo.setFieldName((String) row.get("field_name"));
            vo.setOldValue((String) row.get("old_value"));
            vo.setNewValue((String) row.get("new_value"));
            if (row.get("created_at") != null) {
                vo.setCreatedAt(((java.sql.Timestamp) row.get("created_at")).toLocalDateTime());
            }
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
            Map<Long, String> userNameMap = sysUserMapper.selectBatchIds(assigneeIds).stream()
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));
            for (int i = 0; i < issues.size(); i++) {
                Issue issue = issues.get(i);
                if (issue.getAssigneeId() != null) {
                    voList.get(i).setAssigneeName(userNameMap.get(issue.getAssigneeId()));
                }
            }
        }
    }
}
