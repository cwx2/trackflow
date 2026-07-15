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
     */
    @GetMapping("/charts")
    @PreAuthorize("isAuthenticated()")
    public R<DashboardChartsVO> charts() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getCharts(userId));
    }

    /**
     * 分配给我的工单（最近 10 条，按优先级+更新时间排序）
     */
    @GetMapping("/assigned-to-me")
    @PreAuthorize("isAuthenticated()")
    public R<List<IssueVO>> assignedToMe(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getAssignedToMe(userId, limit));
    }

    /**
     * 即将到期工单（7 天内到期且未关闭）
     */
    @GetMapping("/overdue")
    @PreAuthorize("isAuthenticated()")
    public R<List<IssueVO>> overdue(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getOverdueIssues(userId, days, limit));
    }

    /**
     * 最近活动流（用户相关工单的变更记录）
     */
    @GetMapping("/activity")
    @PreAuthorize("isAuthenticated()")
    public R<List<DashboardActivityVO>> activity(
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getRecentActivity(userId, limit));
    }
}
