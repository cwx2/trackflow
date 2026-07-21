package com.trackflow.report.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.report.dto.CreateDashboardDTO;
import com.trackflow.report.dto.CreateWidgetDTO;
import com.trackflow.report.dto.ShareDashboardDTO;
import com.trackflow.report.dto.UpdateDashboardDTO;
import com.trackflow.report.dto.UpdateLayoutDTO;
import com.trackflow.report.dto.UpdateWidgetDTO;
import com.trackflow.report.service.CustomDashboardService;
import com.trackflow.report.vo.DashboardDetailVO;
import com.trackflow.report.vo.DashboardListVO;
import com.trackflow.report.vo.DashboardShareVO;
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
    @PreAuthorize("@perm.canViewReports()")
    public R<List<DashboardListVO>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.list(userId));
    }

    /**
     * 获取仪表盘详情（含所有 Widget）
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.canViewReports()")
    public R<DashboardDetailVO> getDetail(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getDetail(id, userId));
    }

    /**
     * 创建仪表盘
     */
    @PostMapping
    @PreAuthorize("@perm.canCreateReports()")
    public R<DashboardDetailVO> create(@Valid @RequestBody CreateDashboardDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.create(dto, userId));
    }

    /**
     * 更新仪表盘（名称/描述/共享）
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.canViewReports()")
    public R<DashboardDetailVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateDashboardDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.update(id, dto, userId));
    }

    /**
     * 删除仪表盘
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.canViewReports()")
    public R<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        dashboardService.delete(id, userId);
        return R.ok();
    }

    // ─── 共享管理 ────────────────────────────────────────

    /**
     * 切换仪表盘收藏状态
     */
    @PostMapping("/{id}/favorite")
    @PreAuthorize("@perm.canViewReports()")
    public R<Boolean> toggleFavorite(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean favorited = dashboardService.toggleFavorite(id, userId);
        return R.ok(favorited);
    }

    /**
     * 设为默认仪表盘
     */
    @PutMapping("/{id}/default")
    @PreAuthorize("@perm.canViewReports()")
    public R<Void> setDefault(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        dashboardService.setDefault(id, userId);
        return R.ok();
    }

    /**
     * 取消默认仪表盘
     */
    @DeleteMapping("/default")
    @PreAuthorize("@perm.canViewReports()")
    public R<Void> unsetDefault() {
        Long userId = SecurityUtils.getCurrentUserId();
        dashboardService.unsetDefault(userId);
        return R.ok();
    }

    /**
     * 获取当前用户的默认仪表盘 ID
     */
    @GetMapping("/default")
    @PreAuthorize("@perm.canViewReports()")
    public R<String> getDefault() {
        Long userId = SecurityUtils.getCurrentUserId();
        Long defaultId = dashboardService.getUserDefaultDashboardId(userId);
        return R.ok(defaultId != null ? String.valueOf(defaultId) : null);
    }

    // ─── 共享管理（精细化） ────────────────────────────────

    /**
     * 获取仪表盘的共享列表
     */
    @GetMapping("/{id}/shares")
    @PreAuthorize("@perm.canViewReports()")
    public R<List<DashboardShareVO>> getShares(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getShares(id, userId));
    }

    /**
     * 设置仪表盘共享（覆盖模式）
     */
    @PutMapping("/{id}/shares")
    @PreAuthorize("@perm.canViewReports()")
    public R<List<DashboardShareVO>> setShares(@PathVariable("id") Long id,
                                                @Valid @RequestBody ShareDashboardDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.setShares(id, dto, userId));
    }

    /**
     * 移除单条共享
     */
    @DeleteMapping("/{id}/shares/{shareId}")
    @PreAuthorize("@perm.canViewReports()")
    public R<Void> removeShare(@PathVariable("id") Long id, @PathVariable("shareId") Long shareId) {
        Long userId = SecurityUtils.getCurrentUserId();
        dashboardService.removeShare(id, shareId, userId);
        return R.ok();
    }

    // ─── Widget 管理 ────────────────────────────────────────

    /**
     * 添加 Widget 到仪表盘
     */
    @PostMapping("/{id}/widgets")
    @PreAuthorize("@perm.canViewReports()")
    public R<DashboardWidgetVO> addWidget(@PathVariable("id") Long id, @Valid @RequestBody CreateWidgetDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.addWidget(id, dto, userId));
    }

    /**
     * 更新 Widget（配置/位置/大小）
     */
    @PutMapping("/{id}/widgets/{widgetId}")
    @PreAuthorize("@perm.canViewReports()")
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
    @PreAuthorize("@perm.canViewReports()")
    public R<Void> deleteWidget(@PathVariable("id") Long id, @PathVariable("widgetId") Long widgetId) {
        Long userId = SecurityUtils.getCurrentUserId();
        dashboardService.deleteWidget(id, widgetId, userId);
        return R.ok();
    }

    /**
     * 批量更新 Widget 位置（拖拽后保存布局）
     */
    @PutMapping("/{id}/layout")
    @PreAuthorize("@perm.canViewReports()")
    public R<Void> updateLayout(@PathVariable("id") Long id, @Valid @RequestBody UpdateLayoutDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        dashboardService.updateLayout(id, dto, userId);
        return R.ok();
    }
}
