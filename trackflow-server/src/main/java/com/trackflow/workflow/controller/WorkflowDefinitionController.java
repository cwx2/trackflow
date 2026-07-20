package com.trackflow.workflow.controller;

import com.trackflow.common.model.R;
import com.trackflow.workflow.dto.CreateWorkflowDefinitionDTO;
import com.trackflow.workflow.dto.UpdateWorkflowDefinitionDTO;
import com.trackflow.workflow.dto.WorkflowAttachDTO;
import com.trackflow.workflow.entity.WorkflowDefinition;
import com.trackflow.workflow.service.WorkflowDefinitionService;
import com.trackflow.workflow.vo.WorkflowDefinitionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流定义管理 Controller
 * <p>
 * 提供工作流定义的 CRUD、克隆、附加/分离 API。
 * 仅系统管理员可管理全局工作流定义。
 * 项目级工作流绑定需要 manage_workflow 权限。
 */
@RestController
@RequestMapping("/api/v1/workflow-definitions")
@RequiredArgsConstructor
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService definitionService;

    /**
     * 获取所有工作流定义列表
     */
    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<List<WorkflowDefinitionVO>> listDefinitions() {
        return R.ok(definitionService.listDefinitions());
    }

    /**
     * 获取单个工作流定义详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<WorkflowDefinitionVO> getDefinition(@PathVariable("id") Long id) {
        return R.ok(definitionService.getDefinition(id));
    }

    /**
     * 创建工作流定义
     */
    @PostMapping
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<WorkflowDefinitionVO> createDefinition(@Valid @RequestBody CreateWorkflowDefinitionDTO dto) {
        WorkflowDefinition created = definitionService.createDefinition(dto);
        return R.ok(definitionService.getDefinition(created.getId()));
    }

    /**
     * 更新工作流定义
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<Void> updateDefinition(@PathVariable("id") Long id,
                                    @Valid @RequestBody UpdateWorkflowDefinitionDTO dto) {
        definitionService.updateDefinition(id, dto);
        return R.ok();
    }

    /**
     * 删除工作流定义
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<Void> deleteDefinition(@PathVariable("id") Long id) {
        definitionService.deleteDefinition(id);
        return R.ok();
    }

    /**
     * 克隆工作流定义（含所有转换规则）
     */
    @PostMapping("/{id}/clone")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<WorkflowDefinitionVO> cloneDefinition(@PathVariable("id") Long id,
                                                   @Valid @RequestBody CloneWorkflowDTO dto) {
        WorkflowDefinition cloned = definitionService.cloneDefinition(id, dto.getName());
        return R.ok(definitionService.getDefinition(cloned.getId()));
    }

    /**
     * 将工作流附加到项目
     */
    @PostMapping("/projects/{projectId}/attach")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_workflow')")
    public R<Void> attachToProject(@PathVariable("projectId") Long projectId,
                                   @Valid @RequestBody WorkflowAttachDTO dto) {
        definitionService.attachToProject(projectId, dto.getWorkflowDefinitionId());
        return R.ok();
    }

    /**
     * 从项目分离工作流
     */
    @DeleteMapping("/projects/{projectId}/detach")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_workflow')")
    public R<Void> detachFromProject(@PathVariable("projectId") Long projectId,
                                     @Valid @RequestBody WorkflowAttachDTO dto) {
        definitionService.detachFromProject(projectId, dto.getWorkflowDefinitionId());
        return R.ok();
    }

    /**
     * 获取项目绑定的工作流定义列表
     */
    @GetMapping("/projects/{projectId}")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_workflow')")
    public R<List<WorkflowDefinitionVO>> getProjectWorkflows(@PathVariable("projectId") Long projectId) {
        return R.ok(definitionService.getProjectWorkflows(projectId));
    }

    @Data
    public static class CloneWorkflowDTO {
        @NotBlank(message = "新名称不能为空")
        private String name;
    }
}
