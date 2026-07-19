package com.trackflow.workflow.controller;

import com.trackflow.common.model.R;
import com.trackflow.workflow.dto.WorkflowRuleDTO;
import com.trackflow.workflow.service.WorkflowRuleService;
import com.trackflow.workflow.vo.WorkflowRuleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流自动化规则 Controller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkflowRuleController {

    private final WorkflowRuleService ruleService;

    /**
     * 获取项目规则列表（含全局规则）
     * projectId=0 表示仅查全局规则
     */
    @GetMapping("/projects/{projectId}/workflow-rules")
    @PreAuthorize("T(com.trackflow.workflow.WorkflowScope).isGlobal(#projectId) ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<List<WorkflowRuleVO>> listRules(@PathVariable("projectId") Long projectId) {
        Long effectiveProjectId = com.trackflow.workflow.WorkflowScope.fromApi(projectId);
        return R.ok(ruleService.listRules(effectiveProjectId));
    }

    /**
     * 获取单个规则详情
     */
    @GetMapping("/workflow-rules/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<WorkflowRuleVO> getRule(@PathVariable("id") Long id) {
        return R.ok(ruleService.getRule(id));
    }

    /**
     * 创建规则
     */
    @PostMapping("/projects/{projectId}/workflow-rules")
    @PreAuthorize("T(com.trackflow.workflow.WorkflowScope).isGlobal(#projectId) ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<WorkflowRuleVO> createRule(@PathVariable("projectId") Long projectId,
                                         @Valid @RequestBody WorkflowRuleDTO dto) {
        Long effectiveProjectId = com.trackflow.workflow.WorkflowScope.fromApi(projectId);
        return R.ok(ruleService.createRule(effectiveProjectId, dto));
    }

    /**
     * 更新规则
     */
    @PutMapping("/workflow-rules/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<WorkflowRuleVO> updateRule(@PathVariable("id") Long id,
                                         @Valid @RequestBody WorkflowRuleDTO dto) {
        return R.ok(ruleService.updateRule(id, dto));
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/workflow-rules/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> deleteRule(@PathVariable("id") Long id) {
        ruleService.deleteRule(id);
        return R.ok();
    }

    /**
     * 切换规则启用/禁用
     */
    @PatchMapping("/workflow-rules/{id}/toggle")
    @PreAuthorize("isAuthenticated()")
    public R<WorkflowRuleVO> toggleRule(@PathVariable("id") Long id) {
        return R.ok(ruleService.toggleRule(id));
    }
}
