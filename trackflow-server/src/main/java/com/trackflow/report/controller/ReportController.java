package com.trackflow.report.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.converter.ReportConverter;
import com.trackflow.report.dto.CreateReportDTO;
import com.trackflow.report.service.ReportService;
import com.trackflow.report.vo.ReportDefinitionVO;
import com.trackflow.report.vo.ReportExecuteResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        reportService.deleteWithAccessCheck(id, userId);
        return R.ok();
    }

    @GetMapping("/{id}/data")
    @PreAuthorize("isAuthenticated()")
    public R<ReportExecuteResultVO> execute(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportService.executeWithAccessCheck(id, userId));
    }
}
