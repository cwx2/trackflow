package com.trackflow.workitemattr.controller;

import com.trackflow.common.model.R;
import com.trackflow.workitemattr.dto.CreateWorkItemAttributeDTO;
import com.trackflow.workitemattr.dto.ManageAttributeProjectsDTO;
import com.trackflow.workitemattr.dto.UpdateWorkItemAttributeDTO;
import com.trackflow.workitemattr.service.WorkItemAttributeService;
import com.trackflow.workitemattr.vo.WorkItemAttributeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作项属性管理接口
 * 管理端：CRUD + 项目分配
 * 前端消费端：按项目查询可用属性
 */
@RestController
@RequestMapping("/api/v1/work-item-attributes")
@RequiredArgsConstructor
public class WorkItemAttributeController {

    private final WorkItemAttributeService attributeService;

    /**
     * 列出所有工作项属性（管理页面用）
     */
    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<List<WorkItemAttributeVO>> list() {
        return R.ok(attributeService.listAll());
    }

    /**
     * 获取单个属性详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<WorkItemAttributeVO> getById(@PathVariable("id") Long id) {
        return R.ok(attributeService.getById(id));
    }

    /**
     * 创建工作项属性
     */
    @PostMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<WorkItemAttributeVO> create(@Valid @RequestBody CreateWorkItemAttributeDTO dto) {
        return R.ok(attributeService.create(dto));
    }

    /**
     * 更新工作项属性（名称和/或值列表）
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<WorkItemAttributeVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateWorkItemAttributeDTO dto) {
        return R.ok(attributeService.update(id, dto));
    }

    /**
     * 删除工作项属性
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<Void> delete(@PathVariable("id") Long id) {
        attributeService.delete(id);
        return R.ok();
    }

    /**
     * 管理属性的项目分配
     */
    @PutMapping("/{id}/projects")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<WorkItemAttributeVO> manageProjects(@PathVariable("id") Long id, @Valid @RequestBody ManageAttributeProjectsDTO dto) {
        return R.ok(attributeService.manageProjects(id, dto));
    }

    /**
     * 获取指定项目可用的工作项属性（前端工时弹窗用）
     */
    @GetMapping("/by-project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public R<List<WorkItemAttributeVO>> listByProject(@PathVariable("projectId") Long projectId) {
        return R.ok(attributeService.listByProject(projectId));
    }

    /**
     * 获取属性使用统计（删除前预检）
     */
    @GetMapping("/{id}/usage")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<Integer> getUsage(@PathVariable("id") Long id) {
        return R.ok(attributeService.getUsageCount(id));
    }
}
