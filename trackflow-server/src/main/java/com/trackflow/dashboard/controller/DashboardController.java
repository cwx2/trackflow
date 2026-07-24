package com.trackflow.dashboard.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.dashboard.service.DashboardService;
import com.trackflow.dashboard.vo.DashboardSummaryVO;
import com.trackflow.dashboard.vo.DashboardActivityVO;
import com.trackflow.issue.vo.IssueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 仪表盘统计概览
     */
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public R<DashboardSummaryVO> summary() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getSummary(userId));
    }

    /**
     * 分配给我的工单（最近 10 条，按优先级+更新时间排序）
     */
    @GetMapping("/assigned-to-me")
    @PreAuthorize("isAuthenticated()")
    public R<List<IssueVO>> assignedToMe(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getAssignedToMe(userId, limit));
    }

    /**
     * 即将到期工单（7 天内到期且未关闭）
     */
    @GetMapping("/overdue")
    @PreAuthorize("isAuthenticated()")
    public R<List<IssueVO>> overdue(
            @RequestParam(value = "days", defaultValue = "7") int days,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getOverdueIssues(userId, days, limit));
    }

    /**
     * 最近活动流（用户相关工单的变更记录）
     */
    @GetMapping("/activity")
    @PreAuthorize("isAuthenticated()")
    public R<List<DashboardActivityVO>> activity(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getRecentActivity(userId, limit));
    }

    /**
     * Widget 活动流（支持多维筛选：项目/活动类型/用户）
     * 用于 Activity Feed Widget 的数据加载
     */
    @GetMapping("/activity-feed")
    @PreAuthorize("isAuthenticated()")
    public R<List<DashboardActivityVO>> activityFeed(
            @RequestParam(value = "projectIds", required = false) List<Long> projectIds,
            @RequestParam(value = "actions", required = false) List<String> actions,
            @RequestParam(value = "userIds", required = false) List<Long> userIds,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        // 限制最大条数为 50
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return R.ok(dashboardService.getWidgetActivityFeed(currentUserId, projectIds, actions, userIds, safeLimit));
    }
}
