package com.trackflow.customfield.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.customfield.converter.CustomFieldConverter;
import com.trackflow.customfield.dto.CreateCustomFieldDTO;
import com.trackflow.customfield.dto.CustomFieldQuery;
import com.trackflow.customfield.dto.ReorderCustomFieldDTO;
import com.trackflow.customfield.dto.UpdateCustomFieldDTO;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.customfield.vo.AvailableColumnVO;
import com.trackflow.customfield.vo.CustomFieldDefinitionVO;
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
    public R<Void> delete(@PathVariable Long id) {
        customFieldService.delete(id);
        return R.ok();
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
            @RequestParam(required = false) String issueType) {
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
}
