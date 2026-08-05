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
import com.trackflow.workflow.entity.WorkflowInitialStatus;
import com.trackflow.workflow.WorkflowScope;
import com.trackflow.workflow.service.WorkflowService;
import com.trackflow.workflow.vo.WorkflowActivityVO;
import com.trackflow.workflow.vo.WorkflowImpactAnalysisVO;
import com.trackflow.workflow.vo.WorkflowInitialStatusVO;
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

    /**
     * 更新指定工作流转换规则的守卫条件。
     * <p>
     * 守卫条件对应 YouTrack 的 Workflow Guard Conditions 功能——
     * 定义状态转换的前置条件，仅当所有条件满足时转换才对用户可见。
     * <p>
     * 权限：与工作流编辑相同（系统管理员或拥有 project:manage_workflow 权限的用户）。
     */
    @PatchMapping("/workflows/transitions/{transitionId}/conditions")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<Void> updateTransitionConditions(
            @PathVariable("transitionId") Long transitionId,
            @Valid @RequestBody com.trackflow.workflow.dto.UpdateTransitionConditionsDTO dto) {
        workflowService.updateTransitionConditions(transitionId, dto);
        return R.ok();
    }

    /**
     * 更新转换显示名称。
     * 管理员可以为每个转换配置操作性名称（如"开始处理"），展示时优先于目标状态名。
     * 传空字符串或 null 表示清除名称（回退到目标状态名）。
     */
    @PatchMapping("/workflows/transitions/{transitionId}/name")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<Void> updateTransitionName(
            @PathVariable("transitionId") Long transitionId,
            @Valid @RequestBody com.trackflow.workflow.dto.UpdateTransitionNameDTO dto) {
        String name = dto.getTransitionName();
        if (name != null && name.isBlank()) {
            name = null;
        }
        workflowService.updateTransitionName(transitionId, name);
        return R.ok();
    }

    // ========== 初始状态管理 ==========

    /**
     * 获取指定项目的初始状态配置列表。
     * 用于工作流编辑器展示哪些状态被标记为初始状态。
     */
    @GetMapping("/projects/{projectId}/workflows/initial-statuses")
    @PreAuthorize("T(com.trackflow.workflow.WorkflowScope).isGlobal(#projectId) ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<List<WorkflowInitialStatusVO>> listInitialStatuses(@PathVariable("projectId") Long projectId) {
        Long effectiveProjectId = WorkflowScope.fromApi(projectId);
        List<WorkflowInitialStatus> configs = workflowService.listInitialStatuses(effectiveProjectId);
        List<WorkflowInitialStatusVO> voList = configs.stream().map(config -> {
            WorkflowInitialStatusVO vo = new WorkflowInitialStatusVO();
            vo.setId(String.valueOf(config.getId()));
            vo.setProjectId(config.getProjectId() != null ? String.valueOf(config.getProjectId()) : null);
            vo.setIssueType(config.getIssueType());
            vo.setStatusId(String.valueOf(config.getStatusId()));
            return vo;
        }).toList();
        return R.ok(voList);
    }

    /**
     * 设置指定（项目, 工单类型）的初始状态。
     * 同一组合只能有一个初始状态，设置新的会替换旧的。
     *
     * @param projectId  项目 ID（0 表示全局）
     * @param statusId   目标状态 ID
     * @param issueType  工单类型（默认 *）
     */
    @PutMapping("/projects/{projectId}/workflows/initial-status")
    @PreAuthorize("T(com.trackflow.workflow.WorkflowScope).isGlobal(#projectId) ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<Void> setInitialStatus(
            @PathVariable("projectId") Long projectId,
            @RequestParam("statusId") Long statusId,
            @RequestParam(value = "issueType", defaultValue = "*") String issueType) {
        Long effectiveProjectId = WorkflowScope.fromApi(projectId);
        workflowService.setInitialStatus(effectiveProjectId, issueType, statusId);
        return R.ok();
    }

    /**
     * 清除指定（项目, 工单类型）的初始状态配置，恢复为系统默认逻辑。
     */
    @DeleteMapping("/projects/{projectId}/workflows/initial-status")
    @PreAuthorize("T(com.trackflow.workflow.WorkflowScope).isGlobal(#projectId) ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<Void> clearInitialStatus(
            @PathVariable("projectId") Long projectId,
            @RequestParam(value = "issueType", defaultValue = "*") String issueType) {
        Long effectiveProjectId = WorkflowScope.fromApi(projectId);
        workflowService.clearInitialStatus(effectiveProjectId, issueType);
        return R.ok();
    }
}
