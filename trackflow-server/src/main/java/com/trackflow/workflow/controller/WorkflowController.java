package com.trackflow.workflow.controller;

import com.trackflow.common.model.R;
import com.trackflow.workflow.converter.WorkflowConverter;
import com.trackflow.workflow.dto.UpdateWorkflowDTO;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.service.WorkflowService;
import com.trackflow.workflow.vo.WorkflowTransitionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowConverter workflowConverter;

    /**
     * 获取工作流转换矩阵
     * projectId=0 表示全局工作流，需要系统管理员权限
     * projectId>0 表示项目级工作流，需要项目 manage_workflow 权限
     */
    @GetMapping("/projects/{projectId}/workflows")
    @PreAuthorize("#projectId == 0L ? @perm.checkGlobal('system:admin') : @perm.check(#projectId, 'project:manage_workflow')")
    public R<List<WorkflowTransitionVO>> getTransitionMatrix(
            @PathVariable Long projectId,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) Long roleId) {

        Long effectiveProjectId = (projectId == 0L) ? null : projectId;
        List<WorkflowTransition> transitions = workflowService.getTransitionMatrix(effectiveProjectId, issueType, roleId);
        return R.ok(workflowConverter.toVOList(transitions));
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
}
