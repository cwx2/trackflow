package com.trackflow.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.system.vo.RoleVO;
import com.trackflow.workflow.converter.WorkflowConverter;
import com.trackflow.workflow.dto.UpdateWorkflowDTO;
import com.trackflow.workflow.dto.WorkflowActivityQuery;
import com.trackflow.workflow.entity.WorkflowActivity;
import com.trackflow.workflow.service.WorkflowService;
import com.trackflow.workflow.vo.WorkflowActivityVO;
import com.trackflow.workflow.vo.WorkflowMatrixVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    @PreAuthorize("#projectId == 0L ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<WorkflowMatrixVO> getTransitionMatrix(
            @PathVariable Long projectId,
            @RequestParam(value = "issueType", required = false) String issueType,
            @RequestParam(value = "roleId", required = false) Long roleId,
            @RequestParam(value = "author", required = false) Boolean author,
            @RequestParam(value = "assignee", required = false) Boolean assignee) {

        Long effectiveProjectId = (projectId == 0L) ? null : projectId;
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
    @PreAuthorize("#projectId == 0L ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<Void> updateTransitionMatrix(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateWorkflowDTO dto) {

        Long effectiveProjectId = (projectId == 0L) ? null : projectId;
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
    public R<List<String>> getTransitionableStatuses(@PathVariable Long projectId) {
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
    @PreAuthorize("#projectId == 0L ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<PageResult<WorkflowActivityVO>> listWorkflowActivities(
            @PathVariable Long projectId,
            WorkflowActivityQuery query) {

        query.setProjectId(projectId);
        Page<WorkflowActivity> page = workflowService.listActivities(query);
        List<WorkflowActivityVO> voList = workflowConverter.toActivityVOList(page.getRecords());
        return R.ok(new PageResult<>(voList, page.getTotal(), (int) page.getCurrent(), (int) page.getSize()));
    }
}
