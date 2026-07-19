package com.trackflow.report.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.service.ReportStatisticsService;
import com.trackflow.report.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 报表统计数据 API — 提供仪表盘图表所需的统计数据
 */
@RestController
@RequestMapping("/api/v1/reports/statistics")
@RequiredArgsConstructor
public class ReportStatisticsController {

    private final ReportStatisticsService statisticsService;
    private final ProjectService projectService;

    /**
     * 获取仪表盘全量统计数据（一次请求获取所有图表数据）
     * projectId 可选：不传时返回用户有权限的全部项目聚合数据
     */
    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public R<DashboardVO> dashboard(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "sprintId", required = false) Long sprintId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (projectId != null) {
            projectService.assertProjectAccessible(userId, projectId);
        }
        return R.ok(statisticsService.getDashboardData(projectId, sprintId, startDate, endDate, userId));
    }

    /**
     * 获取工单状态分布
     */
    @GetMapping("/status-distribution")
    @PreAuthorize("isAuthenticated()")
    public R<StatusDistributionVO> statusDistribution(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "sprintId", required = false) Long sprintId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(userId, projectId);
        return R.ok(statisticsService.getStatusDistribution(projectId, sprintId));
    }

    /**
     * 获取优先级分布
     */
    @GetMapping("/priority-distribution")
    @PreAuthorize("isAuthenticated()")
    public R<PriorityDistributionVO> priorityDistribution(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "sprintId", required = false) Long sprintId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(userId, projectId);
        return R.ok(statisticsService.getPriorityDistribution(projectId, sprintId));
    }

    /**
     * 获取工单趋势（每日新建/关闭）
     */
    @GetMapping("/trend")
    @PreAuthorize("isAuthenticated()")
    public R<TrendVO> trend(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(userId, projectId);
        return R.ok(statisticsService.getTrend(projectId, startDate, endDate));
    }

    /**
     * 获取团队工作负载（按负责人统计）
     */
    @GetMapping("/workload")
    @PreAuthorize("isAuthenticated()")
    public R<WorkloadVO> workload(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "sprintId", required = false) Long sprintId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(userId, projectId);
        return R.ok(statisticsService.getWorkload(projectId, sprintId));
    }

    /**
     * 获取工单类型分布
     */
    @GetMapping("/type-distribution")
    @PreAuthorize("isAuthenticated()")
    public R<TypeDistributionVO> typeDistribution(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "sprintId", required = false) Long sprintId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(userId, projectId);
        return R.ok(statisticsService.getTypeDistribution(projectId, sprintId));
    }

    /**
     * 获取 Sprint 燃尽图数据
     */
    @GetMapping("/burndown")
    @PreAuthorize("isAuthenticated()")
    public R<BurndownVO> burndown(
            @RequestParam("projectId") Long projectId,
            @RequestParam("sprintId") Long sprintId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(userId, projectId);
        return R.ok(statisticsService.getBurndown(projectId, sprintId));
    }

    /**
     * 获取累积流图数据（Cumulative Flow Diagram）
     */
    @GetMapping("/cumulative-flow")
    @PreAuthorize("isAuthenticated()")
    public R<CumulativeFlowVO> cumulativeFlow(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(userId, projectId);
        return R.ok(statisticsService.getCumulativeFlow(projectId, startDate, endDate));
    }

    /**
     * 获取解决时间分析数据（Resolution Time）
     */
    @GetMapping("/resolution-time")
    @PreAuthorize("isAuthenticated()")
    public R<ResolutionTimeVO> resolutionTime(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "groupBy", required = false) String groupBy) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectAccessible(userId, projectId);
        return R.ok(statisticsService.getResolutionTime(projectId, startDate, endDate, groupBy));
    }

    /**
     * 获取时间报表（Time Report）
     * 按人员/项目/工作类型汇总工时，含趋势和交叉维度
     * projectId 可选：不传时返回用户有权限的全部项目聚合数据
     */
    @GetMapping("/time-report")
    @PreAuthorize("isAuthenticated()")
    public R<TimeReportVO> timeReport(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (projectId != null) {
            projectService.assertProjectAccessible(userId, projectId);
        }
        return R.ok(statisticsService.getTimeReport(projectId, startDate, endDate, userId));
    }

    /**
     * 获取预估对比报表（Estimation Report）
     * 对比预估工时 vs 实际花费
     * projectId 可选：不传时返回用户有权限的全部项目数据
     * 支持分页：page（默认1）, pageSize（默认50，最大200）
     */
    @GetMapping("/estimation-report")
    @PreAuthorize("isAuthenticated()")
    public R<EstimationReportVO> estimationReport(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (projectId != null) {
            projectService.assertProjectAccessible(userId, projectId);
        }
        // 约束 pageSize 范围
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 1;
        if (pageSize > 200) pageSize = 200;
        return R.ok(statisticsService.getEstimationReport(projectId, userId, page, pageSize));
    }
}
