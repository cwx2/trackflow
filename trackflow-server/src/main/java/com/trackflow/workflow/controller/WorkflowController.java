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
     */
    @GetMapping("/projects/{projectId}/workflows")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_workflow')")
    public R<List<WorkflowTransitionVO>> getTransitionMatrix(
            @PathVariable Long projectId,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) Long roleId) {

        List<WorkflowTransition> transitions = workflowService.getTransitionMatrix(projectId, issueType, roleId);
        return R.ok(workflowConverter.toVOList(transitions));
    }

    /**
     * 更新工作流转换矩阵
     */
    @PutMapping("/projects/{projectId}/workflows")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_workflow')")
    public R<Void> updateTransitionMatrix(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateWorkflowDTO dto) {

        List<WorkflowTransition> transitions = dto.getTransitions().stream()
                .filter(t -> Boolean.TRUE.equals(t.getAllowed()))
                .map(t -> {
                    WorkflowTransition wt = new WorkflowTransition();
                    wt.setOldStatusId(t.getFrom());
                    wt.setNewStatusId(t.getTo());
                    return wt;
                })
                .toList();

        workflowService.updateTransitionMatrix(projectId, dto.getIssueType(), dto.getRoleId(), transitions);
        return R.ok();
    }
}
