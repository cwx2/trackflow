package com.trackflow.issuetemplate.controller;

import com.trackflow.common.model.R;
import com.trackflow.issuetemplate.dto.SaveIssueTemplateDTO;
import com.trackflow.issuetemplate.service.IssueTemplateService;
import com.trackflow.issuetemplate.vo.IssueTemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单模板 Controller
 * <p>
 * 权限说明：
 * - 列表/详情：项目成员可见（project:view）
 * - 创建/修改/删除：需要 project:manage_templates 权限
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/issue-templates")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class IssueTemplateController {

    private final IssueTemplateService templateService;

    /**
     * 获取项目下所有工单模板
     */
    @GetMapping
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<List<IssueTemplateVO>> list(@PathVariable("projectId") Long projectId) {
        return R.ok(templateService.listByProject(projectId));
    }

    /**
     * 获取单个模板详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<IssueTemplateVO> getById(@PathVariable("projectId") Long projectId, @PathVariable("id") Long id) {
        return R.ok(templateService.getById(id));
    }

    /**
     * 创建工单模板
     */
    @PostMapping
    @PreAuthorize("@perm.check(#projectId, 'project:manage_templates')")
    public R<IssueTemplateVO> create(@PathVariable("projectId") Long projectId, @Valid @RequestBody SaveIssueTemplateDTO dto) {
        dto.setProjectId(projectId);
        return R.ok(templateService.create(dto));
    }

    /**
     * 更新工单模板
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_templates')")
    public R<IssueTemplateVO> update(@PathVariable("projectId") Long projectId, @PathVariable("id") Long id, @Valid @RequestBody SaveIssueTemplateDTO dto) {
        dto.setProjectId(projectId);
        return R.ok(templateService.update(id, dto));
    }

    /**
     * 删除工单模板（系统预置模板不可删除）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_templates')")
    public R<Void> delete(@PathVariable("projectId") Long projectId, @PathVariable("id") Long id) {
        templateService.delete(id);
        return R.ok();
    }
}
