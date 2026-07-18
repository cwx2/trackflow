package com.trackflow.dashboard.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.dashboard.service.DashboardService;
import com.trackflow.dashboard.vo.DashboardChartsVO;
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
     * 仪表盘图表数据（趋势、状态分布、工作负载）
     * @param projectId 可选项目筛选，传入时只查询该项目的数据
     * @deprecated 请使用 ReportStatisticsController.dashboard() 端点 (GET /api/v1/reports/statistics/dashboard)，
     *             数据口径统一且有 Redis 缓存。此端点将在未来版本移除。
     */
    @Deprecated
    @GetMapping("/charts")
    @PreAuthorize("isAuthenticated()")
    public R<DashboardChartsVO> charts(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getCharts(userId, projectId));
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
}
