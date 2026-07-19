package com.trackflow.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.system.vo.RoleVO;
import com.trackflow.workflow.converter.WorkflowConverter;
import com.trackflow.workflow.dto.UpdateWorkflowDTO;
import com.trackflow.workflow.dto.WorkflowActivityQuery;
import com.trackflow.workflow.dto.WorkflowImpactAnalysisDTO;
import com.trackflow.workflow.entity.WorkflowActivity;
import com.trackflow.workflow.WorkflowScope;
import com.trackflow.workflow.service.WorkflowService;
import com.trackflow.workflow.vo.WorkflowActivityVO;
import com.trackflow.workflow.vo.WorkflowImpactAnalysisVO;
import com.trackflow.workflow.vo.WorkflowMatrixVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowConverter workflowConverter;

    /**
     * 获取工作流转换矩阵（含版本号，用于乐观锁）
     * projectId=0 表示全局工作流，需要系统管理员权限
     * projectId>0 表示项目级工作流，需要项目 manage_workflow 权限
     *
     * @param author   筛选 author 模式：true=仅Author规则, false=仅Normal规则(该维度), null=不筛选
     * @param assignee 筛选 assignee 模式：true=仅Assignee规则, false=仅Normal规则(该维度), null=不筛选
     */
    @GetMapping("/projects/{projectId}/workflows")
    @PreAuthorize("T(com.trackflow.workflow.WorkflowScope).isGlobal(#projectId) ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<WorkflowMatrixVO> getTransitionMatrix(
            @PathVariable("projectId") Long projectId,
            @RequestParam(value = "issueType", required = false) String issueType,
            @RequestParam(value = "roleId", required = false) Long roleId,
            @RequestParam(value = "author", required = false) Boolean author,
            @RequestParam(value = "assignee", required = false) Boolean assignee) {

        Long effectiveProjectId = WorkflowScope.fromApi(projectId);
        WorkflowMatrixVO matrix = workflowService.getTransitionMatrixWithVersion(
                effectiveProjectId, issueType, roleId, author, assignee);
        return R.ok(matrix);
    }

    /**
     * 更新工作流转换矩阵
     * projectId=0 表示全局工作流，需要系统管理员权限
     * projectId>0 表示项目级工作流，需要项目 manage_workflow 权限
     */
    @PutMapping("/projects/{projectId}/workflows")
    @PreAuthorize("T(com.trackflow.workflow.WorkflowScope).isGlobal(#projectId) ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<Void> updateTransitionMatrix(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody UpdateWorkflowDTO dto) {

        Long effectiveProjectId = WorkflowScope.fromApi(projectId);
        workflowService.updateTransitionMatrix(effectiveProjectId, dto);
        return R.ok();
    }

    /**
     * 获取项目级角色列表（用于工作流编辑器筛选下拉）
     * 只返回 roleType=project 的角色，任何已登录用户可访问
     */
    @GetMapping("/workflows/project-roles")
    @PreAuthorize("isAuthenticated()")
    public R<List<RoleVO>> listProjectRoles() {
        return R.ok(workflowService.listProjectRoles());
    }

    /**
     * 获取系统中已使用的工单类型列表
     * 返回所有已在工单中使用过的 issueType 值
     */
    @GetMapping("/workflows/issue-types")
    @PreAuthorize("isAuthenticated()")
    public R<List<String>> listIssueTypes() {
        return R.ok(workflowService.listIssueTypes());
    }

    /**
     * 获取当前用户在指定项目中可以发起状态转换的源状态 ID 列表。
     * 用于看板预判哪些卡片可拖拽（工作流规则维度）。
     */
    @GetMapping("/projects/{projectId}/workflows/transitionable-statuses")
    @PreAuthorize("@perm.check(#projectId, 'issue:change_status')")
    public R<List<String>> getTransitionableStatuses(@PathVariable("projectId") Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Set<Long> statusIds = workflowService.getTransitionableSourceStatuses(projectId, userId);
        List<String> result = statusIds.stream().map(String::valueOf).toList();
        return R.ok(result);
    }

    /**
     * 获取工作流变更历史（审计日志）
     * projectId=0 表示全局工作流的变更历史
     * projectId>0 表示项目级工作流的变更历史
     * 权限：与工作流编辑相同
     */
    @GetMapping("/projects/{projectId}/workflow-activities")
    @PreAuthorize("T(com.trackflow.workflow.WorkflowScope).isGlobal(#projectId) ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<PageResult<WorkflowActivityVO>> listWorkflowActivities(
            @PathVariable("projectId") Long projectId,
            WorkflowActivityQuery query) {

        query.setProjectId(projectId);
        Page<WorkflowActivity> page = workflowService.listActivities(query);
        List<WorkflowActivityVO> voList = workflowConverter.toActivityVOList(page.getRecords());
        return R.ok(new PageResult<>(voList, page.getTotal(), (int) page.getCurrent(), (int) page.getSize()));
    }

    /**
     * 工作流影响分析：统计被删除转换的源状态下有多少工单。
     * 用于保存确认对话框展示"爆炸半径"。
     * 权限：任意系统管理员或拥有 manage_workflow 权限的用户。
     */
    @PostMapping("/workflows/impact-analysis")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<WorkflowImpactAnalysisVO> analyzeImpact(@Valid @RequestBody WorkflowImpactAnalysisDTO dto) {
        Long effectiveProjectId = (dto.getProjectId() != null && dto.getProjectId() > 0)
                ? dto.getProjectId() : null;
        String effectiveIssueType = (dto.getIssueType() != null && !"*".equals(dto.getIssueType()))
                ? dto.getIssueType() : null;

        Map<Long, Long> counts = workflowService.getIssueCountByStatuses(
                dto.getStatusIds(), effectiveProjectId, effectiveIssueType);

        // 转换为 String key（前端 ID 为 String）
        Map<String, Long> stringCounts = new java.util.LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<Long, Long> entry : counts.entrySet()) {
            stringCounts.put(String.valueOf(entry.getKey()), entry.getValue());
            total += entry.getValue();
        }

        return R.ok(new WorkflowImpactAnalysisVO(stringCounts, total));
    }
}
