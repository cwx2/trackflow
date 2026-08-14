package com.trackflow.customfield.controller;

import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.customfield.dto.ConvertFieldTypeDTO;
import com.trackflow.customfield.dto.CreateCustomFieldDTO;
import com.trackflow.customfield.dto.CustomFieldQuery;
import com.trackflow.customfield.dto.ReplaceFieldDTO;
import com.trackflow.customfield.dto.ReorderCustomFieldDTO;
import com.trackflow.customfield.dto.ReorderProjectFieldsDTO;
import com.trackflow.customfield.dto.SetBadgeConfigDTO;
import com.trackflow.customfield.dto.SetFieldConditionDTO;
import com.trackflow.customfield.dto.SetFieldFilterRulesDTO;
import com.trackflow.customfield.dto.SetFieldProjectOverrideDTO;
import com.trackflow.customfield.dto.SetFieldVisibilityDTO;
import com.trackflow.customfield.dto.UpdateCustomFieldDTO;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.converter.CustomFieldConverter;
import com.trackflow.customfield.service.CustomFieldReplacementService;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.customfield.service.CustomFieldTypeConversionService;
import com.trackflow.customfield.vo.AvailableColumnVO;
import com.trackflow.customfield.vo.AvailableConversionsVO;
import com.trackflow.customfield.vo.ConversionResultVO;
import com.trackflow.customfield.vo.CustomFieldDefinitionVO;
import com.trackflow.customfield.vo.CustomFieldOptionVO;
import com.trackflow.customfield.vo.CustomFieldUsageVO;
import com.trackflow.customfield.vo.MergeOptionsResultVO;
import com.trackflow.customfield.vo.OptionUsageItemVO;
import com.trackflow.customfield.vo.ProjectFieldsVO;
import com.trackflow.customfield.vo.ReplaceResultVO;
import com.trackflow.customfield.vo.ReplaceableFieldsVO;
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
@PreAuthorize("isAuthenticated()")
public class CustomFieldController {

    private final CustomFieldService customFieldService;
    private final CustomFieldTypeConversionService typeConversionService;
    private final CustomFieldReplacementService replacementService;
    private final CustomFieldConverter converter;

    // ========== Admin 端点（需要 system:manage_custom_fields 权限）==========

    @GetMapping("/admin/custom-fields")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<PageResult<CustomFieldDefinitionVO>> list(CustomFieldQuery query) {
        return R.ok(customFieldService.listAdminPage(query));
    }

    @PostMapping("/admin/custom-fields")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<CustomFieldDefinitionVO> create(@Valid @RequestBody CreateCustomFieldDTO dto) {
        return R.ok(customFieldService.createAndReturnVO(dto));
    }

    @PutMapping("/admin/custom-fields/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<CustomFieldDefinitionVO> update(@PathVariable("id") Long id,
                                              @Valid @RequestBody UpdateCustomFieldDTO dto) {
        return R.ok(customFieldService.updateAndReturnVO(id, dto));
    }

    @GetMapping("/admin/custom-fields/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<CustomFieldDefinitionVO> getDetail(@PathVariable("id") Long id) {
        return R.ok(customFieldService.getFieldDetailVO(id));
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
     * 重新排序枚举字段的选项值。
     * 传入按新顺序排列的选项 ID 列表，后端更新 position。
     */
    @PutMapping("/admin/custom-fields/{id}/options/reorder")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<Void> reorderOptions(@PathVariable("id") Long id,
                                  @Valid @RequestBody com.trackflow.customfield.dto.ReorderOptionsDTO dto) {
        customFieldService.reorderOptions(id, dto.getOptionIds());
        return R.ok();
    }

    /**
     * 设置枚举字段选项的排序模式。
     * 切换到自动排序模式后，所有选项按指定规则重新排列 position。
     * 支持的模式: manual / name_asc / name_desc / name_ci_asc / name_ci_desc
     */
    @PutMapping("/admin/custom-fields/{id}/sort-mode")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<CustomFieldDefinitionVO> setSortMode(@PathVariable("id") Long id,
                                                   @Valid @RequestBody com.trackflow.customfield.dto.SetSortModeDTO dto) {
        customFieldService.setSortMode(id, dto.getSortMode());
        return R.ok(customFieldService.getFieldDetailVO(id));
    }

    /**
     * 归档或取消归档枚举字段的某个选项。
     * 归档后选项不出现在工单编辑时的下拉列表中，但已有工单的值仍保留。
     */
    @PutMapping("/admin/custom-fields/{id}/options/{optionId}/archive")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<Void> archiveOption(@PathVariable("id") Long id,
                                 @PathVariable("optionId") Long optionId,
                                 @RequestParam("archived") boolean archived) {
        customFieldService.setOptionArchived(id, optionId, archived);
        return R.ok();
    }

    /**
     * 将源字段的活跃选项合并到当前字段（Merge with）。
     * <p>
     * 参考 YouTrack "Merge with" 功能：
     * <ul>
     *   <li>只合并源字段的活跃选项（已归档的跳过）</li>
     *   <li>名称相同（不区分大小写）的选项跳过</li>
     *   <li>操作后提示合并结果</li>
     * </ul>
     */
    @PostMapping("/admin/custom-fields/{id}/merge-options")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<MergeOptionsResultVO> mergeOptions(@PathVariable("id") Long id,
                                                 @Valid @RequestBody com.trackflow.customfield.dto.MergeOptionsDTO dto) {
        int[] result = customFieldService.mergeOptionsFromField(id, dto.getSourceFieldId());
        return R.ok(new MergeOptionsResultVO(result[0], result[1], result[2]));
    }

    /**
     * 获取"Fields in Projects"矩阵数据：按项目分组展示每个项目关联的自定义字段。
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
        return R.ok(customFieldService.listEnumFieldsVO());
    }

    @PutMapping("/admin/custom-fields/reorder")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<Void> reorder(@Valid @RequestBody ReorderCustomFieldDTO dto) {
        customFieldService.reorder(dto.getIds());
        return R.ok();
    }

    /**
     * 批量更新自定义字段属性（isForAll / isHiddenInList）
     */
    @PutMapping("/admin/custom-fields/batch-update")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<Void> batchUpdate(@Valid @RequestBody com.trackflow.customfield.dto.BatchUpdateCustomFieldDTO dto) {
        customFieldService.batchUpdate(dto.getIds(), dto.getField(), dto.getValue());
        return R.ok();
    }

    /**
     * 批量删除自定义字段
     */
    @DeleteMapping("/admin/custom-fields/batch-delete")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<Void> batchDelete(@RequestParam("ids") List<Long> ids) {
        customFieldService.batchDelete(ids);
        return R.ok();
    }

    /**
     * 启用或禁用字段的 Auto-attach 功能。
     */
    @PutMapping("/admin/custom-fields/{id}/auto-attach")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<Void> setAutoAttach(@PathVariable("id") Long id,
                                  @RequestParam("enabled") boolean enabled) {
        customFieldService.setAutoAttach(id, enabled);
        return R.ok();
    }

    // ========== 项目级读取端点 ==========

    @GetMapping("/projects/{projectId}/custom-fields")
    public R<List<CustomFieldDefinitionVO>> listByProject(
            @PathVariable("projectId") Long projectId,
            @RequestParam(value = "issueType", required = false) String issueType) {
        return R.ok(customFieldService.listByProjectForUser(projectId, issueType));
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
        return R.ok(customFieldService.listProjectSettingsFieldsVO(projectId));
    }

    /**
     * 获取可以附加到项目的字段列表（排除全局字段和已附加字段）
     */
    @GetMapping("/projects/{projectId}/settings/custom-fields/available")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<List<CustomFieldDefinitionVO>> listAvailableForProject(@PathVariable("projectId") Long projectId) {
        return R.ok(customFieldService.listAvailableForProjectVO(projectId));
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
     */
    @PostMapping("/projects/{projectId}/custom-fields/{fieldId}/options")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<CustomFieldOptionVO> addOption(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody com.trackflow.customfield.dto.AddOptionDTO dto) {
        CustomFieldOption option = customFieldService.addOptionInline(projectId, fieldId, dto.getValue(), dto.getColor(), dto.getOwnerUserId(), dto.getReleaseDate(), dto.getIsReleased(), dto.getAssembleDate());
        return R.ok(converter.toOptionVO(option));
    }

    // ========== 项目级独立选项集管理（Make Independent Copy）==========

    /**
     * 获取字段在项目中的选项集状态（共享/独立）
     */
    @GetMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/option-set-status")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<com.trackflow.customfield.vo.OptionSetStatusVO> getOptionSetStatus(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId) {
        return R.ok(customFieldService.getOptionSetStatus(projectId, fieldId));
    }

    /**
     * 创建项目级独立选项副本（Make Independent Copy）
     * <p>
     * 将字段的全局共享选项集复制为项目独立副本，后续该项目可自由编辑选项而不影响其他项目。
     * 参考 YouTrack "Make independent copy" 功能。
     */
    @PostMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/make-independent")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<List<CustomFieldOptionVO>> makeIndependentCopy(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody(required = false) com.trackflow.customfield.dto.MakeIndependentCopyDTO dto) {
        boolean emptyOptions = dto != null && Boolean.TRUE.equals(dto.getEmptyOptions());
        List<CustomFieldOption> options = customFieldService.makeIndependentCopy(projectId, fieldId, emptyOptions);
        return R.ok(converter.toOptionVOList(options));
    }

    /**
     * 恢复为全局共享选项集。
     * 删除项目独立选项，恢复使用全局共享选项集。
     */
    @PostMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/revert-to-shared")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> revertToShared(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @RequestParam(defaultValue = "false") boolean confirm) {
        customFieldService.revertToShared(projectId, fieldId, confirm);
        return R.ok();
    }

    /**
     * 获取项目中字段的有效选项列表（支持独立/共享回退）
     */
    @GetMapping("/projects/{projectId}/custom-fields/{fieldId}/options")
    public R<List<CustomFieldOptionVO>> getProjectFieldOptions(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId) {
        List<CustomFieldOption> options = customFieldService.getEffectiveOptions(fieldId, projectId);
        return R.ok(converter.toOptionVOList(options));
    }

    /**
     * 更新项目独立选项集。
     * 仅当项目使用独立选项集时有效。
     */
    @PutMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/options")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<List<CustomFieldOptionVO>> updateProjectOptions(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody com.trackflow.customfield.dto.UpdateProjectOptionsDTO dto) {
        customFieldService.updateProjectOptions(projectId, fieldId, dto.getOptions());
        List<CustomFieldOption> options = customFieldService.getEffectiveOptions(fieldId, projectId);
        return R.ok(converter.toOptionVOList(options));
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

    // ========== 项目级覆盖（必填性 + 默认值 + 是否允许为空）端点 ==========

    /**
     * 设置字段在项目中的必填性、默认值和是否允许为空的覆盖。
     * <p>
     * YouTrack 风格的"无默认值但必填"模式通过 canBeEmpty=false + defaultValue=null 实现。
     */
    @PutMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/override")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> setFieldProjectOverride(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody SetFieldProjectOverrideDTO dto) {
        customFieldService.setFieldProjectOverride(projectId, fieldId, dto.getIsRequired(), dto.getDefaultValue(), dto.getCanBeEmpty());
        return R.ok();
    }

    // ========== 数字徽章配置端点 ==========

    /**
     * 设置整数字段在项目中的数字徽章显示配置。
     * <p>
     * 开启后，该字段的值将在工单列表标题左侧以数字徽章形式展示（对标 YouTrack 数字优先级效果）。
     * 仅对整数（integer）类型字段有效。
     */
    @PutMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/badge")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> setFieldBadgeConfig(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody SetBadgeConfigDTO dto) {
        customFieldService.setFieldBadgeConfig(projectId, fieldId, dto.getShowAsBadge(), dto.getBadgeColorRules());
        return R.ok();
    }

    // ========== 值过滤规则配置端点（Filter values based on）==========

    /**
     * 设置字段的值过滤规则（项目级）。
     * <p>
     * 配置后，当用户编辑工单并修改源字段的值时，本字段的下拉选项将根据规则过滤。
     * 如果已选值不在新的允许列表中，系统将自动清除并记录活动。
     */
    @PutMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/filter-rules")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<Void> setFieldFilterRules(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody SetFieldFilterRulesDTO dto) {
        customFieldService.setFieldFilterRules(projectId, fieldId, dto.getFilterFieldId(), dto.getRules());
        return R.ok();
    }

    // ========== 类型转换端点（Change Field Type）==========

    /**
     * 获取字段可用的类型转换选项。
     * <p>
     * 返回当前字段可以转换到的目标类型列表，以及是否允许转换（被看板使用的字段不允许）。
     */
    @GetMapping("/admin/custom-fields/{id}/conversions")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<AvailableConversionsVO> getAvailableConversions(@PathVariable("id") Long id) {
        return R.ok(typeConversionService.getAvailableConversions(id));
    }

    /**
     * 转换字段类型。
     * <p>
     * 将字段从当前类型转换为指定目标类型，同时批量转换所有已有值。
     * 类型转换是全局操作，影响所有使用该字段的项目。
     *
     * @param id  字段 ID
     * @param dto 包含目标类型和转换选项
     * @return 转换结果（受影响的 issue 数、转换的值数等）
     */
    @PostMapping("/admin/custom-fields/{id}/convert")
    @PreAuthorize("@perm.checkGlobal('system:manage_custom_fields')")
    public R<ConversionResultVO> convertFieldType(
            @PathVariable("id") Long id,
            @Valid @RequestBody ConvertFieldTypeDTO dto) {
        return R.ok(typeConversionService.convertFieldType(id, dto.getTargetFormat(), dto.getPeriodUnit()));
    }

    // ========== 字段替换端点（Replace Custom Field）==========

    /**
     * 获取可用于替换指定字段的字段列表。
     * <p>
     * 只返回与当前字段类型相同（list 类型还需 isMulti 一致）的其他字段。
     */
    @GetMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/replacements")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<ReplaceableFieldsVO> getAvailableReplacements(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId) {
        return R.ok(replacementService.getAvailableReplacements(projectId, fieldId));
    }

    /**
     * 用目标字段替换当前字段。
     * <p>
     * 替换操作的行为（参照 YouTrack）：
     * <ul>
     *   <li>原字段的值集合（仅 list 类型）合并到目标字段</li>
     *   <li>原字段在本项目 issue 中的值复制到目标字段</li>
     *   <li>原字段从本项目移除，目标字段附加到本项目</li>
     *   <li>其他项目的数据不受影响</li>
     * </ul>
     *
     * @param projectId 项目 ID
     * @param fieldId   要被替换的字段 ID
     * @param dto       包含目标字段 ID
     * @return 替换结果
     */
    @PostMapping("/projects/{projectId}/settings/custom-fields/{fieldId}/replace")
    @PreAuthorize("@perm.check(#projectId, 'project:manage_custom_fields')")
    public R<ReplaceResultVO> replaceField(
            @PathVariable("projectId") Long projectId,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody ReplaceFieldDTO dto) {
        return R.ok(replacementService.replaceField(projectId, fieldId, dto.getTargetFieldId()));
    }
}
