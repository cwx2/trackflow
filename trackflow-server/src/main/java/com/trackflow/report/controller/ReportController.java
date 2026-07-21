package com.trackflow.report.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.converter.ReportConverter;
import com.trackflow.report.dto.CreateReportDTO;
import com.trackflow.report.dto.ShareReportDTO;
import com.trackflow.report.dto.UpdateReportDTO;
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
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportConverter reportConverter;
    private final ProjectService projectService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<List<ReportDefinitionVO>> list(@RequestParam(value = "projectId", required = false) Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (projectId != null) {
            projectService.assertProjectAccessible(userId, projectId);
        }
        List<ReportDefinitionVO> voList = reportConverter.toVOList(reportService.list(projectId, userId));
        // 填充共享数量和收藏状态
        Set<Long> favoriteIds = reportService.getUserFavoriteReportIds(userId);
        for (ReportDefinitionVO vo : voList) {
            vo.setShareCount(reportService.getShareCount(Long.parseLong(vo.getId())));
            vo.setFavorited(favoriteIds.contains(Long.parseLong(vo.getId())));
        }
        // 排序：收藏的在前，然后按名称
        voList.sort((a, b) -> {
            boolean aFav = Boolean.TRUE.equals(a.getFavorited());
            boolean bFav = Boolean.TRUE.equals(b.getFavorited());
            if (aFav != bFav) return aFav ? -1 : 1;
            return (a.getName() != null ? a.getName() : "").compareTo(b.getName() != null ? b.getName() : "");
        });
        return R.ok(voList);
    }

    /**
     * 获取可用的分组维度列表（内置 + 自定义字段）
     * GET /api/v1/reports/group-by-options?projectId=xxx
     */
    @GetMapping("/group-by-options")
    @PreAuthorize("isAuthenticated()")
    public R<List<ReportGroupByOptionVO>> getGroupByOptions(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        if (projectId != null) {
            Long userId = SecurityUtils.getCurrentUserId();
            projectService.assertProjectAccessible(userId, projectId);
        }
        return R.ok(reportService.getAvailableGroupByDimensions(projectId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<ReportDefinitionVO> create(@Valid @RequestBody CreateReportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportConverter.toVO(reportService.createWithAccessCheck(dto, userId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<ReportDefinitionVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateReportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportConverter.toVO(reportService.updateWithAccessCheck(id, dto, userId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        reportService.deleteWithAccessCheck(id, userId);
        return R.ok();
    }

    @GetMapping("/{id}/data")
    @PreAuthorize("isAuthenticated()")
    public R<ReportExecuteResultVO> execute(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportService.executeWithAccessCheck(id, userId));
    }

    /**
     * 克隆报表
     * POST /api/v1/reports/{id}/clone
     */
    @PostMapping("/{id}/clone")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
    public R<Boolean> toggleFavorite(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean favorited = reportService.toggleFavorite(id, userId);
        return R.ok(favorited);
    }

    /**
     * 导出报表为 CSV
     * GET /api/v1/reports/{id}/export?format=csv
     */
    @GetMapping("/{id}/export")
    @PreAuthorize("isAuthenticated()")
    public void export(@PathVariable("id") Long id,
                       @RequestParam(value = "format", defaultValue = "csv") String format,
                       HttpServletResponse response) throws IOException {
        Long userId = SecurityUtils.getCurrentUserId();

        if (!"csv".equalsIgnoreCase(format)) {
            response.setStatus(400);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":40000,\"message\":\"仅支持 CSV 格式导出\"}");
            return;
        }

        String csv = reportService.exportCsv(id, userId);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"report-" + id + ".csv\"");
        // BOM for Excel UTF-8 recognition
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        try (OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(csv);
            writer.flush();
        }
    }

    // ─── 共享管理 ────────────────────────────────────────

    /**
     * 获取报表的共享列表
     */
    @GetMapping("/{id}/shares")
    @PreAuthorize("isAuthenticated()")
    public R<List<ReportShareVO>> getShares(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportService.getShares(id, userId));
    }

    /**
     * 设置报表共享（覆盖模式）
     */
    @PutMapping("/{id}/shares")
    @PreAuthorize("isAuthenticated()")
    public R<List<ReportShareVO>> setShares(@PathVariable("id") Long id,
                                            @Valid @RequestBody ShareReportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportService.setShares(id, dto, userId));
    }

    /**
     * 移除单条共享
     */
    @DeleteMapping("/{id}/shares/{shareId}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> removeShare(@PathVariable("id") Long id, @PathVariable("shareId") Long shareId) {
        Long userId = SecurityUtils.getCurrentUserId();
        reportService.removeShare(id, shareId, userId);
        return R.ok();
    }
}
