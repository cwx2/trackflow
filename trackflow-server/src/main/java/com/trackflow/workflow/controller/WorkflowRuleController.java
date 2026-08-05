package com.trackflow.workflow.controller;

import com.trackflow.common.model.R;
import com.trackflow.workflow.dto.WorkflowRuleDTO;
import com.trackflow.workflow.service.ScheduledRuleService;
import com.trackflow.workflow.service.WorkflowRuleService;
import com.trackflow.workflow.vo.WorkflowRuleExecutionLogVO;
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
    private final ScheduledRuleService scheduledRuleService;

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

    /**
     * 手动触发执行 on_schedule 规则（用于测试）
     */
    @PostMapping("/workflow-rules/{id}/execute")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<WorkflowRuleExecutionLogVO> executeRule(@PathVariable("id") Long id) {
        return R.ok(scheduledRuleService.executeRuleManually(id));
    }

    /**
     * 获取规则执行日志（需要与查看规则相同的权限）
     */
    @GetMapping("/workflow-rules/{id}/execution-logs")
    @PreAuthorize("isAuthenticated()")
    public R<List<WorkflowRuleExecutionLogVO>> getExecutionLogs(
            @PathVariable("id") Long id,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        // 先通过 getRule 触发权限校验（getRule 内部已实现 checkRuleViewPermission）
        ruleService.getRule(id);
        return R.ok(scheduledRuleService.getExecutionLogs(id, Math.min(limit, 100)));
    }

    /**
     * 清空规则执行日志
     */
    @DeleteMapping("/workflow-rules/{id}/execution-logs")
    @PreAuthorize("isAuthenticated()")
    public R<Void> clearExecutionLogs(@PathVariable("id") Long id) {
        ruleService.getRule(id);
        scheduledRuleService.clearExecutionLogs(id);
        return R.ok();
    }

    /**
     * 获取指定工单可用的 Action Rule 命令列表（Guard 条件已通过的）
     */
    @GetMapping("/issues/{issueId}/action-rules")
    @PreAuthorize("isAuthenticated()")
    public R<List<WorkflowRuleVO>> getAvailableActionRules(@PathVariable("issueId") Long issueId) {
        var issue = com.trackflow.common.util.SecurityUtils.getCurrentUserId() != null
                ? ruleService.getIssueForActionRules(issueId) : null;
        if (issue == null) {
            return R.ok(List.of());
        }
        var rules = ruleService.getAvailableActionRules(issueId, issue.getProjectId());
        return R.ok(rules);
    }

    /**
     * 执行 Action Rule（用户触发命令）
     */
    @PostMapping("/issues/{issueId}/action-rules/{command}/execute")
    @PreAuthorize("isAuthenticated()")
    public R<Void> executeActionRule(
            @PathVariable("issueId") Long issueId,
            @PathVariable("command") String command) {
        ruleService.executeActionRule(issueId, command);
        return R.ok();
    }
}
