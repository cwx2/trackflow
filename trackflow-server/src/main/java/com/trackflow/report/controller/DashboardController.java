package com.trackflow.report.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.report.dto.CreateDashboardDTO;
import com.trackflow.report.dto.CreateWidgetDTO;
import com.trackflow.report.dto.UpdateDashboardDTO;
import com.trackflow.report.dto.UpdateLayoutDTO;
import com.trackflow.report.dto.UpdateWidgetDTO;
import com.trackflow.report.service.CustomDashboardService;
import com.trackflow.report.vo.DashboardDetailVO;
import com.trackflow.report.vo.DashboardListVO;
import com.trackflow.report.vo.DashboardWidgetVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自定义仪表盘 API
 */
@RestController("reportDashboardController")
@RequestMapping("/api/v1/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final CustomDashboardService dashboardService;

    /**
     * 获取仪表盘列表（当前用户拥有的 + 共享的）
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<List<DashboardListVO>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.list(userId));
    }

    /**
     * 获取仪表盘详情（含所有 Widget）
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<DashboardDetailVO> getDetail(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getDetail(id, userId));
    }

    /**
     * 创建仪表盘
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<DashboardDetailVO> create(@Valid @RequestBody CreateDashboardDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.create(dto, userId));
    }

    /**
     * 更新仪表盘（名称/描述/共享）
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<DashboardDetailVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateDashboardDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.update(id, dto, userId));
    }

    /**
     * 删除仪表盘
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        dashboardService.delete(id, userId);
        return R.ok();
    }

    /**
     * 添加 Widget 到仪表盘
     */
    @PostMapping("/{id}/widgets")
    @PreAuthorize("isAuthenticated()")
    public R<DashboardWidgetVO> addWidget(@PathVariable("id") Long id, @Valid @RequestBody CreateWidgetDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.addWidget(id, dto, userId));
    }

    /**
     * 更新 Widget（配置/位置/大小）
     */
    @PutMapping("/{id}/widgets/{widgetId}")
    @PreAuthorize("isAuthenticated()")
    public R<DashboardWidgetVO> updateWidget(
            @PathVariable("id") Long id,
            @PathVariable("widgetId") Long widgetId,
            @Valid @RequestBody UpdateWidgetDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.updateWidget(id, widgetId, dto, userId));
    }

    /**
     * 删除 Widget
     */
    @DeleteMapping("/{id}/widgets/{widgetId}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> deleteWidget(@PathVariable("id") Long id, @PathVariable("widgetId") Long widgetId) {
        Long userId = SecurityUtils.getCurrentUserId();
        dashboardService.deleteWidget(id, widgetId, userId);
        return R.ok();
    }

    /**
     * 批量更新 Widget 位置（拖拽后保存布局）
     */
    @PutMapping("/{id}/layout")
    @PreAuthorize("isAuthenticated()")
    public R<Void> updateLayout(@PathVariable("id") Long id, @Valid @RequestBody UpdateLayoutDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        dashboardService.updateLayout(id, dto, userId);
        return R.ok();
    }
}
