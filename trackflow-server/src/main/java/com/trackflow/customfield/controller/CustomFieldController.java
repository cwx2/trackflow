package com.trackflow.customfield.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.customfield.converter.CustomFieldConverter;
import com.trackflow.customfield.dto.CreateCustomFieldDTO;
import com.trackflow.customfield.dto.CustomFieldQuery;
import com.trackflow.customfield.dto.ReorderCustomFieldDTO;
import com.trackflow.customfield.dto.ReorderProjectFieldsDTO;
import com.trackflow.customfield.dto.UpdateCustomFieldDTO;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.customfield.vo.AvailableColumnVO;
import com.trackflow.customfield.vo.CustomFieldDefinitionVO;
import com.trackflow.customfield.vo.CustomFieldUsageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自定义字段控制器
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CustomFieldController {

    private final CustomFieldService customFieldService;
    private final CustomFieldConverter converter;

    // ========== Admin 端点（需要 system:manage_custom_fields 权限）==========

    @GetMapping("/admin/custom-fields")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<PageResult<CustomFieldDefinitionVO>> list(CustomFieldQuery query) {
        Page<CustomFieldDefinition> result = customFieldService.list(
                query.toPage(), query.getFieldFormat(), query.getKeyword());
        List<CustomFieldDefinitionVO> voList = converter.toVOList(result.getRecords());
        // 填充关联数据
        for (int i = 0; i < result.getRecords().size(); i++) {
            CustomFieldDefinition entity = result.getRecords().get(i);
            CustomFieldDefinitionVO vo = voList.get(i);
            vo.setOptions(converter.toOptionVOList(customFieldService.getOptions(entity.getId())));
            vo.setProjectIds(customFieldService.getProjectIds(entity.getId()).stream()
                    .map(String::valueOf).toList());
            vo.setIssueTypes(customFieldService.getIssueTypes(entity.getId()));
        }
        return R.ok(new PageResult<>(voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize()));
    }

    @PostMapping("/admin/custom-fields")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<CustomFieldDefinitionVO> create(@Valid @RequestBody CreateCustomFieldDTO dto) {
        CustomFieldDefinition entity = customFieldService.create(dto);
        CustomFieldDefinitionVO vo = converter.toVO(entity);
        vo.setOptions(converter.toOptionVOList(customFieldService.getOptions(entity.getId())));
        vo.setProjectIds(customFieldService.getProjectIds(entity.getId()).stream()
                .map(String::valueOf).toList());
        vo.setIssueTypes(customFieldService.getIssueTypes(entity.getId()));
        return R.ok(vo);
    }

    @PutMapping("/admin/custom-fields/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<CustomFieldDefinitionVO> update(@PathVariable Long id,
                                              @Valid @RequestBody UpdateCustomFieldDTO dto) {
        CustomFieldDefinition entity = customFieldService.update(id, dto);
        CustomFieldDefinitionVO vo = converter.toVO(entity);
        vo.setOptions(converter.toOptionVOList(customFieldService.getOptions(entity.getId())));
        vo.setProjectIds(customFieldService.getProjectIds(entity.getId()).stream()
                .map(String::valueOf).toList());
        vo.setIssueTypes(customFieldService.getIssueTypes(entity.getId()));
        return R.ok(vo);
    }

    @DeleteMapping("/admin/custom-fields/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<Void> delete(@PathVariable Long id,
                          @RequestParam(defaultValue = "false") boolean confirm) {
        customFieldService.delete(id, confirm);
        return R.ok();
    }

    @GetMapping("/admin/custom-fields/{id}/usage")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<CustomFieldUsageVO> getUsage(@PathVariable Long id) {
        return R.ok(customFieldService.getUsage(id));
    }

    @PutMapping("/admin/custom-fields/reorder")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<Void> reorder(@Valid @RequestBody ReorderCustomFieldDTO dto) {
        customFieldService.reorder(dto.getIds());
        return R.ok();
    }

    // ========== 项目级读取端点 ==========

    @GetMapping("/projects/{projectId}/custom-fields")
    public R<List<CustomFieldDefinitionVO>> listByProject(
            @PathVariable Long projectId,
            @RequestParam(value = "issueType", required = false) String issueType) {
        List<CustomFieldDefinition> fields = customFieldService.listByProject(projectId, issueType);
        List<CustomFieldDefinitionVO> voList = converter.toVOList(fields);
        for (int i = 0; i < fields.size(); i++) {
            voList.get(i).setOptions(converter.toOptionVOList(
                    customFieldService.getOptions(fields.get(i).getId())));
        }
        return R.ok(voList);
    }

    @GetMapping("/projects/{projectId}/available-columns")
    public R<List<AvailableColumnVO>> availableColumnsByProject(@PathVariable Long projectId) {
        return R.ok(customFieldService.getAvailableColumns(projectId));
    }

    @GetMapping("/available-columns")
    public R<List<AvailableColumnVO>> availableColumns() {
        return R.ok(customFieldService.getAllAvailableColumns());
    }

    // ========== 项目级自定义字段管理端点 ==========

    /**
     * 获取项目已附加的自定义字段（含全局字段），用于项目设置页面展示
     */
    @GetMapping("/projects/{projectId}/settings/custom-fields")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<List<CustomFieldDefinitionVO>> listProjectSettingsFields(@PathVariable Long projectId) {
        List<CustomFieldDefinition> fields = customFieldService.listProjectFields(projectId);
        List<CustomFieldDefinitionVO> voList = converter.toVOList(fields);
        for (int i = 0; i < fields.size(); i++) {
            CustomFieldDefinition entity = fields.get(i);
            CustomFieldDefinitionVO vo = voList.get(i);
            vo.setOptions(converter.toOptionVOList(customFieldService.getOptions(entity.getId())));
            vo.setProjectIds(customFieldService.getProjectIds(entity.getId()).stream()
                    .map(String::valueOf).toList());
            vo.setIssueTypes(customFieldService.getIssueTypes(entity.getId()));
        }
        return R.ok(voList);
    }

    /**
     * 获取可以附加到项目的字段列表（排除全局字段和已附加字段）
     */
    @GetMapping("/projects/{projectId}/settings/custom-fields/available")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<List<CustomFieldDefinitionVO>> listAvailableForProject(@PathVariable Long projectId) {
        List<CustomFieldDefinition> fields = customFieldService.listAvailableFieldsForProject(projectId);
        List<CustomFieldDefinitionVO> voList = converter.toVOList(fields);
        for (int i = 0; i < fields.size(); i++) {
            voList.get(i).setOptions(converter.toOptionVOList(
                    customFieldService.getOptions(fields.get(i).getId())));
        }
        return R.ok(voList);
    }

    /**
     * 附加自定义字段到项目
     */
    @PostMapping("/projects/{projectId}/settings/custom-fields/{fieldId}")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> attachField(@PathVariable Long projectId, @PathVariable Long fieldId) {
        customFieldService.attachFieldToProject(projectId, fieldId);
        return R.ok();
    }

    /**
     * 从项目移除自定义字段
     */
    @DeleteMapping("/projects/{projectId}/settings/custom-fields/{fieldId}")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> detachField(@PathVariable Long projectId, @PathVariable Long fieldId) {
        customFieldService.detachFieldFromProject(projectId, fieldId);
        return R.ok();
    }

    /**
     * 调整字段在项目中的显示顺序
     */
    @PutMapping("/projects/{projectId}/settings/custom-fields/reorder")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> reorderProjectFields(
            @PathVariable Long projectId,
            @Valid @RequestBody ReorderProjectFieldsDTO dto) {
        customFieldService.reorderProjectFields(projectId, dto.getFieldIds());
        return R.ok();
    }
}
