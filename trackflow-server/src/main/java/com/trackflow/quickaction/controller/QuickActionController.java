package com.trackflow.quickaction.controller;

import com.trackflow.common.model.R;
import com.trackflow.quickaction.dto.ExecuteQuickActionDTO;
import com.trackflow.quickaction.dto.SaveMailTemplateDTO;
import com.trackflow.quickaction.dto.SaveQuickActionDefinitionDTO;
import com.trackflow.quickaction.service.QuickActionService;
import com.trackflow.quickaction.vo.MailTemplateVO;
import com.trackflow.quickaction.vo.QuickActionDefinitionVO;
import com.trackflow.quickaction.vo.QuickActionExecutionResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 快捷动作 API
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QuickActionController {

    private final QuickActionService quickActionService;

    // ==================== 用户端 API ====================

    /**
     * 获取当前 Issue 可用的快捷动作列表
     */
    @GetMapping("/issues/{issueId}/quick-actions")
    @PreAuthorize("isAuthenticated()")
    public R<List<QuickActionDefinitionVO>> getAvailableActions(@PathVariable Long issueId) {
        List<QuickActionDefinitionVO> actions = quickActionService.getAvailableActions(issueId);
        return R.ok(actions);
    }

    /**
     * 执行快捷动作
     */
    @PostMapping("/issues/{issueId}/quick-actions/{actionKey}/execute")
    @PreAuthorize("isAuthenticated()")
    public R<QuickActionExecutionResultVO> execute(
            @PathVariable Long issueId,
            @PathVariable String actionKey,
            @Valid @RequestBody ExecuteQuickActionDTO dto) {
        QuickActionExecutionResultVO result = quickActionService.execute(issueId, actionKey, dto);
        return R.ok(result);
    }

    /**
     * 获取指定动作的邮件模板列表
     */
    @GetMapping("/issues/{issueId}/quick-actions/{actionKey}/mail-templates")
    @PreAuthorize("isAuthenticated()")
    public R<List<MailTemplateVO>> getMailTemplates(
            @PathVariable Long issueId,
            @PathVariable String actionKey,
            @RequestParam(required = false) Long projectId) {
        List<MailTemplateVO> templates = quickActionService.getMailTemplates(actionKey, projectId);
        return R.ok(templates);
    }

    // ==================== 管理端 API ====================

    /**
     * 获取所有动作定义（管理）
     */
    @GetMapping("/quick-actions/definitions")
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<List<QuickActionDefinitionVO>> listDefinitions(
            @RequestParam(required = false) Long projectId) {
        List<QuickActionDefinitionVO> definitions = quickActionService.listDefinitions(projectId);
        return R.ok(definitions);
    }

    /**
     * 创建动作定义（管理）
     */
    @PostMapping("/quick-actions/definitions")
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<QuickActionDefinitionVO> createDefinition(
            @Valid @RequestBody SaveQuickActionDefinitionDTO dto) {
        QuickActionDefinitionVO vo = quickActionService.createDefinition(dto);
        return R.ok(vo);
    }

    /**
     * 更新动作定义（管理）
     */
    @PutMapping("/quick-actions/definitions/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<QuickActionDefinitionVO> updateDefinition(
            @PathVariable Long id,
            @Valid @RequestBody SaveQuickActionDefinitionDTO dto) {
        QuickActionDefinitionVO vo = quickActionService.updateDefinition(id, dto);
        return R.ok(vo);
    }

    /**
     * 删除动作定义（管理）
     */
    @DeleteMapping("/quick-actions/definitions/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<Void> deleteDefinition(@PathVariable Long id) {
        quickActionService.deleteDefinition(id);
        return R.ok();
    }

    /**
     * 获取所有邮件模板（管理）
     */
    @GetMapping("/mail-templates")
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<List<MailTemplateVO>> listMailTemplates(
            @RequestParam(required = false) Long projectId) {
        List<MailTemplateVO> templates = quickActionService.listAllMailTemplates(projectId);
        return R.ok(templates);
    }

    /**
     * 创建邮件模板（管理）
     */
    @PostMapping("/mail-templates")
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<MailTemplateVO> createMailTemplate(
            @Valid @RequestBody SaveMailTemplateDTO dto) {
        MailTemplateVO vo = quickActionService.createMailTemplate(dto);
        return R.ok(vo);
    }

    /**
     * 更新邮件模板（管理）
     */
    @PutMapping("/mail-templates/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<MailTemplateVO> updateMailTemplate(
            @PathVariable Long id,
            @Valid @RequestBody SaveMailTemplateDTO dto) {
        MailTemplateVO vo = quickActionService.updateMailTemplate(id, dto);
        return R.ok(vo);
    }

    /**
     * 删除邮件模板（管理）
     */
    @DeleteMapping("/mail-templates/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_settings')")
    public R<Void> deleteMailTemplate(@PathVariable Long id) {
        quickActionService.deleteMailTemplate(id);
        return R.ok();
    }
}
