package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.annotation.AuditLog;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.util.EntityUtils;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.customfield.converter.CustomFieldConverter;
import com.trackflow.customfield.dto.CreateCustomFieldDTO;
import com.trackflow.customfield.dto.CustomFieldQuery;
import com.trackflow.customfield.dto.UpdateCustomFieldDTO;
import com.trackflow.customfield.entity.*;
import com.trackflow.customfield.mapper.*;
import com.trackflow.customfield.vo.AvailableColumnVO;
import com.trackflow.customfield.vo.CustomFieldDefinitionVO;
import com.trackflow.customfield.vo.CustomFieldOptionVO;
import com.trackflow.customfield.vo.CustomFieldUsageVO;
import com.trackflow.customfield.vo.CustomFieldValueVO;
import com.trackflow.customfield.vo.OptionUsageItemVO;
import com.trackflow.customfield.vo.ProjectFieldsVO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectStatus;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义字段管理服务 — 全局定义 CRUD、项目级配置、列管理、VO 组装
 *
 * <p>复杂的子领域逻辑委托给：
 * <ul>
 *   <li>{@link CustomFieldOptionService} — 选项集管理</li>
 *   <li>{@link CustomFieldValueService} — EAV 值存储与校验</li>
 *   <li>{@link CustomFieldDisplayService} — 展示值解析</li>
 * </ul>
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldService {

    /**
     * 内置字段 ID 集合 — 这些字段由系统迁移脚本创建，不可删除也不可修改类型。
     * <ul>
     *   <li>1000000000000000001 = Priority (V249)</li>
     *   <li>1000000000000000002 = Type (V251)</li>
     *   <li>1000000000000000003 = Due Date (V252)</li>
     *   <li>1000000000000000004 = State (V253)</li>
     *   <li>1000000000000000005 = Fix versions (V256)</li>
     *   <li>1000000000000000006 = Affected versions (V256)</li>
     * </ul>
     */
    public static final java.util.Set<Long> BUILTIN_FIELD_IDS = java.util.Set.of(
            1000000000000000001L,
            1000000000000000002L,
            1000000000000000003L,
            1000000000000000004L,
            1000000000000000005L,
            1000000000000000006L
    );

    /** State 内置字段 ID，用于特殊处理逻辑 */
    public static final Long STATE_FIELD_ID = 1000000000000000004L;

    private final CustomFieldDefinitionMapper definitionMapper;
    private final CustomFieldOptionMapper optionMapper;
    private final CustomFieldValueMapper valueMapper;
    private final CustomFieldProjectMapper projectMapper;
    private final CustomFieldIssueTypeMapper issueTypeMapper;
    private final CustomFieldValidationEngine validationEngine;
    private final IssueMapper issueMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMapper projectEntityMapper;
    private final PermissionService permissionService;
    private final CustomFieldConverter converter;

    // 委托的子服务
    private final CustomFieldOptionService optionService;
    private final CustomFieldValueService valueService;
    private final CustomFieldDisplayService displayService;
    private final SysUserMapper sysUserMapper;
    private final com.trackflow.customfield.handler.CustomFieldHandlerRegistry handlerRegistry;

    // ========== 全局字段定义 CRUD ==========

    /**
     * 与内置字段语义重叠的保留名称（不区分大小写）。
     * 用户不得创建与这些名称相同的自定义字段，以避免 UI/数据层重复（REQ-387）。
     */
    private static final java.util.Set<String> RESERVED_FIELD_NAMES = java.util.Set.of(
            "state", "priority", "type", "due date", "fix versions", "affected versions"
    );

    @Transactional(rollbackFor = Exception.class)
    public CustomFieldDefinition create(CreateCustomFieldDTO dto) {
        if (!handlerRegistry.getSupportedFormats().contains(dto.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的字段类型: " + dto.getFieldFormat());
        }

        // 拒绝与内置字段同名的自定义字段创建（REQ-387）
        if (RESERVED_FIELD_NAMES.contains(dto.getName().trim().toLowerCase())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "字段名称「" + dto.getName() + "」与系统内置字段冲突，不允许重复创建");
        }

        boolean exists = definitionMapper.exists(new LambdaQueryWrapper<CustomFieldDefinition>()
                .apply("LOWER(name) = LOWER({0})", dto.getName()));
        if (exists) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "自定义字段名称已存在");
        }

        Long count = definitionMapper.selectCount(null);
        int position = count != null ? count.intValue() : 0;

        CustomFieldDefinition entity = new CustomFieldDefinition();
        entity.setName(dto.getName());
        entity.setFieldFormat(dto.getFieldFormat());
        entity.setIsRequired(dto.getIsRequired());
        entity.setIsForAll(dto.getIsForAll());
        entity.setDefaultValue(dto.getDefaultValue());
        entity.setMinLength(dto.getMinLength());
        entity.setMaxLength(dto.getMaxLength());
        entity.setRegexp(dto.getRegexp());
        entity.setPosition(position);
        entity.setIsMulti(("list".equals(dto.getFieldFormat()) || "ownedField".equals(dto.getFieldFormat()) || "version".equals(dto.getFieldFormat())) && Boolean.TRUE.equals(dto.getIsMulti()));
        entity.setIsHiddenInList(Boolean.TRUE.equals(dto.getIsHiddenInList()));
        entity.setAliases(dto.getAliases());
        entity.setIsPrivate(Boolean.TRUE.equals(dto.getIsPrivate()));
        try {
            definitionMapper.insert(entity);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("uk_custom_field_name")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "自定义字段名称已存在");
            }
            throw ex;
        }

        if (CustomFieldOptionService.isEnumLikeFormat(dto.getFieldFormat())) {
            if (dto.getCopyOptionsFromFieldId() != null && (dto.getOptions() == null || dto.getOptions().isEmpty())) {
                optionService.copyOptionsFromField(entity.getId(), dto.getCopyOptionsFromFieldId());
            } else if (dto.getOptions() != null) {
                for (int i = 0; i < dto.getOptions().size(); i++) {
                    CreateCustomFieldDTO.OptionItem opt = dto.getOptions().get(i);
                    CustomFieldOption option = new CustomFieldOption();
                    option.setCustomFieldId(entity.getId());
                    option.setValue(opt.getValue());
                    option.setPosition(i);
                    option.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
                    option.setColor(opt.getColor());
                    option.setDescription(opt.getDescription());
                    option.setIsResolved(Boolean.TRUE.equals(opt.getIsResolved()));
                    option.setOwnerUserId(opt.getOwnerUserId());
                    option.setReleaseDate(opt.getReleaseDate());
                    option.setIsReleased(Boolean.TRUE.equals(opt.getIsReleased()));
                    option.setAssembleDate(opt.getAssembleDate());
                    option.setCreatedAt(LocalDateTime.now());
                    option.setUpdatedAt(LocalDateTime.now());
                    optionMapper.insert(option);
                }
            }
        }

        if (dto.getProjectIds() != null) {
            for (Long projectId : dto.getProjectIds()) {
                CustomFieldProject cfp = new CustomFieldProject();
                cfp.setCustomFieldId(entity.getId());
                cfp.setProjectId(projectId);
                projectMapper.insert(cfp);
            }
        }

        if (dto.getIssueTypes() != null) {
            for (String issueType : dto.getIssueTypes()) {
                CustomFieldIssueType cfit = new CustomFieldIssueType();
                cfit.setCustomFieldId(entity.getId());
                cfit.setIssueType(issueType);
                issueTypeMapper.insert(cfit);
            }
        }

        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomFieldDefinition update(Long id, UpdateCustomFieldDTO dto) {
        CustomFieldDefinition entity = EntityUtils.requireFound(definitionMapper.selectById(id), "自定义字段", id);

        // 内置字段限制：不允许修改名称（其他配置如 isPrivate、isRequired 等允许修改）
        if (BUILTIN_FIELD_IDS.contains(id) && dto.getName() != null
                && !dto.getName().isBlank() && !dto.getName().equals(entity.getName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内置字段名称不允许修改");
        }

        boolean wasForAll = Boolean.TRUE.equals(entity.getIsForAll());

        if (dto.getName() != null && !dto.getName().isBlank()) {
            boolean exists = definitionMapper.exists(new LambdaQueryWrapper<CustomFieldDefinition>()
                    .apply("LOWER(name) = LOWER({0})", dto.getName())
                    .ne(CustomFieldDefinition::getId, id));
            if (exists) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "自定义字段名称已存在");
            }
            entity.setName(dto.getName());
        }

        if (dto.getIsRequired() != null) entity.setIsRequired(dto.getIsRequired());
        if (dto.getIsForAll() != null) entity.setIsForAll(dto.getIsForAll());
        if (dto.getDefaultValue() != null) entity.setDefaultValue(dto.getDefaultValue());
        if (dto.getMinLength() != null) entity.setMinLength(dto.getMinLength());
        if (dto.getMaxLength() != null) entity.setMaxLength(dto.getMaxLength());
        if (dto.getRegexp() != null) entity.setRegexp(dto.getRegexp());
        if (dto.getIsMulti() != null && CustomFieldOptionService.isEnumLikeFormat(entity.getFieldFormat())) {
            boolean currentIsMulti = Boolean.TRUE.equals(entity.getIsMulti());
            if (dto.getIsMulti() != currentIsMulti) {
                long valueCount = valueMapper.selectCount(
                        new LambdaQueryWrapper<CustomFieldValue>()
                                .eq(CustomFieldValue::getCustomFieldId, id));
                if (valueCount > 0) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "该字段已被 " + valueCount + " 条工单数据使用，无法切换单选/多选模式。如需变更，请创建新字段并迁移数据。");
                }
                entity.setIsMulti(dto.getIsMulti());
            }
        }
        if (dto.getIsHiddenInList() != null) entity.setIsHiddenInList(dto.getIsHiddenInList());
        if (dto.getAliases() != null) entity.setAliases(dto.getAliases());
        if (dto.getIsPrivate() != null) entity.setIsPrivate(dto.getIsPrivate());
        try {
            definitionMapper.updateById(entity);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("uk_custom_field_name")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "自定义字段名称已存在");
            }
            throw ex;
        }

        if (CustomFieldOptionService.isEnumLikeFormat(entity.getFieldFormat()) && dto.getOptions() != null) {
            optionService.updateListOptions(id, dto.getOptions());
        }

        if (CustomFieldOptionService.isEnumLikeFormat(entity.getFieldFormat()) && dto.getCopyOptionsFromFieldId() != null && dto.getOptions() == null) {
            optionService.appendOptionsFromField(id, dto.getCopyOptionsFromFieldId());
        }

        if (dto.getProjectIds() != null) {
            projectMapper.delete(new LambdaQueryWrapper<CustomFieldProject>()
                    .eq(CustomFieldProject::getCustomFieldId, id));
            for (Long projectId : dto.getProjectIds()) {
                CustomFieldProject cfp = new CustomFieldProject();
                cfp.setCustomFieldId(id);
                cfp.setProjectId(projectId);
                projectMapper.insert(cfp);
            }
        }

        if (dto.getIssueTypes() != null) {
            issueTypeMapper.delete(new LambdaQueryWrapper<CustomFieldIssueType>()
                    .eq(CustomFieldIssueType::getCustomFieldId, id));
            for (String issueType : dto.getIssueTypes()) {
                CustomFieldIssueType cfit = new CustomFieldIssueType();
                cfit.setCustomFieldId(id);
                cfit.setIssueType(issueType);
                issueTypeMapper.insert(cfit);
            }
        }

        boolean isNowForAll = Boolean.TRUE.equals(entity.getIsForAll());
        if (wasForAll && !isNowForAll) {
            List<Long> retainedProjectIds;
            if (dto.getProjectIds() != null) {
                retainedProjectIds = dto.getProjectIds();
            } else {
                retainedProjectIds = projectMapper.selectList(
                        new LambdaQueryWrapper<CustomFieldProject>()
                                .eq(CustomFieldProject::getCustomFieldId, id))
                        .stream()
                        .map(CustomFieldProject::getProjectId)
                        .toList();
            }
            cleanOrphanValuesForScopeReduction(id, retainedProjectIds);
        }

        return entity;
    }

    public CustomFieldDefinition getById(Long id) {
        CustomFieldDefinition entity = EntityUtils.requireFound(definitionMapper.selectById(id), "自定义字段", id);
        return entity;
    }

    public CustomFieldUsageVO getUsage(Long id) {
        CustomFieldDefinition field = EntityUtils.requireFound(definitionMapper.selectById(id), "自定义字段", id);

        CustomFieldUsageVO usage = new CustomFieldUsageVO();

        List<Object> issueIds = valueMapper.selectObjs(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .select(CustomFieldValue::getIssueId)
                        .eq(CustomFieldValue::getCustomFieldId, id)
                        .groupBy(CustomFieldValue::getIssueId));
        usage.setIssueCount(issueIds.size());

        usage.setValueCount(valueMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getCustomFieldId, id)));

        usage.setIsForAll(Boolean.TRUE.equals(field.getIsForAll()));
        if (Boolean.TRUE.equals(field.getIsForAll())) {
            usage.setProjectCount(projectEntityMapper.selectCount(null));
        } else {
            usage.setProjectCount(projectMapper.selectCount(
                    new LambdaQueryWrapper<CustomFieldProject>()
                            .eq(CustomFieldProject::getCustomFieldId, id)));
        }

        usage.setIssueTypeCount(issueTypeMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldIssueType>()
                        .eq(CustomFieldIssueType::getCustomFieldId, id)));

        usage.setOptionCount(optionMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, id)));

        usage.setConditionRefCount(projectMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getConditionFieldId, id)));

        usage.setFilterRefCount(projectMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getFilterFieldId, id)));

        return usage;
    }

    @AuditLog(action = "delete_custom_field", targetType = "custom_field", targetId = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, boolean confirm) {
        if (definitionMapper.selectById(id) == null) {
            throw BusinessException.notFound("自定义字段", id);
        }

        // 内置字段不可删除（Priority / Type / Due Date）
        if (BUILTIN_FIELD_IDS.contains(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内置字段不允许删除");
        }

        long conditionRefCount = projectMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getConditionFieldId, id));

        long filterRefCount = projectMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getFilterFieldId, id));

        if (!confirm) {
            long valueCount = valueMapper.selectCount(
                    new LambdaQueryWrapper<CustomFieldValue>()
                            .eq(CustomFieldValue::getCustomFieldId, id));
            if (valueCount > 0 || conditionRefCount > 0 || filterRefCount > 0) {
                StringBuilder message = new StringBuilder();
                if (valueCount > 0) {
                    message.append("此字段被 ").append(valueCount).append(" 条工单值记录引用");
                }
                if (conditionRefCount > 0) {
                    if (!message.isEmpty()) message.append("，");
                    message.append("被 ").append(conditionRefCount)
                           .append(" 条字段配置作为条件源引用（删除后相关条件规则将失效，被隐藏的字段将变为始终显示）");
                }
                if (filterRefCount > 0) {
                    if (!message.isEmpty()) message.append("，");
                    message.append("被 ").append(filterRefCount)
                           .append(" 条字段配置作为值过滤源引用（删除后相关字段将恢复为显示所有选项）");
                }
                message.append("，请使用 confirm=true 确认删除");
                throw new BusinessException(ErrorCode.BAD_REQUEST, message.toString());
            }
        }

        // 清理条件字段引用
        if (conditionRefCount > 0) {
            projectMapper.update(null,
                    new LambdaUpdateWrapper<CustomFieldProject>()
                            .eq(CustomFieldProject::getConditionFieldId, id)
                            .set(CustomFieldProject::getConditionFieldId, null)
                            .set(CustomFieldProject::getConditionValues, null));
            log.info("Cleared condition references for deleted field {}: {} mappings affected", id, conditionRefCount);
        }

        // 清理值过滤源字段引用（与条件字段引用清理对称）
        if (filterRefCount > 0) {
            projectMapper.update(null,
                    new LambdaUpdateWrapper<CustomFieldProject>()
                            .eq(CustomFieldProject::getFilterFieldId, id)
                            .set(CustomFieldProject::getFilterFieldId, null)
                            .set(CustomFieldProject::getFilterRules, null));
            log.info("Cleared filter references for deleted field {}: {} mappings affected", id, filterRefCount);
        }

        // 级联删除关联数据（参考 YouTrack/OpenProject：删除字段时清理所有相关值、选项及关联记录）
        cascadeDeleteFieldRelations(id);

        definitionMapper.deleteById(id);

        log.info("Deleted custom field definition: {}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorder(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            CustomFieldDefinition entity = new CustomFieldDefinition();
            entity.setId(ids.get(i));
            entity.setPosition(i);
            definitionMapper.updateById(entity);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void setAutoAttach(Long fieldId, boolean enabled) {
        CustomFieldDefinition field = EntityUtils.requireFound(definitionMapper.selectById(fieldId), "自定义字段", fieldId);
        field.setIsAutoAttach(enabled);
        definitionMapper.updateById(field);
        log.info("字段 {} 的 auto-attach 状态设置为: {}", fieldId, enabled);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchUpdate(List<Long> ids, String field, Boolean value) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段ID列表不能为空");
        }

        LambdaUpdateWrapper<CustomFieldDefinition> wrapper = new LambdaUpdateWrapper<CustomFieldDefinition>()
                .in(CustomFieldDefinition::getId, ids);

        switch (field) {
            case "isForAll" -> wrapper.set(CustomFieldDefinition::getIsForAll, value);
            case "isAutoAttach" -> wrapper.set(CustomFieldDefinition::getIsAutoAttach, value);
            case "isHiddenInList" -> wrapper.set(CustomFieldDefinition::getIsHiddenInList, value);
            case "isPrivate" -> wrapper.set(CustomFieldDefinition::getIsPrivate, value);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的批量更新字段: " + field);
        }

        definitionMapper.update(null, wrapper);
        log.info("批量更新 {} 个字段的 {} 属性为: {}", ids.size(), field, value);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段ID列表不能为空");
        }

        // 清理条件字段引用
        projectMapper.update(null,
                new LambdaUpdateWrapper<CustomFieldProject>()
                        .in(CustomFieldProject::getConditionFieldId, ids)
                        .set(CustomFieldProject::getConditionFieldId, null)
                        .set(CustomFieldProject::getConditionValues, null));

        // 清理值过滤源字段引用（与条件字段引用清理对称）
        projectMapper.update(null,
                new LambdaUpdateWrapper<CustomFieldProject>()
                        .in(CustomFieldProject::getFilterFieldId, ids)
                        .set(CustomFieldProject::getFilterFieldId, null)
                        .set(CustomFieldProject::getFilterRules, null));

        // 级联删除关联数据（参考 YouTrack/OpenProject：删除字段时清理所有相关值、选项及关联记录）
        for (Long id : ids) {
            cascadeDeleteFieldRelations(id);
        }

        definitionMapper.deleteBatchIds(ids);
        log.info("批量删除 {} 个自定义字段: {}", ids.size(), ids);
    }

    /**
     * 级联删除自定义字段的所有关联记录。
     * <p>按顺序删除 value、option、project、issueType 四张关联表中该字段的记录。
     * 参考 OpenProject 的 dependent: :destroy 模式，在事务内按顺序清理所有依赖。</p>
     *
     * @param fieldId 要删除关联数据的字段ID
     */
    private void cascadeDeleteFieldRelations(Long fieldId) {
        // 删除工单中该字段的值记录
        long deletedValues = valueMapper.delete(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getCustomFieldId, fieldId));
        // 删除该字段的选项定义
        long deletedOptions = optionMapper.delete(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId));
        // 删除该字段的项目关联配置
        long deletedProjects = projectMapper.delete(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId));
        // 删除该字段的工单类型绑定
        long deletedIssueTypes = issueTypeMapper.delete(
                new LambdaQueryWrapper<CustomFieldIssueType>()
                        .eq(CustomFieldIssueType::getCustomFieldId, fieldId));

        log.info("Cascade deleted relations for field {}: values={}, options={}, projects={}, issueTypes={}",
                fieldId, deletedValues, deletedOptions, deletedProjects, deletedIssueTypes);
    }

    // ========== 委托方法（保持向后兼容 API） ==========

    /** 委托给 {@link CustomFieldOptionService} */
    public void reorderOptions(Long fieldId, List<Long> optionIds) {
        optionService.reorderOptions(fieldId, optionIds);
    }

    /** 委托给 {@link CustomFieldOptionService}：设置字段选项排序模式 */
    public void setSortMode(Long fieldId, String sortMode) {
        optionService.setSortMode(fieldId, sortMode);
    }

    /** 委托给 {@link CustomFieldOptionService} */
    public void setOptionArchived(Long fieldId, Long optionId, boolean archived) {
        optionService.setOptionArchived(fieldId, optionId, archived);
    }

    /** 委托给 {@link CustomFieldOptionService}：合并另一个字段的选项到当前字段 */
    public int[] mergeOptionsFromField(Long targetFieldId, Long sourceFieldId) {
        return optionService.mergeOptionsFromField(targetFieldId, sourceFieldId);
    }

    /** 委托给 {@link CustomFieldOptionService} */
    public List<OptionUsageItemVO> getOptionUsage(Long id) {
        return optionService.getOptionUsage(id);
    }

    /** 委托给 {@link CustomFieldOptionService} */
    @Transactional(rollbackFor = Exception.class)
    public CustomFieldOption addOptionInline(Long projectId, Long fieldId, String value, String color, Long ownerUserId, java.time.LocalDate releaseDate, Boolean isReleased, java.time.LocalDate assembleDate) {
        return optionService.addOptionInline(projectId, fieldId, value, color, ownerUserId, releaseDate, isReleased, assembleDate);
    }

    // ========== 项目级独立选项集管理（Make Independent Copy）==========

    /** 获取字段在项目中的选项集状态，委托给 {@link CustomFieldOptionService} */
    public com.trackflow.customfield.vo.OptionSetStatusVO getOptionSetStatus(Long projectId, Long fieldId) {
        return optionService.getOptionSetStatus(projectId, fieldId);
    }

    /** 创建项目级独立选项副本，委托给 {@link CustomFieldOptionService} */
    @Transactional(rollbackFor = Exception.class)
    public List<CustomFieldOption> makeIndependentCopy(Long projectId, Long fieldId, boolean emptyOptions) {
        return optionService.makeIndependentCopy(projectId, fieldId, emptyOptions);
    }

    /** 恢复为全局共享选项集，委托给 {@link CustomFieldOptionService} */
    @Transactional(rollbackFor = Exception.class)
    public void revertToShared(Long projectId, Long fieldId, boolean confirm) {
        optionService.revertToShared(projectId, fieldId, confirm);
    }

    /** 获取项目中字段的有效选项列表，委托给 {@link CustomFieldOptionService} */
    public List<CustomFieldOption> getEffectiveOptions(Long fieldId, Long projectId) {
        return optionService.getEffectiveOptions(fieldId, projectId);
    }

    /** 更新项目独立选项集，委托给 {@link CustomFieldOptionService} */
    @Transactional(rollbackFor = Exception.class)
    public void updateProjectOptions(Long projectId, Long fieldId, List<UpdateCustomFieldDTO.OptionItem> options) {
        // 验证项目使用独立选项集
        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));
        if (mapping == null || !Boolean.TRUE.equals(mapping.getHasIndependentOptions())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "此项目未使用独立选项集，请先创建独立副本");
        }
        optionService.updateListOptions(fieldId, options, projectId);
    }

    /** 批量获取多个字段在指定项目中的有效选项列表，委托给 {@link CustomFieldOptionService} */
    public Map<Long, List<CustomFieldOption>> getBatchEffectiveOptions(List<Long> fieldIds, Long projectId) {
        return optionService.getBatchEffectiveOptions(fieldIds, projectId);
    }

    /** 委托给 {@link CustomFieldValueService} */
    public Map<Long, String> applyDefaultsAndValidate(Map<Long, String> userProvided, String issueType, Long projectId) {
        List<CustomFieldDefinition> applicableFields = excludeStateField(listByProject(projectId, issueType));
        return valueService.applyDefaultsAndValidate(userProvided, issueType, projectId, applicableFields);
    }

    /** 仅应用默认值不校验必填（用于子工单快速创建），委托给 {@link CustomFieldValueService} */
    public Map<Long, String> applyDefaultsOnly(Map<Long, String> userProvided, String issueType, Long projectId) {
        List<CustomFieldDefinition> applicableFields = excludeStateField(listByProject(projectId, issueType));
        return valueService.applyDefaultsOnly(userProvided, issueType, projectId, applicableFields);
    }

    /** 委托给 {@link CustomFieldValueService}，并触发级联清除 */
    @Transactional(rollbackFor = Exception.class)
    public void saveValues(Long issueId, Map<Long, String> fieldValues, String issueType, Long projectId) {
        fieldValues = excludeStateFieldValue(fieldValues);
        List<CustomFieldDefinition> applicableFields = excludeStateField(listByProject(projectId, issueType));
        valueService.saveValues(issueId, fieldValues, issueType, projectId, applicableFields);
        // 级联清除：每个被修改的字段都可能是其他字段的 filterFieldId
        cascadeClearForBatch(issueId, fieldValues, projectId);
    }

    /** 委托给 {@link CustomFieldValueService}，并触发级联清除 */
    @Transactional(rollbackFor = Exception.class)
    public void saveValues(Long issueId, Map<Long, String> fieldValues, String issueType, Long projectId,
                           CustomFieldValidateMode mode) {
        fieldValues = excludeStateFieldValue(fieldValues);
        List<CustomFieldDefinition> applicableFields = excludeStateField(listByProject(projectId, issueType));
        valueService.saveValues(issueId, fieldValues, issueType, projectId, applicableFields, mode);
        // 级联清除
        cascadeClearForBatch(issueId, fieldValues, projectId);
    }

    /**
     * 委托给 {@link CustomFieldValueService}，支持跳过初始赋值活动记录。
     * 用于工单创建场景：自定义字段初始赋值不应生成"未设置→默认值"的活动记录（REQ-387）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveValues(Long issueId, Map<Long, String> fieldValues, String issueType, Long projectId,
                           CustomFieldValidateMode mode, boolean skipActivity) {
        fieldValues = excludeStateFieldValue(fieldValues);
        List<CustomFieldDefinition> applicableFields = excludeStateField(listByProject(projectId, issueType));
        valueService.saveValues(issueId, fieldValues, issueType, projectId, applicableFields, mode, skipActivity);
        // 级联清除
        cascadeClearForBatch(issueId, fieldValues, projectId);
    }

    /**
     * 对批量保存中修改的每个字段执行级联清除。
     * 使用 Set 防止同一字段被重复清除（A→B→A 循环场景保护）。
     */
    private void cascadeClearForBatch(Long issueId, Map<Long, String> fieldValues, Long projectId) {
        if (fieldValues == null || fieldValues.isEmpty()) return;
        Set<Long> processedFields = new HashSet<>(fieldValues.keySet());
        for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
            List<String> cleared = valueService.cascadeClearDependentValues(issueId, entry.getKey(), entry.getValue(), projectId);
            // 如果级联清除了某个字段，该字段又可能是别的字段的源 → 继续传播
            // 但为防止无限循环，不做递归传播（YouTrack 也仅做一层级联）
            if (!cleared.isEmpty()) {
                log.debug("Batch save cascade cleared fields: {} (source field: {})", cleared, entry.getKey());
            }
        }
    }

    /**
     * 保存单个字段值，并触发级联清除依赖字段的失效值。
     * State 字段（由工作流引擎管理）的写入请求将被拒绝。
     *
     * @return 被级联清除的字段名称列表（空列表表示无级联清除发生）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> saveSingleValue(Long issueId, Long customFieldId, String value, String issueType, Long projectId) {
        if (STATE_FIELD_ID.equals(customFieldId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态字段由工作流引擎管理，不能通过自定义字段接口修改");
        }
        List<CustomFieldDefinition> applicableFields = excludeStateField(listByProject(projectId, issueType));
        valueService.saveSingleValue(issueId, customFieldId, value, issueType, projectId, applicableFields);
        // 级联清除：如果被修改的字段是其他字段的 filterFieldId，清除失效的依赖值
        return valueService.cascadeClearDependentValues(issueId, customFieldId, value, projectId);
    }

    /** 委托给 {@link CustomFieldValueService} */
    public Map<Long, String> getValues(Long issueId) {
        return valueService.getValues(issueId);
    }

    /**
     * 获取字段值 MultiMap（明确区分单值/多值）。
     * 委托给 {@link CustomFieldValueService#getValuesAsMultiMap(Long)}
     */
    public Map<Long, List<String>> getValuesAsMultiMap(Long issueId) {
        return valueService.getValuesAsMultiMap(issueId);
    }

    /** 委托给 {@link CustomFieldValueService} */
    public void deleteValuesByIssue(Long issueId) {
        valueService.deleteValuesByIssue(issueId);
    }

    /** 委托给 {@link CustomFieldValueService} */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> removeOrphanValues(Long issueId, String newIssueType, Long projectId) {
        List<CustomFieldDefinition> applicableFields = listByProject(projectId, newIssueType);
        return valueService.removeOrphanValues(issueId, newIssueType, projectId, applicableFields);
    }

    /** 委托给 {@link CustomFieldValueService} */
    public void checkFieldEditable(Long projectId, Long fieldId) {
        valueService.checkFieldEditable(projectId, fieldId);
    }

    /** 委托给 {@link CustomFieldDisplayService} */
    public Map<Long, Map<String, String>> getBatchDisplayValues(List<Long> issueIds) {
        return displayService.getBatchDisplayValues(issueIds);
    }

    /** 委托给 {@link CustomFieldDisplayService} */
    public Map<Long, Map<String, String>> getBatchDisplayValues(List<Long> issueIds, Map<Long, Map<String, String>> colorOutMap) {
        return displayService.getBatchDisplayValues(issueIds, colorOutMap);
    }

    /** 委托给 {@link CustomFieldDisplayService} — 批量获取结构化自定义字段详情 */
    public Map<Long, List<CustomFieldValueVO>> getBatchCustomFieldDetails(List<Long> issueIds) {
        return displayService.getBatchCustomFieldDetails(issueIds);
    }

    /** 委托给 {@link CustomFieldDisplayService} */
    public List<CustomFieldValueVO> getValuesForDisplay(Long issueId, Long projectId, String issueType) {
        List<CustomFieldDefinition> applicableFields = listByProject(projectId, issueType);
        // Apply visibility filter
        Map<Long, CustomFieldProject> conditionsMap = getProjectFieldConditions(projectId);
        List<Long> userRoleIds = getCurrentUserRoleIds(projectId);
        applicableFields = filterFieldsByVisibility(applicableFields, conditionsMap, userRoleIds, projectId);
        return displayService.getValuesForDisplay(issueId, projectId, issueType, applicableFields);
    }

    // ========== 项目级字段查询 ==========

    public List<CustomFieldDefinition> listByProject(Long projectId, String issueType) {
        // 获取该项目中被排除的全局字段 ID
        Set<Long> excludedFieldIds = getExcludedFieldIds(projectId);

        List<CustomFieldDefinition> globalFields = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getIsForAll, true)
                        .notIn(!excludedFieldIds.isEmpty(), CustomFieldDefinition::getId, excludedFieldIds)
                        .orderByAsc(CustomFieldDefinition::getPosition));

        List<Long> projectFieldIds = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .eq(CustomFieldProject::getIsExcluded, false))
                .stream()
                .map(CustomFieldProject::getCustomFieldId)
                .toList();

        List<CustomFieldDefinition> projectFields = projectFieldIds.isEmpty()
                ? List.of()
                : definitionMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinition>()
                    .in(CustomFieldDefinition::getId, projectFieldIds)
                    .orderByAsc(CustomFieldDefinition::getPosition));

        Map<Long, CustomFieldDefinition> merged = new LinkedHashMap<>();
        globalFields.forEach(f -> merged.put(f.getId(), f));
        projectFields.forEach(f -> merged.put(f.getId(), f));

        if (issueType != null && !issueType.isBlank()) {
            Set<Long> mergedFieldIds = merged.keySet();
            if (!mergedFieldIds.isEmpty()) {
                Set<Long> typeRestricted = issueTypeMapper.selectList(
                        new LambdaQueryWrapper<CustomFieldIssueType>()
                                .in(CustomFieldIssueType::getCustomFieldId, mergedFieldIds))
                        .stream()
                        .collect(Collectors.groupingBy(CustomFieldIssueType::getCustomFieldId,
                                Collectors.mapping(CustomFieldIssueType::getIssueType, Collectors.toSet())))
                        .entrySet().stream()
                        .filter(e -> !e.getValue().contains(issueType))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());

                merged.entrySet().removeIf(e -> typeRestricted.contains(e.getKey()));
            }
        }

        return new ArrayList<>(merged.values());
    }

    // ========== 列配置 ==========

    public List<AvailableColumnVO> getAllAvailableColumns() {
        List<AvailableColumnVO> columns = buildStandardColumns();

        List<CustomFieldDefinition> allFields = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getIsHiddenInList, false)
                        .orderByAsc(CustomFieldDefinition::getPosition));
        for (CustomFieldDefinition field : allFields) {
            columns.add(buildCustomColumn(field));
        }
        return columns;
    }

    public List<AvailableColumnVO> getAvailableColumns(Long projectId) {
        List<AvailableColumnVO> columns = buildStandardColumns();

        List<CustomFieldDefinition> fields = listByProject(projectId, null);
        for (CustomFieldDefinition field : fields) {
            if (Boolean.TRUE.equals(field.getIsHiddenInList())) continue;
            columns.add(buildCustomColumn(field));
        }
        return columns;
    }

    // ========== 项目级字段管理 ==========

    public List<CustomFieldDefinition> listProjectFields(Long projectId) {
        // 获取该项目中被排除的全局字段 ID
        Set<Long> excludedFieldIds = getExcludedFieldIds(projectId);

        List<CustomFieldDefinition> globalFields = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getIsForAll, true)
                        .notIn(!excludedFieldIds.isEmpty(), CustomFieldDefinition::getId, excludedFieldIds)
                        .orderByAsc(CustomFieldDefinition::getPosition));

        List<CustomFieldProject> projectMappings = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .eq(CustomFieldProject::getIsExcluded, false)
                        .orderByAsc(CustomFieldProject::getPosition));

        List<Long> projectFieldIds = projectMappings.stream()
                .map(CustomFieldProject::getCustomFieldId)
                .toList();

        List<CustomFieldDefinition> projectFields = projectFieldIds.isEmpty()
                ? List.of()
                : definitionMapper.selectBatchIds(projectFieldIds);

        Map<Long, Integer> positionMap = projectMappings.stream()
                .collect(Collectors.toMap(CustomFieldProject::getCustomFieldId, CustomFieldProject::getPosition));
        projectFields = projectFields.stream()
                .sorted(Comparator.comparingInt(f -> positionMap.getOrDefault(f.getId(), 0)))
                .toList();

        List<CustomFieldDefinition> result = new ArrayList<>(globalFields);
        for (CustomFieldDefinition pf : projectFields) {
            if (result.stream().noneMatch(f -> f.getId().equals(pf.getId()))) {
                result.add(pf);
            }
        }
        return result;
    }

    public List<CustomFieldDefinition> listAvailableFieldsForProject(Long projectId) {
        // 已附加（非排除）的字段 ID
        Set<Long> attachedIds = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .eq(CustomFieldProject::getIsExcluded, false))
                .stream()
                .map(CustomFieldProject::getCustomFieldId)
                .collect(Collectors.toSet());

        // 非全局的、未附加的字段
        List<CustomFieldDefinition> allNonGlobal = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getIsForAll, false)
                        .orderByAsc(CustomFieldDefinition::getPosition));

        List<CustomFieldDefinition> available = new ArrayList<>(allNonGlobal.stream()
                .filter(f -> !attachedIds.contains(f.getId()))
                .toList());

        // 被排除的全局字段也可重新附加
        Set<Long> excludedFieldIds = getExcludedFieldIds(projectId);
        if (!excludedFieldIds.isEmpty()) {
            List<CustomFieldDefinition> excludedGlobalFields = definitionMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldDefinition>()
                            .in(CustomFieldDefinition::getId, excludedFieldIds)
                            .orderByAsc(CustomFieldDefinition::getPosition));
            available.addAll(excludedGlobalFields);
        }

        return available;
    }

    @Transactional(rollbackFor = Exception.class)
    public void attachFieldToProject(Long projectId, Long customFieldId) {
        CustomFieldDefinition field = EntityUtils.requireFound(definitionMapper.selectById(customFieldId), "自定义字段", customFieldId);

        if (Boolean.TRUE.equals(field.getIsForAll())) {
            // 全局字段：检查是否有排除记录，有则删除（恢复全局可见性）
            CustomFieldProject exclusion = projectMapper.selectOne(
                    new LambdaQueryWrapper<CustomFieldProject>()
                            .eq(CustomFieldProject::getCustomFieldId, customFieldId)
                            .eq(CustomFieldProject::getProjectId, projectId)
                            .eq(CustomFieldProject::getIsExcluded, true));
            if (exclusion == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "全局字段已对此项目可用，无需重复附加");
            }
            projectMapper.deleteById(exclusion.getId());
            log.info("全局字段 {} 恢复到项目 {}（删除排除记录）", customFieldId, projectId);
            return;
        }

        // 非全局字段：正常附加逻辑
        boolean exists = projectMapper.exists(new LambdaQueryWrapper<CustomFieldProject>()
                .eq(CustomFieldProject::getCustomFieldId, customFieldId)
                .eq(CustomFieldProject::getProjectId, projectId));
        if (exists) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该字段已附加到本项目");
        }

        Long count = projectMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .eq(CustomFieldProject::getIsExcluded, false));
        int position = count != null ? count.intValue() : 0;

        CustomFieldProject cfp = new CustomFieldProject();
        cfp.setCustomFieldId(customFieldId);
        cfp.setProjectId(projectId);
        cfp.setPosition(position);
        cfp.setIsExcluded(false);
        projectMapper.insert(cfp);
    }

    @Transactional(rollbackFor = Exception.class)
    public void detachFieldFromProject(Long projectId, Long customFieldId) {
        CustomFieldDefinition field = EntityUtils.requireFound(definitionMapper.selectById(customFieldId), "自定义字段", customFieldId);

        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, customFieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));

        if (Boolean.TRUE.equals(field.getIsForAll())) {
            // 全局字段：创建或更新排除记录（YouTrack 行为：即使全局字段也可从项目移除）
            if (mapping == null) {
                CustomFieldProject exclusion = new CustomFieldProject();
                exclusion.setCustomFieldId(customFieldId);
                exclusion.setProjectId(projectId);
                exclusion.setPosition(0);
                exclusion.setIsExcluded(true);
                projectMapper.insert(exclusion);
            } else if (!Boolean.TRUE.equals(mapping.getIsExcluded())) {
                mapping.setIsExcluded(true);
                projectMapper.updateById(mapping);
            } else {
                // 已经是排除状态，无需操作
                return;
            }
            log.info("全局字段 {} 从项目 {} 中排除", customFieldId, projectId);
        } else {
            // 非全局字段：删除映射记录
            if (mapping == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该字段未附加到本项目");
            }
            projectMapper.deleteById(mapping.getId());
        }

        // 无论全局还是非全局，都清除该项目内所有工单的字段值
        valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getCustomFieldId, customFieldId)
                .apply("issue_id IN (SELECT id FROM issue WHERE project_id = {0} AND deleted_at IS NULL)", projectId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorderProjectFields(Long projectId, List<Long> fieldIds) {
        List<CustomFieldProject> mappings = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .in(CustomFieldProject::getCustomFieldId, fieldIds));

        Map<Long, CustomFieldProject> mappingMap = mappings.stream()
                .collect(Collectors.toMap(CustomFieldProject::getCustomFieldId, m -> m));

        for (int i = 0; i < fieldIds.size(); i++) {
            CustomFieldProject mapping = mappingMap.get(fieldIds.get(i));
            if (mapping != null) {
                mapping.setPosition(i);
                projectMapper.updateById(mapping);
            }
        }
    }

    // ========== 条件显示 ==========

    public Map<Long, CustomFieldProject> getProjectFieldConditions(Long projectId) {
        List<CustomFieldProject> mappings = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId));
        return mappings.stream()
                .collect(Collectors.toMap(CustomFieldProject::getCustomFieldId, m -> m, (a, b) -> a));
    }

    @Transactional(rollbackFor = Exception.class)
    public void setFieldCondition(Long projectId, Long fieldId, Long conditionFieldId, List<String> conditionValues) {
        CustomFieldDefinition targetField = definitionMapper.selectById(fieldId);
        if (targetField == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标字段不存在");
        }

        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));

        if (mapping == null) {
            if (!Boolean.TRUE.equals(targetField.getIsForAll())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该字段未附加到本项目");
            }
            mapping = new CustomFieldProject();
            mapping.setCustomFieldId(fieldId);
            mapping.setProjectId(projectId);
            mapping.setPosition(0);
        }

        if (conditionFieldId == null) {
            mapping.setConditionFieldId(null);
            mapping.setConditionValues(null);
            if (mapping.getId() != null) {
                projectMapper.updateById(mapping);
            }
            return;
        }

        CustomFieldDefinition condField = definitionMapper.selectById(conditionFieldId);
        if (condField == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "条件源字段不存在");
        }
        if (!"list".equals(condField.getFieldFormat()) && !"ownedField".equals(condField.getFieldFormat()) && !"version".equals(condField.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条件源字段必须是列表(枚举)类型");
        }
        if (Boolean.TRUE.equals(condField.getIsMulti())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条件源字段必须是单值选择（不支持多值字段作为条件源）");
        }
        if (conditionFieldId.equals(fieldId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段不能以自身作为条件源");
        }

        List<CustomFieldDefinition> projectFields = listByProject(projectId, null);
        boolean condFieldInProject = projectFields.stream()
                .anyMatch(f -> f.getId().equals(conditionFieldId));
        if (!condFieldInProject) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条件源字段未在本项目中启用");
        }

        CustomFieldProject condMapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, conditionFieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));
        if (condMapping != null && condMapping.getConditionFieldId() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持链式条件依赖（条件源字段本身已有条件）");
        }

        if (conditionValues == null || conditionValues.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "必须至少指定一个触发值");
        }

        List<CustomFieldOption> condOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, conditionFieldId));
        Set<String> validOptionIds = condOptions.stream()
                .map(o -> String.valueOf(o.getId()))
                .collect(Collectors.toSet());

        for (String val : conditionValues) {
            if (!validOptionIds.contains(val)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的条件值: " + val);
            }
        }

        mapping.setConditionFieldId(conditionFieldId);
        mapping.setConditionValues(toJsonArray(conditionValues));
        if (mapping.getId() != null) {
            projectMapper.updateById(mapping);
        } else {
            projectMapper.insert(mapping);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int clearHiddenValues(Long projectId, Long fieldId) {
        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));

        if (mapping == null || mapping.getConditionFieldId() == null) {
            return 0;
        }

        Long condFieldId = mapping.getConditionFieldId();
        List<String> condValues = parseJsonArray(mapping.getConditionValues());

        List<CustomFieldValue> targetValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getCustomFieldId, fieldId)
                        .apply("issue_id IN (SELECT id FROM issue WHERE project_id = {0} AND deleted_at IS NULL)", projectId));

        if (targetValues.isEmpty()) return 0;

        Set<Long> targetIssueIds = targetValues.stream()
                .map(CustomFieldValue::getIssueId)
                .collect(Collectors.toSet());

        Map<Long, String> condFieldValuesByIssue = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getCustomFieldId, condFieldId)
                        .in(CustomFieldValue::getIssueId, targetIssueIds))
                .stream()
                .collect(Collectors.toMap(CustomFieldValue::getIssueId, CustomFieldValue::getValue, (a, b) -> a));

        int cleared = 0;
        for (CustomFieldValue tv : targetValues) {
            String currentCondValue = condFieldValuesByIssue.get(tv.getIssueId());
            if (currentCondValue == null || !condValues.contains(currentCondValue)) {
                valueMapper.deleteById(tv.getId());
                cleared++;
            }
        }
        return cleared;
    }

    // ========== 可见性 ==========

    @Transactional(rollbackFor = Exception.class)
    public void setFieldVisibility(Long projectId, Long fieldId, List<Long> visibleToRoles, List<Long> updatableByRoles) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }

        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));

        if (mapping == null) {
            if (!Boolean.TRUE.equals(field.getIsForAll())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "字段未附加到此项目");
            }
            mapping = new CustomFieldProject();
            mapping.setCustomFieldId(fieldId);
            mapping.setProjectId(projectId);
            mapping.setPosition(0);
            mapping.setVisibleToRoles(roleIdsToJson(visibleToRoles));
            mapping.setUpdatableByRoles(roleIdsToJson(updatableByRoles));
            projectMapper.insert(mapping);
        } else {
            mapping.setVisibleToRoles(roleIdsToJson(visibleToRoles));
            mapping.setUpdatableByRoles(roleIdsToJson(updatableByRoles));
            projectMapper.updateById(mapping);
        }

        log.info("Updated field visibility: project={}, field={}, visibleTo={}, updatableBy={}",
                projectId, fieldId, visibleToRoles, updatableByRoles);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setFieldProjectOverride(Long projectId, Long fieldId, Boolean isRequired, String defaultValue, Boolean canBeEmpty) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }

        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));

        if (mapping == null) {
            if (!Boolean.TRUE.equals(field.getIsForAll())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "字段未附加到此项目");
            }
            mapping = new CustomFieldProject();
            mapping.setCustomFieldId(fieldId);
            mapping.setProjectId(projectId);
            mapping.setPosition(0);
            mapping.setIsRequired(isRequired);
            mapping.setDefaultValue(defaultValue);
            mapping.setCanBeEmpty(canBeEmpty);
            projectMapper.insert(mapping);
        } else {
            projectMapper.update(null, new LambdaUpdateWrapper<CustomFieldProject>()
                    .eq(CustomFieldProject::getId, mapping.getId())
                    .set(CustomFieldProject::getIsRequired, isRequired)
                    .set(CustomFieldProject::getDefaultValue, defaultValue)
                    .set(CustomFieldProject::getCanBeEmpty, canBeEmpty));
        }

        log.info("Updated field project override: project={}, field={}, isRequired={}, defaultValue={}, canBeEmpty={}",
                projectId, fieldId, isRequired, defaultValue, canBeEmpty);
    }

    // ========== 数字徽章配置 ==========

    /**
     * 设置整数字段在项目中的数字徽章显示配置。
     * <p>
     * 开启后，该字段的值将在工单列表标题左侧以数字徽章形式展示。
     * 仅对整数（integer）类型字段有效。
     *
     * @param projectId       项目ID
     * @param fieldId         字段ID（必须为整数类型）
     * @param showAsBadge     是否显示为徽章
     * @param badgeColorRules 颜色规则 JSON 字符串
     */
    @Transactional(rollbackFor = Exception.class)
    public void setFieldBadgeConfig(Long projectId, Long fieldId, Boolean showAsBadge, String badgeColorRules) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }

        // 仅整数类型字段支持数字徽章
        if (!"int".equals(field.getFieldFormat()) && !"integer".equals(field.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅整数类型字段支持数字徽章显示");
        }

        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));

        if (mapping == null && !Boolean.TRUE.equals(field.getIsForAll())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段未附加到此项目");
        }

        // 先校验：如果要开启徽章，检查同一项目是否已达上限（最多 2 个）
        if (Boolean.TRUE.equals(showAsBadge)) {
            boolean alreadyEnabled = mapping != null && Boolean.TRUE.equals(mapping.getShowAsBadge());
            if (!alreadyEnabled) {
                Long badgeCount = projectMapper.selectCount(
                        new LambdaQueryWrapper<CustomFieldProject>()
                                .eq(CustomFieldProject::getProjectId, projectId)
                                .eq(CustomFieldProject::getShowAsBadge, true)
                                .ne(CustomFieldProject::getIsExcluded, true));
                if (badgeCount >= 2) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "同一项目最多配置 2 个徽章字段");
                }
            }
        }

        // 校验通过后再执行保存
        if (mapping == null) {
            mapping = new CustomFieldProject();
            mapping.setCustomFieldId(fieldId);
            mapping.setProjectId(projectId);
            mapping.setPosition(0);
            mapping.setShowAsBadge(showAsBadge != null ? showAsBadge : false);
            mapping.setBadgeColorRules(badgeColorRules);
            mapping.setIsExcluded(false);
            mapping.setHasIndependentOptions(false);
            projectMapper.insert(mapping);
        } else {
            mapping.setShowAsBadge(showAsBadge != null ? showAsBadge : false);
            mapping.setBadgeColorRules(badgeColorRules);
            projectMapper.updateById(mapping);
        }

        log.info("Updated field badge config: project={}, field={}, showAsBadge={}", projectId, fieldId, showAsBadge);
    }

    // ========== 值过滤规则配置（Filter values based on）==========

    /**
     * 设置字段的值过滤规则（项目级）。
     * <p>
     * 值过滤与条件显示是两个独立机制：
     * - 条件显示（conditionFieldId）：控制字段本身是否出现
     * - 值依赖过滤（filterFieldId）：字段出现，但下拉选项被缩小
     *
     * @param projectId     项目ID
     * @param fieldId       目标字段ID（枚举类型）
     * @param filterFieldId 源字段ID（枚举单值类型），null表示清除
     * @param rules         过滤规则列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void setFieldFilterRules(Long projectId, Long fieldId, Long filterFieldId,
                                     List<com.trackflow.customfield.dto.SetFieldFilterRulesDTO.FilterRuleItem> rules) {
        CustomFieldDefinition targetField = definitionMapper.selectById(fieldId);
        if (targetField == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标字段不存在");
        }

        // 目标字段必须是枚举类型
        if (!"list".equals(targetField.getFieldFormat()) && !"ownedField".equals(targetField.getFieldFormat()) && !"version".equals(targetField.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "值过滤只适用于列表(枚举)类型字段");
        }

        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));

        if (mapping == null) {
            if (!Boolean.TRUE.equals(targetField.getIsForAll())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该字段未附加到本项目");
            }
            mapping = new CustomFieldProject();
            mapping.setCustomFieldId(fieldId);
            mapping.setProjectId(projectId);
            mapping.setPosition(0);
        }

        // 清除过滤规则
        if (filterFieldId == null) {
            mapping.setFilterFieldId(null);
            mapping.setFilterRules(null);
            if (mapping.getId() != null) {
                projectMapper.updateById(mapping);
            }
            log.info("Cleared field filter rules: project={}, field={}", projectId, fieldId);
            return;
        }

        // 校验源字段
        CustomFieldDefinition sourceField = definitionMapper.selectById(filterFieldId);
        if (sourceField == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "源字段不存在");
        }
        if (!"list".equals(sourceField.getFieldFormat()) && !"ownedField".equals(sourceField.getFieldFormat()) && !"version".equals(sourceField.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源字段必须是列表(枚举)类型");
        }
        if (Boolean.TRUE.equals(sourceField.getIsMulti())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源字段必须是单值选择（不支持多值字段作为过滤源）");
        }
        if (filterFieldId.equals(fieldId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段不能以自身作为过滤源");
        }

        // 检查循环依赖：源字段的过滤源不能指向目标字段
        CustomFieldProject sourceMapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, filterFieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));
        if (sourceMapping != null && sourceMapping.getFilterFieldId() != null) {
            if (sourceMapping.getFilterFieldId().equals(fieldId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持循环值过滤依赖");
            }
        }

        // 校验源字段在项目中
        List<CustomFieldDefinition> projectFields = listByProject(projectId, null);
        boolean sourceFieldInProject = projectFields.stream()
                .anyMatch(f -> f.getId().equals(filterFieldId));
        if (!sourceFieldInProject) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源字段未在本项目中启用");
        }

        // 校验规则
        if (rules == null || rules.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "必须至少指定一条过滤规则");
        }

        // 获取源字段有效选项
        List<CustomFieldOption> sourceOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, filterFieldId));
        Set<String> validSourceOptionIds = sourceOptions.stream()
                .map(o -> String.valueOf(o.getId()))
                .collect(Collectors.toSet());

        // 获取目标字段有效选项
        List<CustomFieldOption> targetOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId));
        Set<String> validTargetOptionIds = targetOptions.stream()
                .map(o -> String.valueOf(o.getId()))
                .collect(Collectors.toSet());

        // 校验每条规则的合法性
        for (var rule : rules) {
            if (rule.getWhenValue() == null || rule.getWhenValue().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "规则的 whenValue 不能为空");
            }
            if (!validSourceOptionIds.contains(rule.getWhenValue())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的源选项值: " + rule.getWhenValue());
            }
            if (rule.getShowOnly() == null || rule.getShowOnly().isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "规则的 showOnly 不能为空");
            }
            for (String targetOptId : rule.getShowOnly()) {
                if (!validTargetOptionIds.contains(targetOptId)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的目标选项值: " + targetOptId);
                }
            }
        }

        // 序列化规则为 JSON
        String filterRulesJson = toFilterRulesJson(rules);

        mapping.setFilterFieldId(filterFieldId);
        mapping.setFilterRules(filterRulesJson);
        if (mapping.getId() != null) {
            projectMapper.updateById(mapping);
        } else {
            projectMapper.insert(mapping);
        }

        log.info("Updated field filter rules: project={}, field={}, sourceField={}, rulesCount={}",
                projectId, fieldId, filterFieldId, rules.size());
    }

    /**
     * 将过滤规则列表序列化为 JSON 字符串
     */
    private String toFilterRulesJson(List<com.trackflow.customfield.dto.SetFieldFilterRulesDTO.FilterRuleItem> rules) {
        try {
            List<Map<String, Object>> rulesList = new ArrayList<>();
            for (var rule : rules) {
                Map<String, Object> ruleMap = new LinkedHashMap<>();
                ruleMap.put("whenValue", rule.getWhenValue());
                ruleMap.put("showOnly", rule.getShowOnly());
                rulesList.add(ruleMap);
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rulesList);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "序列化过滤规则失败");
        }
    }

    // ========== Fields in Projects 矩阵 ==========

    @Transactional(readOnly = true)
    public List<ProjectFieldsVO> getFieldsInProjects() {
        List<Project> projects = projectEntityMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getStatus, ProjectStatus.ACTIVE)
                        .orderByAsc(Project::getName));

        List<CustomFieldDefinition> globalFields = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getIsForAll, true)
                        .orderByAsc(CustomFieldDefinition::getPosition));

        List<CustomFieldProject> allMappings = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .orderByAsc(CustomFieldProject::getPosition));
        Map<String, CustomFieldProject> mappingIndex = new HashMap<>();
        Map<Long, List<Long>> projectFieldIdsMap = new HashMap<>();
        for (CustomFieldProject m : allMappings) {
            mappingIndex.put(m.getProjectId() + ":" + m.getCustomFieldId(), m);
            projectFieldIdsMap.computeIfAbsent(m.getProjectId(), k -> new ArrayList<>()).add(m.getCustomFieldId());
        }

        Set<Long> allProjectFieldIds = allMappings.stream()
                .map(CustomFieldProject::getCustomFieldId)
                .collect(Collectors.toSet());
        Map<Long, CustomFieldDefinition> fieldMap = new HashMap<>();
        globalFields.forEach(f -> fieldMap.put(f.getId(), f));
        if (!allProjectFieldIds.isEmpty()) {
            definitionMapper.selectBatchIds(allProjectFieldIds)
                    .forEach(f -> fieldMap.put(f.getId(), f));
        }

        List<ProjectFieldsVO> result = new ArrayList<>();
        for (Project project : projects) {
            ProjectFieldsVO pvo = new ProjectFieldsVO();
            pvo.setProjectId(String.valueOf(project.getId()));
            pvo.setProjectName(project.getName());
            pvo.setProjectKey(project.getKey());

            List<ProjectFieldsVO.FieldSummaryVO> fields = new ArrayList<>();

            for (CustomFieldDefinition gf : globalFields) {
                CustomFieldProject mp = mappingIndex.get(project.getId() + ":" + gf.getId());
                fields.add(toFieldSummary(gf, mp));
            }

            List<Long> projectFieldIds = projectFieldIdsMap.getOrDefault(project.getId(), List.of());
            for (Long fieldId : projectFieldIds) {
                CustomFieldDefinition fd = fieldMap.get(fieldId);
                if (fd != null && !Boolean.TRUE.equals(fd.getIsForAll())) {
                    CustomFieldProject mp = mappingIndex.get(project.getId() + ":" + fieldId);
                    fields.add(toFieldSummary(fd, mp));
                }
            }

            pvo.setFields(fields);
            result.add(pvo);
        }
        return result;
    }

    // ========== 辅助查询方法 ==========

    public Page<CustomFieldDefinition> list(Page<CustomFieldDefinition> page, String fieldFormat, String keyword) {
        LambdaQueryWrapper<CustomFieldDefinition> wrapper = new LambdaQueryWrapper<>();
        if (fieldFormat != null && !fieldFormat.isBlank()) {
            wrapper.eq(CustomFieldDefinition::getFieldFormat, fieldFormat);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(CustomFieldDefinition::getName, keyword);
        }
        wrapper.orderByAsc(CustomFieldDefinition::getPosition);
        return definitionMapper.selectPage(page, wrapper);
    }

    public List<CustomFieldDefinition> listEnumFields() {
        return definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .in(CustomFieldDefinition::getFieldFormat, "list", "state", "ownedField", "version")
                        .orderByAsc(CustomFieldDefinition::getPosition));
    }

    public List<CustomFieldOption> getOptions(Long fieldId) {
        return optionService.getOptions(fieldId);
    }

    public List<Long> getProjectIds(Long fieldId) {
        return projectMapper.selectList(new LambdaQueryWrapper<CustomFieldProject>()
                .eq(CustomFieldProject::getCustomFieldId, fieldId))
                .stream().map(CustomFieldProject::getProjectId).toList();
    }

    public List<String> getIssueTypes(Long fieldId) {
        return issueTypeMapper.selectList(new LambdaQueryWrapper<CustomFieldIssueType>()
                .eq(CustomFieldIssueType::getCustomFieldId, fieldId))
                .stream().map(CustomFieldIssueType::getIssueType).toList();
    }

    public Map<Long, List<CustomFieldOption>> getBatchOptions(List<Long> fieldIds) {
        return optionService.getBatchOptions(fieldIds);
    }

    public Map<Long, List<Long>> getBatchProjectIds(List<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) return Map.of();
        List<CustomFieldProject> allMappings = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .in(CustomFieldProject::getCustomFieldId, fieldIds));
        return allMappings.stream()
                .collect(Collectors.groupingBy(
                        CustomFieldProject::getCustomFieldId,
                        Collectors.mapping(CustomFieldProject::getProjectId, Collectors.toList())));
    }

    public Map<Long, List<String>> getBatchIssueTypes(List<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) return Map.of();
        List<CustomFieldIssueType> allTypes = issueTypeMapper.selectList(
                new LambdaQueryWrapper<CustomFieldIssueType>()
                        .in(CustomFieldIssueType::getCustomFieldId, fieldIds));
        return allTypes.stream()
                .collect(Collectors.groupingBy(
                        CustomFieldIssueType::getCustomFieldId,
                        Collectors.mapping(CustomFieldIssueType::getIssueType, Collectors.toList())));
    }

    public CustomFieldDefinition getDefinitionById(Long id) {
        return definitionMapper.selectById(id);
    }

    public List<CustomFieldDefinition> listGlobalFields() {
        return definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getIsForAll, true)
                        .orderByAsc(CustomFieldDefinition::getPosition));
    }

    // ========== 角色/可见性 ==========

    public List<Long> getCurrentUserRoleIds(Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return List.of();
        if (permissionService.hasGlobalPermission(userId, "system:admin")) {
            return null;
        }
        return projectMemberMapper.selectRoleIdsByUserAndProject(userId, projectId);
    }

    public boolean isVisibleToUser(List<Long> restrictedRoles, List<Long> userRoleIds) {
        if (userRoleIds == null) return true;
        if (restrictedRoles == null || restrictedRoles.isEmpty()) return true;
        for (Long roleId : userRoleIds) {
            if (restrictedRoles.contains(roleId)) return true;
        }
        return false;
    }

    public boolean isUpdatableByUser(List<Long> updatableRoles, List<Long> userRoleIds) {
        return isVisibleToUser(updatableRoles, userRoleIds);
    }

    public List<CustomFieldDefinition> filterFieldsByVisibility(
            List<CustomFieldDefinition> fields,
            Map<Long, CustomFieldProject> conditionsMap,
            List<Long> userRoleIds) {
        return filterFieldsByVisibility(fields, conditionsMap, userRoleIds, null);
    }

    public List<CustomFieldDefinition> filterFieldsByVisibility(
            List<CustomFieldDefinition> fields,
            Map<Long, CustomFieldProject> conditionsMap,
            List<Long> userRoleIds,
            Long projectId) {
        if (userRoleIds == null) {
            // system admin — no filtering needed
            return fields;
        }
        List<CustomFieldDefinition> visible = new ArrayList<>();
        for (CustomFieldDefinition field : fields) {
            // Private field check: if field is private, user must have issue:read_private_fields permission
            if (Boolean.TRUE.equals(field.getIsPrivate())) {
                Long userId = SecurityUtils.getCurrentUserId();
                if (userId == null) {
                    continue; // skip private fields for unauthenticated users
                }
                // Note: userRoleIds != null means user is NOT system admin (already checked above)
                // so we need to check the specific permission
                if (!permissionService.hasPermission(userId, projectId, "issue:read_private_fields")) {
                    continue; // user lacks permission to read private fields
                }
            }
            CustomFieldProject mapping = conditionsMap.get(field.getId());
            List<Long> visibleRoles = mapping != null ? parseRoleIds(mapping.getVisibleToRoles()) : null;
            if (isVisibleToUser(visibleRoles, userRoleIds)) {
                visible.add(field);
            }
        }
        return visible;
    }

    // ========== VO 组装方法 ==========

    @Transactional(readOnly = true)
    public PageResult<CustomFieldDefinitionVO> listAdminPage(CustomFieldQuery query) {
        Page<CustomFieldDefinition> result = list(query.toPage(), query.getFieldFormat(), query.getKeyword());
        List<CustomFieldDefinitionVO> voList = converter.toVOList(result.getRecords());
        enrichAdminVOList(voList, result.getRecords());
        return new PageResult<>(voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @Transactional(readOnly = true)
    public CustomFieldDefinitionVO getFieldDetailVO(Long fieldId) {
        CustomFieldDefinition entity = getById(fieldId);
        CustomFieldDefinitionVO vo = converter.toVO(entity);
        enrichAdminVO(vo, fieldId);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomFieldDefinitionVO createAndReturnVO(CreateCustomFieldDTO dto) {
        CustomFieldDefinition entity = create(dto);
        return getFieldDetailVO(entity.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomFieldDefinitionVO updateAndReturnVO(Long id, UpdateCustomFieldDTO dto) {
        update(id, dto);
        return getFieldDetailVO(id);
    }

    public List<CustomFieldDefinitionVO> listEnumFieldsVO() {
        List<CustomFieldDefinition> fields = listEnumFields();
        List<CustomFieldDefinitionVO> voList = converter.toVOList(fields);
        List<Long> fieldIds = fields.stream().map(CustomFieldDefinition::getId).toList();
        Map<Long, List<CustomFieldOption>> optionsMap = getBatchOptions(fieldIds);
        for (int i = 0; i < fields.size(); i++) {
            Long fieldId = fields.get(i).getId();
            CustomFieldDefinitionVO vo = voList.get(i);
            vo.setIsBuiltIn(BUILTIN_FIELD_IDS.contains(fieldId));
            List<CustomFieldOptionVO> optVOs = converter.toOptionVOList(optionsMap.getOrDefault(fieldId, List.of()));
            enrichOptionOwnerDisplayNames(optVOs);
            vo.setOptions(optVOs);
        }
        return voList;
    }

    @Transactional(readOnly = true)
    public List<CustomFieldDefinitionVO> listByProjectForUser(Long projectId, String issueType) {
        List<CustomFieldDefinition> fields = listByProject(projectId, issueType);
        Map<Long, CustomFieldProject> conditionsMap = getProjectFieldConditions(projectId);
        List<Long> userRoleIds = getCurrentUserRoleIds(projectId);
        fields = filterFieldsByVisibility(fields, conditionsMap, userRoleIds, projectId);

        List<CustomFieldDefinitionVO> voList = converter.toVOList(fields);
        List<Long> fieldIds = fields.stream().map(CustomFieldDefinition::getId).toList();
        Map<Long, List<CustomFieldOption>> optionsMap = getBatchOptions(fieldIds);

        for (int i = 0; i < fields.size(); i++) {
            CustomFieldDefinition field = fields.get(i);
            Long fieldId = field.getId();
            CustomFieldDefinitionVO vo = voList.get(i);
            CustomFieldProject mapping = conditionsMap.get(fieldId);

            vo.setIsBuiltIn(BUILTIN_FIELD_IDS.contains(fieldId));
            List<CustomFieldOptionVO> optVOs = converter.toOptionVOList(optionsMap.getOrDefault(fieldId, List.of()));
            enrichOptionOwnerDisplayNames(optVOs);
            vo.setOptions(optVOs);
            enrichConditionInfo(vo, mapping);
            enrichEditableInfo(vo, mapping, userRoleIds);
            enrichBadgeConfig(vo, mapping);
            enrichEffectiveValues(vo, field, mapping);
        }
        return voList;
    }

    @Transactional(readOnly = true)
    public List<CustomFieldDefinitionVO> listProjectSettingsFieldsVO(Long projectId) {
        List<CustomFieldDefinition> fields = listProjectFields(projectId);
        List<CustomFieldDefinitionVO> voList = converter.toVOList(fields);
        Map<Long, CustomFieldProject> conditionsMap = getProjectFieldConditions(projectId);

        List<Long> fieldIds = fields.stream().map(CustomFieldDefinition::getId).toList();
        Map<Long, List<CustomFieldOption>> optionsMap = getBatchOptions(fieldIds);
        Map<Long, List<Long>> projectIdsMap = getBatchProjectIds(fieldIds);
        Map<Long, List<String>> issueTypesMap = getBatchIssueTypes(fieldIds);

        for (int i = 0; i < fields.size(); i++) {
            CustomFieldDefinition field = fields.get(i);
            Long fieldId = field.getId();
            CustomFieldDefinitionVO vo = voList.get(i);
            CustomFieldProject mapping = conditionsMap.get(fieldId);

            vo.setIsBuiltIn(BUILTIN_FIELD_IDS.contains(fieldId));
            List<CustomFieldOptionVO> optVOs = converter.toOptionVOList(optionsMap.getOrDefault(fieldId, List.of()));
            enrichOptionOwnerDisplayNames(optVOs);
            vo.setOptions(optVOs);
            vo.setProjectIds(projectIdsMap.getOrDefault(fieldId, List.of()).stream()
                    .map(String::valueOf).toList());
            vo.setIssueTypes(issueTypesMap.getOrDefault(fieldId, List.of()));
            enrichConditionInfo(vo, mapping);
            enrichVisibilityConfig(vo, mapping);
            enrichProjectOverride(vo, mapping);
            enrichBadgeConfig(vo, mapping);
            enrichEffectiveValues(vo, field, mapping);
        }
        return voList;
    }

    @Transactional(readOnly = true)
    public List<CustomFieldDefinitionVO> listAvailableForProjectVO(Long projectId) {
        List<CustomFieldDefinition> fields = listAvailableFieldsForProject(projectId);
        List<CustomFieldDefinitionVO> voList = converter.toVOList(fields);
        List<Long> fieldIds = fields.stream().map(CustomFieldDefinition::getId).toList();
        Map<Long, List<CustomFieldOption>> optionsMap = getBatchOptions(fieldIds);
        for (int i = 0; i < fields.size(); i++) {
            Long fieldId = fields.get(i).getId();
            CustomFieldDefinitionVO vo = voList.get(i);
            vo.setIsBuiltIn(BUILTIN_FIELD_IDS.contains(fieldId));
            vo.setOptions(converter.toOptionVOList(optionsMap.getOrDefault(fieldId, List.of())));
        }
        return voList;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 获取某项目中被排除的全局字段 ID 集合
     */
    private Set<Long> getExcludedFieldIds(Long projectId) {
        return projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .eq(CustomFieldProject::getIsExcluded, true))
                .stream()
                .map(CustomFieldProject::getCustomFieldId)
                .collect(Collectors.toSet());
    }

    private void cleanOrphanValuesForScopeReduction(Long customFieldId, List<Long> retainedProjectIds) {
        List<CustomFieldValue> existingValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .select(CustomFieldValue::getId, CustomFieldValue::getIssueId)
                        .eq(CustomFieldValue::getCustomFieldId, customFieldId));
        if (existingValues.isEmpty()) return;

        Set<Long> issueIds = existingValues.stream()
                .map(CustomFieldValue::getIssueId)
                .collect(Collectors.toSet());
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getId, Issue::getProjectId)
                        .in(Issue::getId, issueIds));
        Map<Long, Long> issueProjectMap = issues.stream()
                .collect(Collectors.toMap(Issue::getId, Issue::getProjectId));

        Set<Long> retainedSet = new HashSet<>(retainedProjectIds);
        List<Long> orphanValueIds = existingValues.stream()
                .filter(v -> {
                    Long projectId = issueProjectMap.get(v.getIssueId());
                    return projectId != null && !retainedSet.contains(projectId);
                })
                .map(CustomFieldValue::getId)
                .toList();

        if (orphanValueIds.isEmpty()) return;

        valueMapper.deleteByIds(orphanValueIds);
        log.info("Custom field {} scope reduced (isForAll: true→false): removed {} orphan values",
                customFieldId, orphanValueIds.size());
    }

    private List<AvailableColumnVO> buildStandardColumns() {
        List<AvailableColumnVO> columns = new ArrayList<>();
        columns.add(buildStandardColumn("issueKey", "编号", true, true));
        columns.add(buildStandardColumn("title", "标题", false, false));
        columns.add(buildStandardColumn("project", "项目", true, true));
        columns.add(buildStandardColumn("assignee", "负责人", true, true));
        columns.add(buildStandardColumn("status", "状态", true, true));
        columns.add(buildStandardColumn("sprint", "Sprint", true, true));
        columns.add(buildStandardColumn("priority", "优先级", true, true));
        columns.add(buildStandardColumn("issueType", "类型", true, true));
        columns.add(buildStandardColumn("reporter", "报告人", true, true));
        columns.add(buildStandardColumn("createdAt", "创建时间", true, true));
        columns.add(buildStandardColumn("updatedAt", "更新时间", true, true));
        columns.add(buildStandardColumn("dueDate", "截止日期", true, true));
        columns.add(buildStandardColumn("childProgress", "子任务进度", false, true));
        columns.add(buildStandardColumn("voteCount", "投票数", true, true));
        columns.add(buildStandardColumn("estimatedHours", "预估工时", true, true));
        columns.add(buildStandardColumn("spentHours", "已用工时", true, true));
        columns.add(buildStandardColumn("remaining", "剩余工时", true, true));
        return columns;
    }

    private AvailableColumnVO buildStandardColumn(String key, String label, boolean sortable, boolean removable) {
        AvailableColumnVO col = new AvailableColumnVO();
        col.setKey(key);
        col.setLabel(label);
        col.setGroup("standard");
        col.setFieldFormat(null);
        col.setSortable(sortable);
        col.setRemovable(removable);
        return col;
    }

    private AvailableColumnVO buildCustomColumn(CustomFieldDefinition field) {
        AvailableColumnVO col = new AvailableColumnVO();
        col.setKey("cf_" + field.getId());
        col.setLabel(field.getName());
        col.setGroup("custom");
        col.setFieldFormat(field.getFieldFormat());
        col.setSortable(isFieldSortable(field));
        col.setRemovable(true);
        return col;
    }

    private boolean isFieldSortable(CustomFieldDefinition field) {
        if (Boolean.TRUE.equals(field.getIsMulti())) {
            return false;
        }
        String format = field.getFieldFormat();
        return format != null && !format.equals("text") && !format.equals("bool");
    }

    private ProjectFieldsVO.FieldSummaryVO toFieldSummary(CustomFieldDefinition field, CustomFieldProject mapping) {
        ProjectFieldsVO.FieldSummaryVO vo = new ProjectFieldsVO.FieldSummaryVO();
        vo.setId(String.valueOf(field.getId()));
        vo.setName(field.getName());
        vo.setFieldFormat(field.getFieldFormat());
        vo.setIsForAll(field.getIsForAll());
        vo.setIsRequired(field.getIsRequired());
        vo.setIsMulti(field.getIsMulti());
        if (mapping != null) {
            vo.setProjectIsRequired(mapping.getIsRequired());
            vo.setProjectDefaultValue(mapping.getDefaultValue());
            vo.setProjectCanBeEmpty(mapping.getCanBeEmpty());
            vo.setPosition(mapping.getPosition());
            vo.setHasOverride(mapping.getIsRequired() != null || mapping.getDefaultValue() != null || mapping.getCanBeEmpty() != null);
        } else {
            vo.setHasOverride(false);
        }
        return vo;
    }

    private void enrichAdminVOList(List<CustomFieldDefinitionVO> voList, List<CustomFieldDefinition> entities) {
        List<Long> fieldIds = entities.stream().map(CustomFieldDefinition::getId).toList();
        Map<Long, List<CustomFieldOption>> optionsMap = getBatchOptions(fieldIds);
        Map<Long, List<Long>> projectIdsMap = getBatchProjectIds(fieldIds);
        Map<Long, List<String>> issueTypesMap = getBatchIssueTypes(fieldIds);
        for (int i = 0; i < entities.size(); i++) {
            Long fieldId = entities.get(i).getId();
            CustomFieldDefinitionVO vo = voList.get(i);
            vo.setIsBuiltIn(BUILTIN_FIELD_IDS.contains(fieldId));
            List<CustomFieldOptionVO> optVOs = converter.toOptionVOList(optionsMap.getOrDefault(fieldId, List.of()));
            enrichOptionOwnerDisplayNames(optVOs);
            vo.setOptions(optVOs);
            vo.setProjectIds(projectIdsMap.getOrDefault(fieldId, List.of()).stream()
                    .map(String::valueOf).toList());
            vo.setIssueTypes(issueTypesMap.getOrDefault(fieldId, List.of()));
        }
    }

    private void enrichAdminVO(CustomFieldDefinitionVO vo, Long fieldId) {
        vo.setIsBuiltIn(BUILTIN_FIELD_IDS.contains(fieldId));
        List<CustomFieldOptionVO> optVOs = converter.toOptionVOList(getOptions(fieldId));
        enrichOptionOwnerDisplayNames(optVOs);
        vo.setOptions(optVOs);
        vo.setProjectIds(getProjectIds(fieldId).stream().map(String::valueOf).toList());
        vo.setIssueTypes(getIssueTypes(fieldId));
    }

    /**
     * 为选项 VO 列表填充 ownerDisplayName。
     * 收集所有非空 ownerUserId，批量查询用户信息，设置显示名。
     */
    private void enrichOptionOwnerDisplayNames(List<CustomFieldOptionVO> optionVOs) {
        if (optionVOs == null || optionVOs.isEmpty()) return;
        Set<Long> ownerIds = optionVOs.stream()
                .filter(o -> o.getOwnerUserId() != null && !o.getOwnerUserId().isBlank())
                .map(o -> Long.parseLong(o.getOwnerUserId()))
                .collect(Collectors.toSet());
        if (ownerIds.isEmpty()) return;
        Map<Long, String> userNameMap = sysUserMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName));
        for (CustomFieldOptionVO vo : optionVOs) {
            if (vo.getOwnerUserId() != null && !vo.getOwnerUserId().isBlank()) {
                Long uid = Long.parseLong(vo.getOwnerUserId());
                vo.setOwnerDisplayName(userNameMap.get(uid));
            }
        }
    }

    private void enrichConditionInfo(CustomFieldDefinitionVO vo, CustomFieldProject mapping) {
        if (mapping != null && mapping.getConditionFieldId() != null) {
            vo.setConditionFieldId(String.valueOf(mapping.getConditionFieldId()));
            vo.setConditionValues(parseJsonArray(mapping.getConditionValues()));
        }
    }

    private void enrichEditableInfo(CustomFieldDefinitionVO vo, CustomFieldProject mapping, List<Long> userRoleIds) {
        if (mapping != null) {
            List<Long> updatableRoles = parseRoleIds(mapping.getUpdatableByRoles());
            vo.setEditable(isUpdatableByUser(updatableRoles, userRoleIds));
            vo.setVisibleToRoles(parseRoleIds(mapping.getVisibleToRoles()));
            vo.setUpdatableByRoles(updatableRoles);
        } else {
            vo.setEditable(true);
        }
    }

    private void enrichVisibilityConfig(CustomFieldDefinitionVO vo, CustomFieldProject mapping) {
        if (mapping != null) {
            vo.setVisibleToRoles(parseRoleIds(mapping.getVisibleToRoles()));
            vo.setUpdatableByRoles(parseRoleIds(mapping.getUpdatableByRoles()));
        }
    }

    private void enrichBadgeConfig(CustomFieldDefinitionVO vo, CustomFieldProject mapping) {
        if (mapping != null) {
            vo.setShowAsBadge(Boolean.TRUE.equals(mapping.getShowAsBadge()));
            vo.setBadgeColorRules(mapping.getBadgeColorRules());
        } else {
            vo.setShowAsBadge(false);
        }
    }

    private void enrichProjectOverride(CustomFieldDefinitionVO vo, CustomFieldProject mapping) {
        if (mapping != null) {
            vo.setProjectIsRequired(mapping.getIsRequired());
            vo.setProjectDefaultValue(mapping.getDefaultValue());
            vo.setProjectCanBeEmpty(mapping.getCanBeEmpty());
        }
    }

    private void enrichEffectiveValues(CustomFieldDefinitionVO vo, CustomFieldDefinition field, CustomFieldProject mapping) {
        vo.setEffectiveIsRequired(mapping != null && mapping.getIsRequired() != null
                ? mapping.getIsRequired() : field.getIsRequired());
        
        // 计算有效默认值
        // 1. 优先使用项目级配置的默认值
        // 2. 如果项目级没有配置，使用字段级默认值
        // 3. 对于 list 类型字段，还需要检查选项表中 isDefault=true 的选项
        String effectiveDefault = (mapping != null && mapping.getDefaultValue() != null)
                ? (mapping.getDefaultValue().isEmpty() ? null : mapping.getDefaultValue())
                : field.getDefaultValue();
        
        // 对于 list/ownedField/version/state 类型字段，如果默认值不是数字 ID，
        // 尝试通过选项名称匹配转换为选项 ID（防御性处理旧数据或误配置）
        boolean isListType = "list".equals(field.getFieldFormat()) || "ownedField".equals(field.getFieldFormat())
                || "version".equals(field.getFieldFormat()) || "state".equals(field.getFieldFormat());
        if (isListType && effectiveDefault != null && !effectiveDefault.isBlank() && !effectiveDefault.matches("^\\d+$")) {
            // 默认值是文本名称而非数字 ID，尝试按名称查找选项
            CustomFieldOption matchedOption = optionMapper.selectOne(
                    new LambdaQueryWrapper<CustomFieldOption>()
                            .eq(CustomFieldOption::getCustomFieldId, field.getId())
                            .eq(CustomFieldOption::getValue, effectiveDefault)
                            .eq(CustomFieldOption::getIsArchived, false)
                            .last("LIMIT 1"));
            if (matchedOption != null) {
                effectiveDefault = String.valueOf(matchedOption.getId());
                log.debug("自定义字段 {} 默认值从文本 '{}' 解析为选项 ID: {}", field.getId(), field.getDefaultValue(), effectiveDefault);
            } else {
                // 文本名无法匹配到选项，清空默认值以防止验证失败
                log.warn("自定义字段 {} 默认值 '{}' 无法匹配到有效选项，已忽略", field.getId(), effectiveDefault);
                effectiveDefault = null;
            }
        }
        
        // 对于 list/ownedField 类型字段，如果没有显式默认值，检查选项表中是否有 isDefault=true 的选项
        if ((effectiveDefault == null || effectiveDefault.isBlank()) && ("list".equals(field.getFieldFormat()) || "ownedField".equals(field.getFieldFormat()))) {
            List<CustomFieldOption> defaultOptions = optionMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldOption>()
                            .eq(CustomFieldOption::getCustomFieldId, field.getId())
                            .eq(CustomFieldOption::getIsDefault, true)
                            .eq(CustomFieldOption::getIsArchived, false));
            if (!defaultOptions.isEmpty()) {
                if (defaultOptions.size() > 1) {
                    log.warn("自定义字段 {} 有 {} 个默认选项（数据异常），取第一个: {}",
                            field.getId(), defaultOptions.size(), defaultOptions.get(0).getId());
                }
                // 有默认选项 → effectiveDefault 设置为选项 ID
                effectiveDefault = String.valueOf(defaultOptions.get(0).getId());
            }
        }
        vo.setEffectiveDefaultValue(effectiveDefault);
        
        // 计算"是否允许为空"的有效值
        // canBeEmpty 默认为 true（可以为空）
        Boolean effectiveCanBeEmpty = (mapping != null && mapping.getCanBeEmpty() != null)
                ? mapping.getCanBeEmpty() : true;
        vo.setEffectiveCanBeEmpty(effectiveCanBeEmpty);
        
        // 如果 canBeEmpty=false，则字段必填（补充设置 effectiveIsRequired）
        if (Boolean.FALSE.equals(effectiveCanBeEmpty)) {
            vo.setEffectiveIsRequired(true);
        }
        
        // 判断是否为"无默认值但必填"模式
        // 条件：canBeEmpty=false 且 没有有效默认值（包括无选项默认值）
        // 此模式下前端应显示 "Set value" 提示
        boolean requiresExplicitSelection = Boolean.FALSE.equals(effectiveCanBeEmpty) 
                && (effectiveDefault == null || effectiveDefault.isBlank());
        vo.setRequiresExplicitSelection(requiresExplicitSelection);
    }

    // ========== JSON 辅助 ==========

    public List<Long> parseRoleIds(String json) {
        if (json == null || json.isBlank()) return null;
        List<String> raw = parseJsonArray(json);
        if (raw.isEmpty()) return null;
        return raw.stream().map(Long::parseLong).toList();
    }

    public List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return List.of();
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        if (inner.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String part : inner.split(",")) {
            String val = part.trim();
            if (val.startsWith("\"") && val.endsWith("\"")) {
                val = val.substring(1, val.length() - 1);
            }
            if (!val.isEmpty()) {
                result.add(val);
            }
        }
        return result;
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(values.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 从适用字段列表中排除 State 内置字段。
     * State 字段数据由工作流引擎管理（issue.status_id），不经过 custom_field_value 表。
     */
    private List<CustomFieldDefinition> excludeStateField(List<CustomFieldDefinition> fields) {
        return fields.stream()
                .filter(f -> !STATE_FIELD_ID.equals(f.getId()))
                .collect(Collectors.toList());
    }

    /**
     * 从用户提交的字段值 Map 中排除 State 字段（静默忽略，避免写入 custom_field_value）。
     */
    private Map<Long, String> excludeStateFieldValue(Map<Long, String> fieldValues) {
        if (fieldValues == null || !fieldValues.containsKey(STATE_FIELD_ID)) {
            return fieldValues;
        }
        Map<Long, String> filtered = new HashMap<>(fieldValues);
        filtered.remove(STATE_FIELD_ID);
        return filtered;
    }

    private String roleIdsToJson(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return null;
        return "[" + roleIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }
}
