package com.trackflow.automation.controller;

import com.trackflow.automation.converter.AutomationWorkflowConverter;
import com.trackflow.automation.converter.AutomationWorkflowTemplateConverter;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.entity.AutomationWorkflowTemplate;
import com.trackflow.automation.service.AutomationWorkflowTemplateService;
import com.trackflow.automation.vo.WorkflowDetailVO;
import com.trackflow.automation.vo.WorkflowTemplateVO;
import com.trackflow.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自动化工作流模板 API
 */
@RestController
@RequestMapping("/api/v1/automation/templates")
@RequiredArgsConstructor
public class AutomationWorkflowTemplateController {

    private final AutomationWorkflowTemplateService templateService;
    private final AutomationWorkflowTemplateConverter templateConverter;
    private final AutomationWorkflowConverter workflowConverter;

    /**
     * 列出所有模板
     */
    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<List<WorkflowTemplateVO>> listTemplates() {
        List<AutomationWorkflowTemplate> templates = templateService.listTemplates();
        return R.ok(templateConverter.toVOList(templates));
    }

    /**
     * 从模板克隆一条工作流（返回新工作流详情，状态=draft）
     */
    @PostMapping("/{id}/clone")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<WorkflowDetailVO> cloneFromTemplate(@PathVariable("id") Long id) {
        AutomationWorkflow workflow = templateService.cloneFromTemplate(id);
        return R.ok(workflowConverter.toDetailVO(workflow));
    }

    /**
     * 删除模板（内置模板返回 403）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<Void> deleteTemplate(@PathVariable("id") Long id) {
        templateService.deleteTemplate(id);
        return R.ok();
    }
}
