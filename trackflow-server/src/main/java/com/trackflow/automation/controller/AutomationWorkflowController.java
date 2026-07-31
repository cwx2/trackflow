package com.trackflow.automation.controller;

import com.trackflow.automation.converter.WorkflowConverter;
import com.trackflow.automation.dto.CreateWorkflowDTO;
import com.trackflow.automation.dto.UpdateWorkflowDTO;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.service.AutomationWorkflowService;
import com.trackflow.automation.vo.WorkflowDetailVO;
import com.trackflow.automation.vo.WorkflowVO;
import com.trackflow.common.model.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自动化工作流 API
 */
@RestController
@RequestMapping("/api/v1/automation/workflows")
@RequiredArgsConstructor
public class AutomationWorkflowController {

    private final AutomationWorkflowService workflowService;
    private final WorkflowConverter workflowConverter;

    /**
     * 获取工作流列表
     */
    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<List<WorkflowVO>> list() {
        List<AutomationWorkflow> workflows = workflowService.listWorkflows();
        return R.ok(workflowConverter.toVOList(workflows));
    }

    /**
     * 获取工作流详情（含 definition）
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<WorkflowDetailVO> getById(@PathVariable("id") Long id) {
        AutomationWorkflow workflow = workflowService.getById(id);
        return R.ok(workflowConverter.toDetailVO(workflow));
    }

    /**
     * 创建工作流
     */
    @PostMapping
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<WorkflowDetailVO> create(@Valid @RequestBody CreateWorkflowDTO dto) {
        AutomationWorkflow workflow = workflowService.create(dto);
        return R.ok(workflowConverter.toDetailVO(workflow));
    }

    /**
     * 更新工作流（包括保存画布）
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<WorkflowDetailVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateWorkflowDTO dto) {
        AutomationWorkflow workflow = workflowService.update(id, dto);
        return R.ok(workflowConverter.toDetailVO(workflow));
    }

    /**
     * 删除工作流
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<Void> delete(@PathVariable("id") Long id) {
        workflowService.delete(id);
        return R.ok();
    }
}
