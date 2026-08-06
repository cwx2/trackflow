package com.trackflow.report.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.converter.ReportConverter;
import com.trackflow.report.dto.CreateReportDTO;
import com.trackflow.report.dto.ShareReportDTO;
import com.trackflow.report.dto.UpdateReportDTO;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.service.ReportService;
import com.trackflow.report.vo.ReportDefinitionVO;
import com.trackflow.report.vo.ReportExecuteResultVO;
import com.trackflow.report.vo.ReportGroupByOptionVO;
import com.trackflow.report.vo.ReportShareVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportConverter reportConverter;
    private final ProjectService projectService;

    @GetMapping
    @PreAuthorize("@perm.canViewReports()")
    public R<List<ReportDefinitionVO>> list(@RequestParam(value = "projectId", required = false) Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (projectId != null) {
            projectService.assertProjectAccessible(userId, projectId);
        }
        return R.ok(reportService.listReportsVO(projectId, userId));
    }

    /**
     * 获取可用的分组维度列表（内置 + 自定义字段）
     * GET /api/v1/reports/group-by-options?projectId=xxx
     */
    @GetMapping("/group-by-options")
    @PreAuthorize("@perm.canViewReports()")
    public R<List<ReportGroupByOptionVO>> getGroupByOptions(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        if (projectId != null) {
            Long userId = SecurityUtils.getCurrentUserId();
            projectService.assertProjectAccessible(userId, projectId);
        }
        return R.ok(reportService.getAvailableGroupByDimensions(projectId));
    }

    @PostMapping
    @PreAuthorize("@perm.canCreateReports()")
    public R<ReportDefinitionVO> create(@Valid @RequestBody CreateReportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportConverter.toVO(reportService.createWithAccessCheck(dto, userId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.canEditReports()")
    public R<ReportDefinitionVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateReportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportConverter.toVO(reportService.updateWithAccessCheck(id, dto, userId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.canEditReports()")
    public R<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        reportService.deleteWithAccessCheck(id, userId);
        return R.ok();
    }

    /**
     * 获取单个报表定义详情
     * GET /api/v1/reports/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.canViewReports()")
    public R<ReportDefinitionVO> getById(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        ReportDefinition report = reportService.getWithAccessCheck(id, userId);
        ReportDefinitionVO vo = reportConverter.toVO(report);
        // 填充元数据
        vo.setFavorited(reportService.isFavorited(id, userId));
        vo.setShareCount(reportService.getShareCount(id));
        if (report.getCreatedBy() != null) {
            vo.setOwnerDisplayName(reportService.getOwnerDisplayName(report.getCreatedBy()));
        }
        return R.ok(vo);
    }

    @GetMapping("/{id}/data")
    @PreAuthorize("@perm.canViewReports()")
    public R<ReportExecuteResultVO> execute(@PathVariable("id") Long id,
                                            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportService.executeWithAccessCheck(id, userId, force));
    }

    /**
     * 克隆报表
     * POST /api/v1/reports/{id}/clone
     */
    @PostMapping("/{id}/clone")
    @PreAuthorize("@perm.canCreateReports()")
    public R<ReportDefinitionVO> clone(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportConverter.toVO(reportService.clone(id, userId)));
    }

    /**
     * 切换报表收藏状态
     * POST /api/v1/reports/{id}/favorite
     *
     * @return true=已收藏, false=已取消收藏
     */
    @PostMapping("/{id}/favorite")
    @PreAuthorize("@perm.canViewReports()")
    public R<Boolean> toggleFavorite(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean favorited = reportService.toggleFavorite(id, userId);
        return R.ok(favorited);
    }

    /**
     * 导出报表为 CSV 或 Excel
     * GET /api/v1/reports/{id}/export?format=csv|xlsx
     */
    @GetMapping("/{id}/export")
    @PreAuthorize("@perm.canViewReports()")
    public void export(@PathVariable("id") Long id,
                       @RequestParam(value = "format", defaultValue = "csv") String format,
                       HttpServletResponse response) throws IOException {
        Long userId = SecurityUtils.getCurrentUserId();
        reportService.exportToResponse(id, format, userId, response);
    }

    // ─── 共享管理 ────────────────────────────────────────

    /**
     * 获取报表的共享列表
     */
    @GetMapping("/{id}/shares")
    @PreAuthorize("@perm.canViewReports()")
    public R<List<ReportShareVO>> getShares(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportService.getShares(id, userId));
    }

    /**
     * 设置报表共享（覆盖模式）
     */
    @PutMapping("/{id}/shares")
    @PreAuthorize("@perm.canEditReports()")
    public R<List<ReportShareVO>> setShares(@PathVariable("id") Long id,
                                            @Valid @RequestBody ShareReportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportService.setShares(id, dto, userId));
    }

    /**
     * 移除单条共享
     */
    @DeleteMapping("/{id}/shares/{shareId}")
    @PreAuthorize("@perm.canEditReports()")
    public R<Void> removeShare(@PathVariable("id") Long id, @PathVariable("shareId") Long shareId) {
        Long userId = SecurityUtils.getCurrentUserId();
        reportService.removeShare(id, shareId, userId);
        return R.ok();
    }
}
