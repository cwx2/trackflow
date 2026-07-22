package com.trackflow.rule.controller;

import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.rule.dto.CreateExecutionLogDTO;
import com.trackflow.rule.dto.RuleExecutionLogQuery;
import com.trackflow.rule.dto.SaveRuleDefinitionDTO;
import com.trackflow.rule.service.RuleService;
import com.trackflow.rule.vo.RuleDefinitionVO;
import com.trackflow.rule.vo.RuleExecutionLogVO;
import com.trackflow.rule.vo.RuleStatisticsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 规则引擎 API
 */
@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;

    // ==================== 规则定义管理 ====================

    @GetMapping
    @PreAuthorize("@perm.checkGlobal('rule:manage') or @perm.checkGlobal('rule:view_statistics')")
    public R<List<RuleDefinitionVO>> listRules(
            @RequestParam(required = false) Long projectId) {
        return R.ok(ruleService.listRules(projectId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('rule:manage') or @perm.checkGlobal('rule:view_statistics')")
    public R<RuleDefinitionVO> getRuleDetail(@PathVariable("id") Long id) {
        return R.ok(ruleService.getRuleDetail(id));
    }

    @PostMapping
    @PreAuthorize("@perm.checkGlobal('rule:manage')")
    public R<RuleDefinitionVO> createRule(@RequestBody @Valid SaveRuleDefinitionDTO dto) {
        return R.ok(ruleService.createRule(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('rule:manage')")
    public R<RuleDefinitionVO> updateRule(@PathVariable("id") Long id,
                                          @RequestBody @Valid SaveRuleDefinitionDTO dto) {
        return R.ok(ruleService.updateRule(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('rule:manage')")
    public R<Void> deleteRule(@PathVariable("id") Long id) {
        ruleService.deleteRule(id);
        return R.ok();
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("@perm.checkGlobal('rule:manage')")
    public R<Void> toggleRule(@PathVariable("id") Long id) {
        ruleService.toggleRule(id);
        return R.ok();
    }

    @PostMapping("/{id}/execute-now")
    @PreAuthorize("@perm.checkGlobal('rule:manage')")
    public R<Map<String, Object>> executeNow(@PathVariable("id") Long id) {
        int count = ruleService.executeRuleNow(id);
        return R.ok(Map.of("executedCount", count));
    }

    // ==================== 执行记录 ====================

    @GetMapping("/logs")
    @PreAuthorize("@perm.checkGlobal('rule:manage') or @perm.checkGlobal('rule:view_statistics')")
    public R<PageResult<RuleExecutionLogVO>> listLogs(RuleExecutionLogQuery query) {
        return R.ok(ruleService.listLogs(query));
    }

    @PostMapping("/logs")
    @PreAuthorize("@perm.checkGlobal('rule:manage')")
    public R<RuleExecutionLogVO> createLog(@RequestBody @Valid CreateExecutionLogDTO dto) {
        return R.ok(ruleService.createLog(dto));
    }

    @DeleteMapping("/logs/{id}")
    @PreAuthorize("@perm.checkGlobal('rule:manage')")
    public R<Void> deleteLog(@PathVariable("id") Long id) {
        ruleService.deleteLog(id);
        return R.ok();
    }

    // ==================== 统计 ====================

    @GetMapping("/statistics")
    @PreAuthorize("@perm.checkGlobal('rule:view_statistics')")
    public R<RuleStatisticsVO> getStatistics(
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "20") int rankLimit) {
        return R.ok(ruleService.getStatistics(ruleId, startDate, endDate, rankLimit));
    }

    // ==================== Issue 关联 ====================

    @GetMapping("/issues/{issueId}/logs")
    @PreAuthorize("@perm.checkGlobal('rule:view_statistics')")
    public R<List<RuleExecutionLogVO>> getIssueRuleLogs(@PathVariable("issueId") Long issueId) {
        return R.ok(ruleService.getIssueRuleLogs(issueId));
    }

    @GetMapping("/issues/{issueId}/total-score")
    @PreAuthorize("@perm.checkGlobal('rule:view_statistics')")
    public R<Map<String, Object>> getIssueTotalScore(@PathVariable("issueId") Long issueId) {
        BigDecimal total = ruleService.getIssueTotalScore(issueId);
        return R.ok(Map.of("totalScore", total));
    }
}
