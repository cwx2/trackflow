package com.trackflow.report.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.service.ProjectService;
import com.trackflow.report.converter.ReportConverter;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.service.ReportService;
import com.trackflow.report.vo.ReportDefinitionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportConverter reportConverter;
    private final ProjectService projectService;

    @GetMapping
    public R<List<ReportDefinitionVO>> list(@RequestParam(required = false) Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (projectId != null) {
            projectService.assertProjectMember(userId, projectId);
        }
        return R.ok(reportConverter.toVOList(reportService.list(projectId, userId)));
    }

    // TODO: 历史债务 — 应创建 CreateReportDTO 替代 Entity 入参，避免持久化注解暴露到 Controller 层
    @PostMapping
    @PreAuthorize("@perm.check(#report.projectId, 'project:edit')")
    public R<ReportDefinitionVO> create(@RequestBody ReportDefinition report) {
        return R.ok(reportConverter.toVO(reportService.create(report)));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        reportService.deleteWithAccessCheck(id, userId);
        return R.ok();
    }

    @GetMapping("/{id}/data")
    public R<Map<String, Object>> execute(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(reportService.executeWithAccessCheck(id, userId));
    }
}
