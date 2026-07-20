package com.trackflow.customfield.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.customfield.converter.CustomFieldConverter;
import com.trackflow.customfield.dto.CreateCustomFieldDTO;
import com.trackflow.customfield.dto.CustomFieldQuery;
import com.trackflow.customfield.dto.ReorderCustomFieldDTO;
import com.trackflow.customfield.dto.ReorderProjectFieldsDTO;
import com.trackflow.customfield.dto.SetFieldConditionDTO;
import com.trackflow.customfield.dto.SetFieldProjectOverrideDTO;
import com.trackflow.customfield.dto.SetFieldVisibilityDTO;
import com.trackflow.customfield.dto.UpdateCustomFieldDTO;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.entity.CustomFieldProject;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.customfield.vo.AvailableColumnVO;
import com.trackflow.customfield.vo.CustomFieldDefinitionVO;
import com.trackflow.customfield.vo.CustomFieldOptionVO;
import com.trackflow.customfield.vo.CustomFieldUsageVO;
import com.trackflow.customfield.vo.OptionUsageItemVO;
import com.trackflow.customfield.vo.ProjectFieldsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
        // 批量加载关联数据（消除 N+1 查询）
        List<Long> fieldIds = result.getRecords().stream()
                .map(CustomFieldDefinition::getId).toList();
        Map<Long, List<CustomFieldOption>> optionsMap = customFieldService.getBatchOptions(fieldIds);
        Map<Long, List<Long>> projectIdsMap = customFieldService.getBatchProjectIds(fieldIds);
        Map<Long, List<String>> issueTypesMap = customFieldService.getBatchIssueTypes(fieldIds);
        for (int i = 0; i < result.getRecords().size(); i++) {
            Long fieldId = result.getRecords().get(i).getId();
            CustomFieldDefinitionVO vo = voList.get(i);
            vo.setOptions(converter.toOptionVOList(optionsMap.getOrDefault(fieldId, List.of())));
            vo.setProjectIds(projectIdsMap.getOrDefault(fieldId, List.of()).stream()
                    .map(String::valueOf).toList());
            vo.setIssueTypes(issueTypesMap.getOrDefault(fieldId, List.of()));
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
    public R<CustomFieldDefinitionVO> update(@PathVariable("id") Long id,
                                              @Valid @RequestBody UpdateCustomFieldDTO dto) {
        CustomFieldDefinition entity = customFieldService.update(id, dto);
        CustomFieldDefinitionVO vo = converter.toVO(entity);
        vo.setOptions(converter.toOptionVOList(customFieldService.getOptions(entity.getId())));
        vo.setProjectIds(customFieldService.getProjectIds(entity.getId()).stream()
                .map(String::valueOf).toList());
        vo.setIssueTypes(customFieldService.getIssueTypes(entity.getId()));
        return R.ok(vo);
    }

    @GetMapping("/admin/custom-fields/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<CustomFieldDefinitionVO> getDetail(@PathVariable("id") Long id) {
        CustomFieldDefinition entity = customFieldService.getById(id);
        CustomFieldDefinitionVO vo = converter.toVO(entity);
        vo.setOptions(converter.toOptionVOList(customFieldService.getOptions(entity.getId())));
        vo.setProjectIds(customFieldService.getProjectIds(entity.getId()).stream()
                .map(String::valueOf).toList());
        vo.setIssueTypes(customFieldService.getIssueTypes(entity.getId()));
        return R.ok(vo);
    }

    @DeleteMapping("/admin/custom-fields/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<Void> delete(@PathVariable("id") Long id,
                          @RequestParam(defaultValue = "false") boolean confirm) {
        customFieldService.delete(id, confirm);
        return R.ok();
    }

    @GetMapping("/admin/custom-fields/{id}/usage")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<CustomFieldUsageVO> getUsage(@PathVariable("id") Long id) {
        return R.ok(customFieldService.getUsage(id));
    }

    @GetMapping("/admin/custom-fields/{id}/option-usage")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<List<OptionUsageItemVO>> getOptionUsage(@PathVariable("id") Long id) {
        return R.ok(customFieldService.getOptionUsage(id));
    }

    /**
     * 获取"Fields in Projects"矩阵数据：按项目分组展示每个项目关联的自定义字段。
     * 包含全局字段和项目专属字段。
     */
    @GetMapping("/admin/custom-fields/fields-in-projects")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<List<ProjectFieldsVO>> fieldsInProjects() {
        return R.ok(customFieldService.getFieldsInProjects());
    }

    /**
     * 获取所有枚举类型字段的选项摘要（用于"从已有字段复制选项"下拉列表）
     */
    @GetMapping("/admin/custom-fields/enum-fields")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<List<CustomFieldDefinitionVO>> listEnumFields() {
        List<CustomFieldDefinition> fields = customFieldService.listEnumFields();
        List<CustomFieldDefinitionVO> voList = converter.toVOList(fields);
        // 批量加载选项
        List<Long> fieldIds = fields.stream().map(CustomFieldDefinition::getId).toList();
        Map<Long, List<CustomFieldOption>> optionsMap = customFieldService.getBatchOptions(fieldIds);
        for (int i = 0; i < fields.size(); i++) {
            Long fieldId = fields.get(i).getId();
            voList.get(i).setOptions(converter.toOptionVOList(optionsMap.getOrDefault(fieldId, List.of())));
        }
        return R.ok(voList);
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
            @PathVariable("projectId") Long projectId,
            @RequestParam(value = "issueType", required = false) String issueType) {
        List<CustomFieldDefinition> fields = customFieldService.listByProject(projectId, issueType);
        Map<Long, CustomFieldProject> conditionsMap = customFieldService.getProjectFieldConditions(projectId);

        // Role-based visibility filtering
        List<Long> userRoleIds = customFieldService.getCurrentUserRoleIds(projectId);
        fields = customFieldService.filterFieldsByVisibility(fields, conditionsMap, userRoleIds);

        List<CustomFieldDefinitionVO> voList = converter.toVOList(fields);
        // 批量加载选项数据（消除 N+1 查询）
        List<Long> fieldIds = fields.stream().map(CustomFieldDefinition::getId).toList();
        Map<Long, List<CustomFieldOption>> optionsMap = customFieldService.getBatchOptions(fieldIds);
        for (int i = 0; i < fields.size(); i++) {
            Long fieldId = fields.get(i).getId();
            CustomFieldDefinitionVO vo = voList.get(i);
            CustomFieldDefinition field = fields.get(i);
            vo.setOptions(converter.toOptionVOList(optionsMap.getOrDefault(fieldId, List.of())));
            // 填充条件信息
            CustomFieldProject mapping = conditionsMap.get(fieldId);
            if (mapping != null && mapping.getConditionFieldId() != null) {
                vo.setConditionFieldId(String.valueOf(mapping.getConditionFieldId()));
                vo.setConditionValues(customFieldService.parseJsonArray(mapping.getConditionValues()));
            }
            // 填充可编辑性标记
            if (mapping != null) {
                List<Long> updatableRoles = customFieldService.parseRoleIds(mapping.getUpdatableByRoles());
                vo.setEditable(customFieldService.isUpdatableByUser(updatableRoles, userRoleIds));
                vo.setVisibleToRoles(customFieldService.parseRoleIds(mapping.getVisibleToRoles()));
                vo.setUpdatableByRoles(updatableRoles);
            } else {
                vo.setEditable(true);
            }
            // 计算项目级有效必填性和默认值（前端用于校验和填充）
            vo.setEffectiveIsRequired(mapping != null && mapping.getIsRequired() != null
                    ? mapping.getIsRequired() : field.getIsRequired());
            String effectiveDefault = (mapping != null && mapping.getDefaultValue() != null)
                    ? (mapping.getDefaultValue().isEmpty() ? null : mapping.getDefaultValue())
                    : field.getDefaultValue();
            vo.setEffectiveDefaultValue(effectiveDefault);
        }
        return R.ok(voList);
    }

    @GetMapping("/projects/{projectId}/available-columns")
    public R<List<AvailableColumnVO>> availableColumnsByProject(@PathVariable("projectId") Long projectId) {
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
    public R<List<CustomFieldDefinitionVO>> listProjectSettingsFields(@PathVariable("projectId") Long projectId) {
        List<CustomFieldDefinition> fields = customFieldService.listProjectFields(projectId);
        List<CustomFieldDefinitionVO> voList = converter.toVOList(fields);
        Map<Long, CustomFieldProject> conditionsMap = customFieldService.getProjectFieldConditions(projectId);
        // 批量加载关联数据（消除 N+1 查询）
        List<Long> fieldIds = fields.stream().map(CustomFieldDefinition::getId).toList();
        Map<Long, List<CustomFieldOption>> optionsMap = customFieldService.getBatchOptions(fieldIds);
        Map<Long, List<Long>> projectIdsMap = customFieldService.getBatchProjectIds(fieldIds);
        Map<Long, List<String>> issueTypesMap = customFieldService.getBatchIssueTypes(fieldIds);
        for (int i = 0; i < fields.size(); i++) {
            Long fieldId = fields.get(i).getId();
            CustomFieldDefinitionVO vo = voList.get(i);
            vo.setOptions(converter.toOptionVOList(optionsMap.getOrDefault(fieldId, List.of())));
            vo.setProjectIds(projectIdsMap.getOrDefault(fieldId, List.of()).stream()
                    .map(String::valueOf).toList());
            vo.setIssueTypes(issueTypesMap.getOrDefault(fieldId, List.of()));
            // 填充条件信息
            CustomFieldProject mapping = conditionsMap.get(fieldId);
            if (mapping != null && mapping.getConditionFieldId() != null) {
                vo.setConditionFieldId(String.valueOf(mapping.getConditionFieldId()));
                vo.setConditionValues(customFieldService.parseJsonArray(mapping.getConditionValues()));
            }
            // 填充可见性/可编辑性配置
            if (mapping != null) {
                vo.setVisibleToRoles(customFieldService.parseRoleIds(mapping.getVisibleToRoles()));
                vo.setUpdatableByRoles(customFieldService.parseRoleIds(mapping.getUpdatableByRoles()));
            }
            // 填充项目级覆盖（必填性 + 默认值）
            if (mapping != null) {
                vo.setProjectIsRequired(mapping.getIsRequired());
                vo.setProjectDefaultValue(mapping.getDefaultValue());
            }
            // 计算有效值（项目覆盖 > 全局）
            CustomFieldDefinition field = fields.get(i);
            vo.setEffectiveIsRequired(mapping != null && mapping.getIsRequired() != null
                    ? mapping.getIsRequired() : field.getIsRequired());
            String effectiveDefault = (mapping != null && mapping.getDefaultValue() != null)
                    ? (mapping.getDefaultValue().isEmpty() ? null : mapping.getDefaultValue())
                    : field.getDefaultValue();
            vo.setEffectiveDefaultValue(effectiveDefault);
        }
        return R.ok(voList);
    }

    /**
     * 获取可以附加到项目的字段列表（排除全局字段和已附加字段）
     */
    @GetMapping("/projects/{projectId}/settings/custom-fields/available")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<List<CustomFieldDefinitionVO>> listAvailableForProject(@PathVariable("projectId") Long projectId) {
        List<CustomFieldDefinition> fields = customFieldService.listAvailableFieldsForProject(projectId);
        List<CustomFieldDefinitionVO> voList = converter.toVOList(fields);
        // 批量加载选项数据（消除 N+1 查询）
        List<Long> fieldIds = fields.stream().map(CustomFieldDefinition::getId).toList();
        Map<Long, List<CustomFieldOption>> optionsMap = customFieldService.getBatchOptions(fieldIds);
        for (int i = 0; i < fields.size(); i++) {
            Long fieldId = fields.get(i).getId();
            voList.get(i).setOptions(converter.toOptionVOList(optionsMap.getOrDefault(fieldId, List.of())));
        }
        return R.ok(voList);
    }

    /**
     * 附加自定义字段到项目
     */
    @PostMapping("/projects/{projectId}/settings/custom-fields/{fieldId}")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> attachField(@PathVariable("projectId") Long projectId, @PathVariable("fieldId") Long fieldId) {
        customFieldService.attachFieldToProject(projectId, fieldId);
        return R.ok();
    }

    /**
     * 从项目移除自定义字段
     */
    @DeleteMapping("/projects/{projectId}/settings/custom-fields/{fieldId}")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> detachField(@PathVariable("projectId") Long projectId, @PathVariable("fieldId") Long fieldId) {
        customFieldService.detachFieldFromProject(projectId, fieldId);
        return R.ok();
    }

    /**
     * 调整字段在项目中的显示顺序
     */
    @PutMapping("/projects/{projectId}/settings/custom-fields/reorder")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> reorderProjectFields(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ReorderProjectFieldsDTO dto) {
        customFieldService.reorderProjectFields(projectId, dto.getFieldIds());
        return R.ok();
    }

    // ========== 内联添加选项值（工单编辑时快捷添加）==========

    /**
     * 为枚举类型字段添加一个新选项值。
     * 用于工单详情页/创建表单中，有权限的用户直接在下拉中添加新值。
     * 参考 YouTrack: "Wherever you can change the value for a field, there is an option to add a new value to the set."
     */
    @PostMapping("/projects/{projectId}/custom-fields/{fieldId}/options")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<CustomFieldOptionVO> addOption(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody com.trackflow.customfield.dto.AddOptionDTO dto) {
        CustomFieldOption option = customFieldService.addOptionInline(projectId, fieldId, dto.getValue(), dto.getColor());
        return R.ok(converter.toOptionVO(option));
    }

    // ========== 条件显示配置端点 ==========

    /**
     * 设置字段的条件显示规则（项目级）
     */
    @PutMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/condition")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> setFieldCondition(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody SetFieldConditionDTO dto) {
        customFieldService.setFieldCondition(projectId, fieldId, dto.getConditionFieldId(), dto.getConditionValues());
        return R.ok();
    }

    /**
     * 清除项目中字段被条件隐藏的 issue 值
     */
    @PostMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/clear-hidden-values")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Integer> clearHiddenValues(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId) {
        int cleared = customFieldService.clearHiddenValues(projectId, fieldId);
        return R.ok(cleared);
    }

    // ========== 字段可见性/可编辑性配置端点 ==========

    /**
     * 设置字段的可见性和编辑权限（项目级，基于角色）
     */
    @PutMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/visibility")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> setFieldVisibility(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody SetFieldVisibilityDTO dto) {
        customFieldService.setFieldVisibility(projectId, fieldId, dto.getVisibleToRoles(), dto.getUpdatableByRoles());
        return R.ok();
    }

    // ========== 项目级覆盖（必填性 + 默认值）端点 ==========

    /**
     * 设置字段在项目中的必填性和默认值覆盖。
     * null 值表示继承全局设置。
     */
    @PutMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/override")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> setFieldProjectOverride(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody SetFieldProjectOverrideDTO dto) {
        customFieldService.setFieldProjectOverride(projectId, fieldId, dto.getIsRequired(), dto.getDefaultValue());
        return R.ok();
    }
}
