package com.trackflow.automation.controller;

import com.trackflow.automation.converter.AutomationWorkflowConverter;
import com.trackflow.automation.converter.AutomationWorkflowTemplateConverter;
import com.trackflow.automation.dto.CreateTemplateFromWorkflowDTO;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.entity.AutomationWorkflowTemplate;
import com.trackflow.automation.service.AutomationWorkflowTemplateService;
import com.trackflow.automation.vo.WorkflowDetailVO;
import com.trackflow.automation.vo.WorkflowTemplateVO;
import com.trackflow.common.model.R;
import jakarta.validation.Valid;
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
     * 列出当前用户可见的模板：
     * - 内置模板对所有登录用户可见
     * - 自定义模板仅创建者可见
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<List<WorkflowTemplateVO>> listTemplates() {
        List<AutomationWorkflowTemplate> templates = templateService.listTemplates();
        return R.ok(templateConverter.toVOList(templates));
    }

    /**
     * 将已有工作流保存为自定义模板
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<WorkflowTemplateVO> saveAsTemplate(@Valid @RequestBody CreateTemplateFromWorkflowDTO dto) {
        AutomationWorkflowTemplate template = templateService.saveAsTemplate(dto);
        return R.ok(templateConverter.toVO(template));
    }

    /**
     * 从模板克隆一条工作流（返回新工作流详情，状态=draft）
     */
    @PostMapping("/{id}/clone")
    @PreAuthorize("isAuthenticated()")
    public R<WorkflowDetailVO> cloneFromTemplate(@PathVariable("id") Long id) {
        AutomationWorkflow workflow = templateService.cloneFromTemplate(id);
        return R.ok(workflowConverter.toDetailVO(workflow));
    }

    /**
     * 删除模板（内置模板返回 403，自定义模板只有创建者可删除）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> deleteTemplate(@PathVariable("id") Long id) {
        templateService.deleteTemplate(id);
        return R.ok();
    }
}
