package com.trackflow.report.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.converter.ReportConverter;
import com.trackflow.report.dto.CreateReportDTO;
import com.trackflow.report.dto.UpdateReportDTO;
import com.trackflow.report.service.ReportService;
import com.trackflow.report.vo.ReportDefinitionVO;
import com.trackflow.report.vo.ReportExecuteResultVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
            projectService.assertProjectMember(userId, projectId);
        }
        return R.ok(reportConverter.toVOList(reportService.list(projectId, userId)));
    }

    @PostMapping
    @PreAuthorize("@perm.check(#dto.projectId, 'project:edit')")
    public R<ReportDefinitionVO> create(@Valid @RequestBody CreateReportDTO dto) {
        return R.ok(reportConverter.toVO(reportService.create(dto)));
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
}
