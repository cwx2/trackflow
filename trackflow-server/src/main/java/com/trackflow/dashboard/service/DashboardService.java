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
import com.trackflow.project.entity.Project;
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
    private final SysUserMapper sysUserMapper;

    /**
     * 获取仪表盘统计概览
     */
    public DashboardSummaryVO getSummary(Long userId) {
        DashboardSummaryVO vo = new DashboardSummaryVO();

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

        // 本周已完成（状态为 done 且本周更新的）
        LocalDateTime weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        vo.setCompletedThisWeek(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .eq("assignee_id", userId)
                .in("status_id", doneStatusIds)
                .ge("updated_at", weekStart)));

        // 即将到期（7天内）
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysLater = today.plusDays(7);
        vo.setDueSoon(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .eq("assignee_id", userId)
                .notIn("status_id", doneStatusIds)
                .ge("due_date", today)
                .le("due_date", sevenDaysLater)));

        // 逾期未完成
        vo.setOverdue(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .eq("assignee_id", userId)
                .notIn("status_id", doneStatusIds)
                .lt("due_date", today)));

        // 我报告的未解决
        vo.setReportedByMeOpen(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .eq("reporter_id", userId)
                .notIn("status_id", doneStatusIds)));

        // 总工单数
        vo.setTotalIssues(issueMapper.selectCount(new QueryWrapper<Issue>()
                .isNull("deleted_at")));

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
     * 即将到期工单
     */
    public List<IssueVO> getOverdueIssues(Long userId, int days, int limit) {
        List<IssueStatus> statuses = statusMapper.selectList(null);
        Set<Long> closedStatusIds = statuses.stream()
                .filter(s -> "done".equals(s.getCategory()) || "cancelled".equals(s.getCategory()))
                .map(IssueStatus::getId).collect(Collectors.toSet());

        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(days);

        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at")
                .eq("assignee_id", userId)
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
     * 最近活动流（用户相关工单的变更记录）
     */
    public List<DashboardActivityVO> getRecentActivity(Long userId, int limit) {
        // 查询用户相关的 issue IDs（我创建的 + 分配给我的）
        List<Long> relatedIssueIds = issueMapper.selectList(new QueryWrapper<Issue>()
                .isNull("deleted_at")
                .and(w -> w.eq("assignee_id", userId).or().eq("reporter_id", userId))
                .select("id"))
                .stream().map(Issue::getId).toList();

        if (relatedIssueIds.isEmpty()) {
            return List.of();
        }

        // 查询这些 issue 的活动记录
        List<Map<String, Object>> rows = issueMapper.selectDashboardActivities(relatedIssueIds, limit);

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
