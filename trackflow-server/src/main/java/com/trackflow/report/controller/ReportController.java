package com.trackflow.report.controller;

import com.trackflow.common.model.R;
import com.trackflow.report.converter.ReportConverter;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.service.ReportService;
import com.trackflow.report.vo.ReportDefinitionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportConverter reportConverter;

    @GetMapping
    public R<List<ReportDefinitionVO>> list(@RequestParam(required = false) Long projectId) {
        return R.ok(reportConverter.toVOList(reportService.list(projectId)));
    }

    @PostMapping
    public R<ReportDefinitionVO> create(@RequestBody ReportDefinition report) {
        return R.ok(reportConverter.toVO(reportService.create(report)));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        reportService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/data")
    public R<Map<String, Object>> execute(@PathVariable Long id) {
        return R.ok(reportService.execute(id));
    }
}
