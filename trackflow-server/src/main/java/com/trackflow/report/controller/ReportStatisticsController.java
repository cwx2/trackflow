package com.trackflow.report.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.service.ReportStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

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
     */
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long sprintId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(userId, projectId);

        Map<String, Object> data = statisticsService.getDashboardData(projectId, sprintId, startDate, endDate);
        return R.ok(data);
    }

    /**
     * 获取工单状态分布
     */
    @GetMapping("/status-distribution")
    public R<Map<String, Object>> statusDistribution(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long sprintId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(userId, projectId);
        return R.ok(statisticsService.getStatusDistribution(projectId, sprintId));
    }

    /**
     * 获取优先级分布
     */
    @GetMapping("/priority-distribution")
    public R<Map<String, Object>> priorityDistribution(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long sprintId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(userId, projectId);
        return R.ok(statisticsService.getPriorityDistribution(projectId, sprintId));
    }

    /**
     * 获取工单趋势（每日新建/关闭）
     */
    @GetMapping("/trend")
    public R<Map<String, Object>> trend(
            @RequestParam Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(userId, projectId);
        return R.ok(statisticsService.getTrend(projectId, startDate, endDate));
    }

    /**
     * 获取团队工作负载（按负责人统计）
     */
    @GetMapping("/workload")
    public R<Map<String, Object>> workload(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long sprintId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(userId, projectId);
        return R.ok(statisticsService.getWorkload(projectId, sprintId));
    }

    /**
     * 获取工单类型分布
     */
    @GetMapping("/type-distribution")
    public R<Map<String, Object>> typeDistribution(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long sprintId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(userId, projectId);
        return R.ok(statisticsService.getTypeDistribution(projectId, sprintId));
    }

    /**
     * 获取 Sprint 燃尽图数据
     */
    @GetMapping("/burndown")
    public R<Map<String, Object>> burndown(
            @RequestParam Long projectId,
            @RequestParam Long sprintId) {
        Long userId = SecurityUtils.getCurrentUserId();
        projectService.assertProjectMember(userId, projectId);
        return R.ok(statisticsService.getBurndown(projectId, sprintId));
    }
}
