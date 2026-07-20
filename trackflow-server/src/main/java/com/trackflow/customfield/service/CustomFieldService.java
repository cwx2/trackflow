package com.trackflow.customfield.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.customfield.dto.CreateCustomFieldDTO;
import com.trackflow.customfield.dto.UpdateCustomFieldDTO;
import com.trackflow.customfield.entity.*;
import com.trackflow.customfield.mapper.*;
import com.trackflow.customfield.vo.AvailableColumnVO;
import com.trackflow.customfield.vo.CustomFieldUsageVO;
import com.trackflow.customfield.vo.CustomFieldValueVO;
import com.trackflow.customfield.vo.ProjectFieldsVO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectStatus;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service

@RequiredArgsConstructor
public class CustomFieldService {

    private final CustomFieldDefinitionMapper definitionMapper;
    private final CustomFieldOptionMapper optionMapper;
    private final CustomFieldValueMapper valueMapper;
    private final CustomFieldProjectMapper projectMapper;
    private final CustomFieldIssueTypeMapper issueTypeMapper;
    private final CustomFieldValidationEngine validationEngine;
    private final SysUserMapper userMapper;
    private final IssueActivityMapper activityMapper;
    private final IssueMapper issueMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMapper projectEntityMapper;
    private final PermissionService permissionService;

    @Transactional
    public CustomFieldDefinition create(CreateCustomFieldDTO dto) {
        if (!CustomFieldValidationEngine.SUPPORTED_FORMATS.contains(dto.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的字段类型: " + dto.getFieldFormat());
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
        entity.setIsMulti("list".equals(dto.getFieldFormat()) && Boolean.TRUE.equals(dto.getIsMulti()));
        entity.setIsHiddenInList(Boolean.TRUE.equals(dto.getIsHiddenInList()));
        try {
            definitionMapper.insert(entity);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("uk_custom_field_name")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "自定义字段名称已存在");
            }
            throw ex;
        }

        if ("list".equals(dto.getFieldFormat())) {
            if (dto.getCopyOptionsFromFieldId() != null && (dto.getOptions() == null || dto.getOptions().isEmpty())) {
                // 从已有字段复制选项
                copyOptionsFromField(entity.getId(), dto.getCopyOptionsFromFieldId());
            } else if (dto.getOptions() != null) {
                for (int i = 0; i < dto.getOptions().size(); i++) {
                    CreateCustomFieldDTO.OptionItem opt = dto.getOptions().get(i);
                    CustomFieldOption option = new CustomFieldOption();
                    option.setCustomFieldId(entity.getId());
                    option.setValue(opt.getValue());
                    option.setPosition(i);
                    option.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
                    option.setColor(opt.getColor());
                    option.setCreatedAt(LocalDateTime.now());
                    option.setUpdatedAt(LocalDateTime.now());
                    optionMapper.insert(option);
                }
            }
        }

        if (!Boolean.TRUE.equals(dto.getIsForAll()) && dto.getProjectIds() != null) {
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

    @Transactional
    public CustomFieldDefinition update(Long id, UpdateCustomFieldDTO dto) {
        CustomFieldDefinition entity = definitionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "自定义字段不存在");
        }

        // 记录 isForAll 变更前状态，用于检测 true→false 转换
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
        if (dto.getIsMulti() != null && "list".equals(entity.getFieldFormat())) {
            // 检测 isMulti 是否实际变更
            boolean currentIsMulti = Boolean.TRUE.equals(entity.getIsMulti());
            if (dto.getIsMulti() != currentIsMulti) {
                // 如果字段已有值数据，禁止切换单选/多选模式（参考 YouTrack：创建后不允许切换）
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
        try {
            definitionMapper.updateById(entity);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("uk_custom_field_name")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "自定义字段名称已存在");
            }
            throw ex;
        }

        if ("list".equals(entity.getFieldFormat()) && dto.getOptions() != null) {
            updateListOptions(id, dto.getOptions());
        }

        // 从其他字段追加选项（仅当 options 未提供时触发复制）
        if ("list".equals(entity.getFieldFormat()) && dto.getCopyOptionsFromFieldId() != null && dto.getOptions() == null) {
            appendOptionsFromField(id, dto.getCopyOptionsFromFieldId());
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

        // isForAll 从 true→false 时，清理不再适用项目中的孤立字段值
        boolean isNowForAll = Boolean.TRUE.equals(entity.getIsForAll());
        if (wasForAll && !isNowForAll) {
            // 确定当前保留的项目列表：
            // - 如果 dto 提供了 projectIds，使用它（已在上面更新到数据库）
            // - 如果 dto 未提供，则从数据库读取当前关联
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

    /**
     * 当字段从全局（isForAll=true）收缩为项目专属（isForAll=false）时，
     * 清理不再适用项目中工单的字段值。
     *
     * @param customFieldId 自定义字段 ID
     * @param retainedProjectIds 仍然关联的项目 ID 列表
     */
    private void cleanOrphanValuesForScopeReduction(Long customFieldId, List<Long> retainedProjectIds) {
        // 查出当前有该字段值的所有 issue ID
        List<CustomFieldValue> existingValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .select(CustomFieldValue::getId, CustomFieldValue::getIssueId)
                        .eq(CustomFieldValue::getCustomFieldId, customFieldId));
        if (existingValues.isEmpty()) return;

        // 获取涉及的 issue 对应的 projectId
        Set<Long> issueIds = existingValues.stream()
                .map(CustomFieldValue::getIssueId)
                .collect(Collectors.toSet());
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getId, Issue::getProjectId)
                        .in(Issue::getId, issueIds));
        Map<Long, Long> issueProjectMap = issues.stream()
                .collect(Collectors.toMap(Issue::getId, Issue::getProjectId));

        // 找出属于"不再适用"项目的字段值记录 ID
        Set<Long> retainedSet = new HashSet<>(retainedProjectIds);
        List<Long> orphanValueIds = existingValues.stream()
                .filter(v -> {
                    Long projectId = issueProjectMap.get(v.getIssueId());
                    return projectId != null && !retainedSet.contains(projectId);
                })
                .map(CustomFieldValue::getId)
                .toList();

        if (orphanValueIds.isEmpty()) return;

        // 批量删除孤立值
        valueMapper.deleteByIds(orphanValueIds);

        log.info("Custom field {} scope reduced (isForAll: true→false): removed {} orphan values from {} issues in non-retained projects",
                customFieldId, orphanValueIds.size(), orphanValueIds.size());
    }

    /**
     * 获取单个自定义字段详情。
     */
    public CustomFieldDefinition getById(Long id) {
        CustomFieldDefinition entity = definitionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "自定义字段不存在");
        }
        return entity;
    }

    /**
     * 获取自定义字段的使用情况统计。
     * 用于删除前展示影响范围。
     */
    public CustomFieldUsageVO getUsage(Long id) {
        if (definitionMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "自定义字段不存在");
        }

        CustomFieldUsageVO usage = new CustomFieldUsageVO();

        // 有值记录的工单数量（通过 distinct issue_id 统计）
        List<Object> issueIds = valueMapper.selectObjs(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .select(CustomFieldValue::getIssueId)
                        .eq(CustomFieldValue::getCustomFieldId, id)
                        .groupBy(CustomFieldValue::getIssueId));
        usage.setIssueCount(issueIds.size());

        usage.setValueCount(valueMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getCustomFieldId, id)));

        usage.setProjectCount(projectMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, id)));

        usage.setIssueTypeCount(issueTypeMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldIssueType>()
                        .eq(CustomFieldIssueType::getCustomFieldId, id)));

        usage.setOptionCount(optionMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, id)));

        return usage;
    }

    /**
     * 删除自定义字段。
     * 如果有工单引用且未确认（confirm=false），抛出业务异常返回影响数据。
     * confirm=true 时强制删除。
     */
    @Transactional
    public void delete(Long id, boolean confirm) {
        if (definitionMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "自定义字段不存在");
        }

        if (!confirm) {
            long valueCount = valueMapper.selectCount(
                    new LambdaQueryWrapper<CustomFieldValue>()
                            .eq(CustomFieldValue::getCustomFieldId, id));
            if (valueCount > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "此字段被工单引用，请使用 confirm=true 确认删除");
            }
        }

        definitionMapper.deleteById(id);
    }

    @Transactional
    public void reorder(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            CustomFieldDefinition entity = new CustomFieldDefinition();
            entity.setId(ids.get(i));
            entity.setPosition(i);
            definitionMapper.updateById(entity);
        }
    }

    /**
     * 更新列表类型字段的选项（保持选项 ID 稳定性）。
     * <p>
     * 策略：
     * - DTO 中有 id 的选项 → UPDATE（更新 value/position/isDefault）
     * - DTO 中无 id 的选项 → INSERT（新增）
     * - DB 中存在但 DTO 中不存在的选项 → 检查是否被引用：有引用则归档，无引用则物理删除
     * <p>
     * 参考 OpenProject: app/models/custom_field.rb#possible_values=
     */
    private void updateListOptions(Long customFieldId, List<UpdateCustomFieldDTO.OptionItem> dtoOptions) {
        // 1. 加载当前数据库中所有选项
        List<CustomFieldOption> existingOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, customFieldId));
        Map<Long, CustomFieldOption> existingMap = existingOptions.stream()
                .collect(Collectors.toMap(CustomFieldOption::getId, o -> o));

        // 2. 收集 DTO 中引用了已有 ID 的集合
        Set<Long> dtoReferencedIds = dtoOptions.stream()
                .map(UpdateCustomFieldDTO.OptionItem::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. 处理 DTO 中的每个选项
        // 收集本次更新后的活跃选项值（用于后续清理同名归档条目）
        Set<String> activeValues = new HashSet<>();
        // 跟踪被复用（重新激活）的归档选项 ID，这些不应在 step 4 中被重新归档
        Set<Long> reactivatedIds = new HashSet<>();
        for (int i = 0; i < dtoOptions.size(); i++) {
            UpdateCustomFieldDTO.OptionItem opt = dtoOptions.get(i);
            if (opt.getId() != null) {
                // 已有选项 → UPDATE
                CustomFieldOption existing = existingMap.get(opt.getId());
                if (existing == null) {
                    // ID 无效，当作新增处理
                    Long reactivatedId = insertOrReactivateOption(customFieldId, opt, i, existingOptions);
                    if (reactivatedId != null) reactivatedIds.add(reactivatedId);
                } else {
                    existing.setValue(opt.getValue());
                    existing.setPosition(i);
                    existing.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
                    existing.setColor(opt.getColor());
                    existing.setIsArchived(false); // 如果之前被归档，恢复
                    existing.setUpdatedAt(LocalDateTime.now());
                    optionMapper.updateById(existing);
                }
            } else {
                // 新选项 → 优先复用同名归档选项，避免产生重复
                Long reactivatedId = insertOrReactivateOption(customFieldId, opt, i, existingOptions);
                if (reactivatedId != null) reactivatedIds.add(reactivatedId);
            }
            activeValues.add(opt.getValue());
        }

        // 4. 处理 DB 中存在但 DTO 中不存在的选项（被移除的）
        // 排除被 step 3 复用重新激活的选项——这些虽然不在 dtoReferencedIds 中（DTO 没传 ID），
        // 但已被成功匹配复用，不应该再被归档或删除
        Set<Long> removedIds = existingMap.keySet().stream()
                .filter(id -> !dtoReferencedIds.contains(id) && !reactivatedIds.contains(id))
                .collect(Collectors.toSet());

        for (Long removedId : removedIds) {
            // 检查该选项是否被工单引用
            boolean isReferenced = isOptionReferenced(customFieldId, removedId);
            if (isReferenced) {
                // 有引用 → 归档（保留数据，标记为不可选）
                CustomFieldOption archived = existingMap.get(removedId);
                archived.setIsArchived(true);
                archived.setPosition(Integer.MAX_VALUE); // 归档选项排最后
                archived.setUpdatedAt(LocalDateTime.now());
                optionMapper.updateById(archived);
                log.info("Custom field option {} archived (referenced by issues), fieldId={}", removedId, customFieldId);
            } else {
                // 无引用 → 物理删除
                optionMapper.deleteById(removedId);
            }
        }

        // 5. 清理同名归档残留：如果一个归档选项的 value 与某个活跃选项相同，
        //    且该归档选项未被工单引用，则物理删除（避免无意义的重复条目）
        cleanupSupersededArchivedOptions(customFieldId, activeValues);
    }

    /**
     * 清理被活跃选项替代的归档残留条目。
     * 当归档选项的 value 与活跃选项相同且未被工单引用时，物理删除归档条目。
     */
    private void cleanupSupersededArchivedOptions(Long customFieldId, Set<String> activeValues) {
        if (activeValues.isEmpty()) return;

        List<CustomFieldOption> archivedOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, customFieldId)
                        .eq(CustomFieldOption::getIsArchived, true));

        for (CustomFieldOption archived : archivedOptions) {
            if (activeValues.contains(archived.getValue())) {
                // 同名活跃选项已存在，检查归档条目是否还被工单引用
                if (!isOptionReferenced(customFieldId, archived.getId())) {
                    // 无引用 → 安全删除残留
                    optionMapper.deleteById(archived.getId());
                    log.info("Cleaned up superseded archived option {} (value='{}'), fieldId={}",
                            archived.getId(), archived.getValue(), customFieldId);
                }
                // 如果仍被引用，保留归档条目（工单需要通过 ID 解析值）
            }
        }
    }

    /**
     * 插入新选项或复用同名已归档选项。
     * 当存在同名归档选项时，复用其 ID（保留工单引用），更新属性并取消归档。
     * 这避免了"全删重建"策略产生的同名活跃+归档重复条目。
     *
     * @return 被复用的归档选项 ID（如果发生了复用），否则返回 null
     */
    private Long insertOrReactivateOption(Long customFieldId, UpdateCustomFieldDTO.OptionItem opt,
                                           int position, List<CustomFieldOption> existingOptions) {
        // 查找同名已归档选项
        CustomFieldOption archivedSameName = existingOptions.stream()
                .filter(o -> o.getIsArchived() && o.getValue().equals(opt.getValue()))
                .findFirst()
                .orElse(null);

        if (archivedSameName != null) {
            // 复用归档选项：更新属性并取消归档
            archivedSameName.setPosition(position);
            archivedSameName.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
            archivedSameName.setColor(opt.getColor());
            archivedSameName.setIsArchived(false);
            archivedSameName.setUpdatedAt(LocalDateTime.now());
            optionMapper.updateById(archivedSameName);
            log.info("Reactivated archived option {} (value='{}') instead of creating duplicate, fieldId={}",
                    archivedSameName.getId(), opt.getValue(), customFieldId);
            return archivedSameName.getId();
        } else {
            // 真正的新值 → 插入新记录
            CustomFieldOption option = new CustomFieldOption();
            option.setCustomFieldId(customFieldId);
            option.setValue(opt.getValue());
            option.setPosition(position);
            option.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
            option.setColor(opt.getColor());
            option.setIsArchived(false);
            option.setCreatedAt(LocalDateTime.now());
            option.setUpdatedAt(LocalDateTime.now());
            optionMapper.insert(option);
            return null;
        }
    }

    /**
     * 检查指定选项 ID 是否被任何工单的 custom_field_value 引用。
     * list 字段值存储方式：单选存 option ID 字符串，多选存逗号分隔的 ID 列表。
     */
    /**
     * 从已有字段复制全部活跃选项到新字段（创建时使用）。
     * 参考 YouTrack "Existing set" 功能——创建独立副本。
     *
     * @param targetFieldId 目标字段 ID（刚创建的字段）
     * @param sourceFieldId 源字段 ID（复制选项来源）
     */
    private void copyOptionsFromField(Long targetFieldId, Long sourceFieldId) {
        CustomFieldDefinition sourceField = definitionMapper.selectById(sourceFieldId);
        if (sourceField == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源字段不存在");
        }
        if (!"list".equals(sourceField.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源字段不是枚举类型，无法复制选项");
        }

        // 获取源字段所有活跃选项
        List<CustomFieldOption> sourceOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, sourceFieldId)
                        .eq(CustomFieldOption::getIsArchived, false)
                        .orderByAsc(CustomFieldOption::getPosition));

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < sourceOptions.size(); i++) {
            CustomFieldOption src = sourceOptions.get(i);
            CustomFieldOption copy = new CustomFieldOption();
            copy.setCustomFieldId(targetFieldId);
            copy.setValue(src.getValue());
            copy.setPosition(i);
            copy.setIsDefault(src.getIsDefault());
            copy.setColor(src.getColor());
            copy.setIsArchived(false);
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            optionMapper.insert(copy);
        }

        log.info("Copied {} options from field {} to new field {}", sourceOptions.size(), sourceFieldId, targetFieldId);
    }

    /**
     * 从已有字段追加选项到当前字段（编辑时使用，"Copy values from" 功能）。
     * 跳过已存在的同名选项，只追加新选项。
     *
     * @param targetFieldId 目标字段 ID
     * @param sourceFieldId 源字段 ID
     */
    private void appendOptionsFromField(Long targetFieldId, Long sourceFieldId) {
        CustomFieldDefinition sourceField = definitionMapper.selectById(sourceFieldId);
        if (sourceField == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源字段不存在");
        }
        if (!"list".equals(sourceField.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源字段不是枚举类型，无法复制选项");
        }

        // 获取目标字段已有选项值（用于去重）
        List<CustomFieldOption> existingOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, targetFieldId));
        Set<String> existingValues = existingOptions.stream()
                .map(CustomFieldOption::getValue)
                .collect(Collectors.toSet());

        // 获取当前最大 position
        int maxPosition = existingOptions.stream()
                .filter(o -> !o.getIsArchived())
                .mapToInt(CustomFieldOption::getPosition)
                .max()
                .orElse(-1);

        // 获取源字段活跃选项
        List<CustomFieldOption> sourceOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, sourceFieldId)
                        .eq(CustomFieldOption::getIsArchived, false)
                        .orderByAsc(CustomFieldOption::getPosition));

        LocalDateTime now = LocalDateTime.now();
        int added = 0;
        for (CustomFieldOption src : sourceOptions) {
            if (existingValues.contains(src.getValue())) {
                continue; // 跳过同名选项
            }
            maxPosition++;
            CustomFieldOption copy = new CustomFieldOption();
            copy.setCustomFieldId(targetFieldId);
            copy.setValue(src.getValue());
            copy.setPosition(maxPosition);
            copy.setIsDefault(false); // 追加的选项不设为默认
            copy.setColor(src.getColor());
            copy.setIsArchived(false);
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            optionMapper.insert(copy);
            added++;
        }

        log.info("Appended {} options from field {} to field {} (skipped {} duplicates)",
                added, sourceFieldId, targetFieldId, sourceOptions.size() - added);
    }

    private boolean isOptionReferenced(Long customFieldId, Long optionId) {
        String idStr = String.valueOf(optionId);
        // 精确匹配单选值，或者作为多选中的一部分
        // 用 SQL LIKE 检查：value = 'id' OR value LIKE 'id,%' OR value LIKE '%,id,%' OR value LIKE '%,id'
        Long count = valueMapper.selectCount(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getCustomFieldId, customFieldId)
                .and(w -> w
                        .eq(CustomFieldValue::getValue, idStr)
                        .or().likeRight(CustomFieldValue::getValue, idStr + ",")
                        .or().like(CustomFieldValue::getValue, "," + idStr + ",")
                        .or().likeLeft(CustomFieldValue::getValue, "," + idStr)
                ));
        return count != null && count > 0;
    }

    public List<CustomFieldDefinition> listByProject(Long projectId, String issueType) {
        List<CustomFieldDefinition> globalFields = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getIsForAll, true)
                        .orderByAsc(CustomFieldDefinition::getPosition));

        List<Long> projectFieldIds = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId))
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
            Set<Long> typeRestricted = issueTypeMapper.selectList(null).stream()
                    .collect(Collectors.groupingBy(CustomFieldIssueType::getCustomFieldId,
                            Collectors.mapping(CustomFieldIssueType::getIssueType, Collectors.toSet())))
                    .entrySet().stream()
                    .filter(e -> !e.getValue().contains(issueType))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            merged.entrySet().removeIf(e -> typeRestricted.contains(e.getKey()));
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * 获取所有可用列（内置 + 所有自定义字段），不需要 projectId
     */
    public List<AvailableColumnVO> getAllAvailableColumns() {
        List<AvailableColumnVO> columns = new ArrayList<>();

        // 内置属性列
        columns.add(buildStandardColumn("issueKey", "编号", true, true));
        columns.add(buildStandardColumn("title", "标题", false, false)); // 不可移除
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

        // 所有自定义字段（排除管理员设为隐藏的）
        List<CustomFieldDefinition> allFields = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getIsHiddenInList, false)
                        .orderByAsc(CustomFieldDefinition::getPosition));
        for (CustomFieldDefinition field : allFields) {
            AvailableColumnVO col = new AvailableColumnVO();
            col.setKey("cf_" + field.getId());
            col.setLabel(field.getName());
            col.setGroup("custom");
            col.setFieldFormat(field.getFieldFormat());
            col.setSortable(true);
            col.setRemovable(true);
            columns.add(col);
        }

        return columns;
    }

    public List<AvailableColumnVO> getAvailableColumns(Long projectId) {
        List<AvailableColumnVO> columns = new ArrayList<>();

        columns.add(buildStandardColumn("issueKey", "编号", true, true));
        columns.add(buildStandardColumn("title", "标题", false, false));
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

        List<CustomFieldDefinition> fields = listByProject(projectId, null);
        for (CustomFieldDefinition field : fields) {
            // 跳过管理员设为隐藏的字段
            if (Boolean.TRUE.equals(field.getIsHiddenInList())) continue;
            AvailableColumnVO col = new AvailableColumnVO();
            col.setKey("cf_" + field.getId());
            col.setLabel(field.getName());
            col.setGroup("custom");
            col.setFieldFormat(field.getFieldFormat());
            col.setSortable(true);
            col.setRemovable(true);
            columns.add(col);
        }

        return columns;
    }

    /**
     * 为创建工单场景应用默认值并校验必填字段。
     * <p>
     * 流程：
     * 1. 获取项目+类型下所有适用字段
     * 2. 对用户未提供值的字段：
     *    - 有 defaultValue → 填充
     *    - 是 list 类型且有 is_default=true 的选项 → 填充选项 ID
     * 3. 对必填字段：用户未提供且无默认值 → 抛出异常
     * 4. 返回合并后的 fieldValues（含默认值），供 saveValues 使用
     *
     * @param userProvided 用户显式提供的字段值（可为 null 或空）
     * @param issueType    工单类型
     * @param projectId    项目ID
     * @return 合并默认值后的字段值 Map（字段ID → 值）
     */
    public Map<Long, String> applyDefaultsAndValidate(Map<Long, String> userProvided, String issueType, Long projectId) {
        List<CustomFieldDefinition> applicableFields = listByProject(projectId, issueType);
        if (applicableFields.isEmpty()) {
            return userProvided != null ? userProvided : new HashMap<>();
        }

        // Load project-level overrides
        Map<Long, CustomFieldProject> projectOverrides = getProjectFieldConditions(projectId);

        Map<Long, String> merged = new HashMap<>();
        if (userProvided != null) {
            merged.putAll(userProvided);
        }

        List<CustomFieldValidationEngine.FieldValidationError> errors = new ArrayList<>();

        for (CustomFieldDefinition field : applicableFields) {
            if (merged.containsKey(field.getId())) {
                // 用户已提供值，跳过默认值填充
                continue;
            }

            // 尝试应用默认值（项目级优先）
            String defaultVal = resolveDefaultValueWithOverride(field, projectOverrides.get(field.getId()));
            if (defaultVal != null && !defaultVal.isBlank()) {
                merged.put(field.getId(), defaultVal);
            } else if (isFieldRequired(field, projectOverrides.get(field.getId()))) {
                // 必填字段无默认值且用户未提供 → 报错
                errors.add(new CustomFieldValidationEngine.FieldValidationError(
                        field.getName(), "此字段为必填项"));
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "自定义字段验证失败: " + errors.stream()
                            .map(e -> e.getField() + ": " + e.getMessage())
                            .collect(Collectors.joining("; ")));
        }

        return merged;
    }

    /**
     * 解析字段的默认值。
     * - list 类型：查找 is_default=true 且未归档的选项，返回选项 ID
     * - 其他类型：返回 defaultValue 字段值
     */
    private String resolveDefaultValue(CustomFieldDefinition field) {
        if ("list".equals(field.getFieldFormat())) {
            // list 类型：查找 is_default=true 的未归档选项
            List<CustomFieldOption> defaultOptions = optionMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldOption>()
                            .eq(CustomFieldOption::getCustomFieldId, field.getId())
                            .eq(CustomFieldOption::getIsDefault, true)
                            .eq(CustomFieldOption::getIsArchived, false));
            if (!defaultOptions.isEmpty()) {
                if (Boolean.TRUE.equals(field.getIsMulti())) {
                    // 多值列表：返回逗号分隔的选项 ID
                    return defaultOptions.stream()
                            .map(o -> String.valueOf(o.getId()))
                            .collect(Collectors.joining(","));
                } else {
                    // 单值列表：返回第一个默认选项 ID
                    return String.valueOf(defaultOptions.get(0).getId());
                }
            }
            return null;
        } else {
            // 非 list 类型：直接返回 defaultValue
            return field.getDefaultValue();
        }
    }

    /**
     * 解析字段默认值（优先使用项目级覆盖）。
     * @param field 字段定义
     * @param projectMapping 项目级映射（可为 null）
     * @return 有效默认值
     */
    private String resolveDefaultValueWithOverride(CustomFieldDefinition field, CustomFieldProject projectMapping) {
        // 项目级覆盖优先
        if (projectMapping != null && projectMapping.getDefaultValue() != null) {
            // 空字符串 "" 表示显式设为无默认值
            String projectDefault = projectMapping.getDefaultValue();
            return projectDefault.isEmpty() ? null : projectDefault;
        }
        // fallback 到全局默认值
        return resolveDefaultValue(field);
    }

    /**
     * 判断字段在指定项目中是否必填（优先使用项目级覆盖）。
     * @param field 字段定义
     * @param projectMapping 项目级映射（可为 null）
     * @return 有效必填状态
     */
    private boolean isFieldRequired(CustomFieldDefinition field, CustomFieldProject projectMapping) {
        if (projectMapping != null && projectMapping.getIsRequired() != null) {
            return projectMapping.getIsRequired();
        }
        return Boolean.TRUE.equals(field.getIsRequired());
    }

    @Transactional
    public void saveValues(Long issueId, Map<Long, String> fieldValues, String issueType, Long projectId) {
        if (fieldValues == null || fieldValues.isEmpty()) return;

        List<CustomFieldDefinition> applicableFields = listByProject(projectId, issueType);
        Map<Long, CustomFieldDefinition> fieldMap = applicableFields.stream()
                .collect(Collectors.toMap(CustomFieldDefinition::getId, f -> f));

        // Load project-level overrides for required check
        Map<Long, CustomFieldProject> projectOverrides = getProjectFieldConditions(projectId);

        // Field-level editability check (updatableByRoles enforcement)
        List<Long> userRoleIds = getCurrentUserRoleIds(projectId);
        if (userRoleIds != null) { // null = system admin, skip check
            for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
                CustomFieldProject mapping = projectOverrides.get(entry.getKey());
                if (mapping == null) continue;
                List<Long> updatableRoles = parseRoleIds(mapping.getUpdatableByRoles());
                if (!isUpdatableByUser(updatableRoles, userRoleIds)) {
                    CustomFieldDefinition field = fieldMap.get(entry.getKey());
                    String fieldName = field != null ? field.getName() : "ID:" + entry.getKey();
                    throw new BusinessException(ErrorCode.ACCESS_DENIED,
                            "您没有编辑字段「" + fieldName + "」的权限");
                }
            }
        }

        List<CustomFieldValidationEngine.FieldValidationError> allErrors = new ArrayList<>();

        for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
            CustomFieldDefinition field = fieldMap.get(entry.getKey());
            if (field == null) continue;
            // Pass project-level required override to validation engine
            CustomFieldProject override = projectOverrides.get(entry.getKey());
            Boolean effectiveRequired = (override != null && override.getIsRequired() != null)
                    ? override.getIsRequired() : null;
            allErrors.addAll(validationEngine.validate(field, entry.getValue(), projectId, effectiveRequired));
        }

        for (CustomFieldDefinition field : applicableFields) {
            if (isFieldRequired(field, projectOverrides.get(field.getId())) && !fieldValues.containsKey(field.getId())) {
                allErrors.add(new CustomFieldValidationEngine.FieldValidationError(
                        field.getName(), "此字段为必填项"));
            }
        }

        if (!allErrors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "自定义字段验证失败: " + allErrors.stream()
                            .map(e -> e.getField() + ": " + e.getMessage())
                            .collect(Collectors.joining("; ")));
        }

        for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
            CustomFieldDefinition field = fieldMap.get(entry.getKey());
            if (field == null) continue;

            boolean isMulti = Boolean.TRUE.equals(field.getIsMulti());
            String rawInput = entry.getValue();

            if (isMulti) {
                // 多值字段：多行存储（先删再插）
                List<String> oldValues = getMultiValues(issueId, entry.getKey());
                List<String> newValues = parseMultiValueInput(rawInput);

                // 删除旧值
                valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getIssueId, issueId)
                        .eq(CustomFieldValue::getCustomFieldId, entry.getKey()));

                // 插入新值（每个选项一行）
                LocalDateTime now = LocalDateTime.now();
                for (String singleValue : newValues) {
                    CustomFieldValue cfv = new CustomFieldValue();
                    cfv.setIssueId(issueId);
                    cfv.setCustomFieldId(entry.getKey());
                    cfv.setValue(singleValue);
                    cfv.setIsMulti(true);
                    cfv.setCreatedAt(now);
                    cfv.setUpdatedAt(now);
                    valueMapper.insert(cfv);
                }

                // 记录活动日志
                if (!oldValues.equals(newValues)) {
                    String displayOld = resolveMultiDisplayValue(field, oldValues);
                    String displayNew = resolveMultiDisplayValue(field, newValues);
                    recordCustomFieldActivity(issueId, field.getName(), displayOld, displayNew);
                }
            } else {
                // 单值字段：使用 advisory lock 防止并发产生重复记录
                valueMapper.acquireSingleValueLock(issueId, entry.getKey());

                CustomFieldValue existing = valueMapper.selectOne(
                        new LambdaQueryWrapper<CustomFieldValue>()
                                .eq(CustomFieldValue::getIssueId, issueId)
                                .eq(CustomFieldValue::getCustomFieldId, entry.getKey()));

                String oldValue = existing != null ? existing.getValue() : null;
                String newValue = rawInput;

                if (existing != null) {
                    existing.setValue(newValue);
                    existing.setUpdatedAt(LocalDateTime.now());
                    valueMapper.updateById(existing);
                } else {
                    CustomFieldValue cfv = new CustomFieldValue();
                    cfv.setIssueId(issueId);
                    cfv.setCustomFieldId(entry.getKey());
                    cfv.setValue(newValue);
                    cfv.setIsMulti(false);
                    cfv.setCreatedAt(LocalDateTime.now());
                    cfv.setUpdatedAt(LocalDateTime.now());
                    valueMapper.insert(cfv);
                }

                // 记录活动日志（值有变化时）
                String effectiveNew = (newValue == null || newValue.isBlank()) ? null : newValue;
                if (!Objects.equals(oldValue, effectiveNew)) {
                    String displayOldValue = resolveDisplayValue(field, oldValue);
                    String displayNewValue = resolveDisplayValue(field, effectiveNew);
                    recordCustomFieldActivity(issueId, field.getName(), displayOldValue, displayNewValue);
                }
            }
        }
    }

    /**
     * 获取单值字段值 Map。多值字段以逗号分隔聚合返回（兼容旧逻辑）。
     */
    public Map<Long, String> getValues(Long issueId) {
        List<CustomFieldValue> allValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getIssueId, issueId));

        // 按 fieldId 分组，多值字段合并为逗号分隔
        Map<Long, List<String>> grouped = allValues.stream()
                .collect(Collectors.groupingBy(
                        CustomFieldValue::getCustomFieldId,
                        Collectors.mapping(CustomFieldValue::getValue, Collectors.toList())));

        Map<Long, String> result = new HashMap<>();
        for (Map.Entry<Long, List<String>> entry : grouped.entrySet()) {
            List<String> values = entry.getValue();
            if (values.size() == 1) {
                result.put(entry.getKey(), values.get(0));
            } else {
                result.put(entry.getKey(), String.join(",", values));
            }
        }
        return result;
    }

    /**
     * 获取多值字段的所有值（每个值独立一行存储）
     */
    private List<String> getMultiValues(Long issueId, Long customFieldId) {
        return valueMapper.selectList(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getIssueId, issueId)
                .eq(CustomFieldValue::getCustomFieldId, customFieldId)
                .orderByAsc(CustomFieldValue::getId))
                .stream()
                .map(CustomFieldValue::getValue)
                .toList();
    }

    /**
     * 解析多值输入：支持逗号分隔字符串（兼容旧格式）和空值
     */
    private List<String> parseMultiValueInput(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawInput.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted()
                .toList();
    }

    /**
     * 解析多值展示值（用于活动日志）
     */
    private String resolveMultiDisplayValue(CustomFieldDefinition field, List<String> values) {
        if (values == null || values.isEmpty()) return null;
        List<String> labels = new ArrayList<>();
        for (String idStr : values) {
            try {
                Long optionId = Long.parseLong(idStr);
                CustomFieldOption option = optionMapper.selectById(optionId);
                labels.add(option != null ? option.getValue() : idStr);
            } catch (NumberFormatException e) {
                labels.add(idStr);
            }
        }
        return String.join(", ", labels);
    }

    /**
     * 删除工单中不再适用于新 issueType 的自定义字段值。
     * 类型变更后调用，参考 OpenProject reset_custom_values! 逻辑。
     *
     * @return 被删除的字段 ID 列表
     */
    @Transactional
    public List<Long> removeOrphanValues(Long issueId, String newIssueType, Long projectId) {
        Map<Long, String> currentValues = getValues(issueId);
        if (currentValues.isEmpty()) return List.of();

        // 获取新类型下适用的字段集合
        List<CustomFieldDefinition> applicableFields = listByProject(projectId, newIssueType);
        Set<Long> applicableFieldIds = applicableFields.stream()
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());

        // 找出不再适用的字段 ID
        List<Long> orphanFieldIds = currentValues.keySet().stream()
                .filter(fieldId -> !applicableFieldIds.contains(fieldId))
                .toList();

        if (orphanFieldIds.isEmpty()) return List.of();

        // 在删除前，加载字段定义并为每个被清除的字段记录活动日志
        Map<Long, CustomFieldDefinition> fieldMap = definitionMapper.selectBatchIds(orphanFieldIds)
                .stream()
                .collect(Collectors.toMap(CustomFieldDefinition::getId, f -> f));

        for (Long fieldId : orphanFieldIds) {
            CustomFieldDefinition field = fieldMap.get(fieldId);
            String rawValue = currentValues.get(fieldId);
            if (field != null && rawValue != null && !rawValue.isBlank()) {
                String displayValue = resolveDisplayValue(field, rawValue);
                recordCustomFieldActivity(issueId, field.getName(), displayValue, null);
            }
        }

        // 删除不再适用的字段值
        valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getIssueId, issueId)
                .in(CustomFieldValue::getCustomFieldId, orphanFieldIds));

        log.info("Issue {} type changed to {}: removed {} orphan custom field values (fieldIds: {})",
                issueId, newIssueType, orphanFieldIds.size(), orphanFieldIds);

        return orphanFieldIds;
    }

    /**
     * 批量获取多个 Issue 的自定义字段展示值（不含颜色）
     */
    public Map<Long, Map<String, String>> getBatchDisplayValues(List<Long> issueIds) {
        return getBatchDisplayValues(issueIds, null);
    }

    /**
     * 批量获取多个 Issue 的自定义字段展示值
     * 返回 Map<issueId, Map<"cf_{fieldId}", displayValue>>
     * list 类型解析为选项文本，user 类型解析为用户名
     *
     * 仅返回对每个 issue 所在项目适用的字段值（过滤孤立数据）。
     *
     * @param colorOutMap 可选参数，非 null 时填充颜色数据 Map<issueId, Map<"cf_{fieldId}", hex>>
     */
    public Map<Long, Map<String, String>> getBatchDisplayValues(List<Long> issueIds, Map<Long, Map<String, String>> colorOutMap) {
        if (issueIds == null || issueIds.isEmpty()) {
            return new HashMap<>();
        }

        // 1. 批量加载所有相关 custom_field_value 记录
        List<CustomFieldValue> allValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .in(CustomFieldValue::getIssueId, issueIds));
        if (allValues.isEmpty()) {
            return new HashMap<>();
        }

        // 2. 收集涉及的 fieldId，加载字段定义
        Set<Long> fieldIds = allValues.stream()
                .map(CustomFieldValue::getCustomFieldId)
                .collect(Collectors.toSet());
        Map<Long, CustomFieldDefinition> fieldDefMap = definitionMapper.selectBatchIds(fieldIds)
                .stream()
                .collect(Collectors.toMap(CustomFieldDefinition::getId, f -> f));

        // 3. 构建字段适用性过滤：确定哪些字段对哪些项目适用
        //    - isForAll=true 的字段对所有项目适用
        //    - isForAll=false 的字段只对关联项目适用
        Set<Long> nonGlobalFieldIds = fieldDefMap.values().stream()
                .filter(f -> !Boolean.TRUE.equals(f.getIsForAll()))
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());

        // 加载非全局字段的项目关联
        Map<Long, Set<Long>> fieldToProjectIds = new HashMap<>();
        if (!nonGlobalFieldIds.isEmpty()) {
            List<CustomFieldProject> cfProjects = projectMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldProject>()
                            .in(CustomFieldProject::getCustomFieldId, nonGlobalFieldIds));
            for (CustomFieldProject cfp : cfProjects) {
                fieldToProjectIds
                        .computeIfAbsent(cfp.getCustomFieldId(), k -> new HashSet<>())
                        .add(cfp.getProjectId());
            }
        }

        // 查询 issue→projectId 映射（用于适用性过滤）
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getId, Issue::getProjectId)
                        .in(Issue::getId, issueIds));
        Map<Long, Long> issueProjectMap = issues.stream()
                .collect(Collectors.toMap(Issue::getId, Issue::getProjectId));

        // 4. 过滤掉不适用的字段值
        List<CustomFieldValue> applicableValues = allValues.stream()
                .filter(v -> {
                    CustomFieldDefinition fieldDef = fieldDefMap.get(v.getCustomFieldId());
                    if (fieldDef == null) return false;
                    // 全局字段对所有项目适用
                    if (Boolean.TRUE.equals(fieldDef.getIsForAll())) return true;
                    // 非全局字段需检查项目关联
                    Long projectId = issueProjectMap.get(v.getIssueId());
                    if (projectId == null) return false;
                    Set<Long> allowedProjects = fieldToProjectIds.get(v.getCustomFieldId());
                    return allowedProjects != null && allowedProjects.contains(projectId);
                })
                .toList();

        if (applicableValues.isEmpty()) {
            return new HashMap<>();
        }

        // 5. 预加载 list 类型字段的选项映射 (optionId → optionValue) 和 (optionId → color)
        Set<Long> listFieldIds = fieldDefMap.values().stream()
                .filter(f -> "list".equals(f.getFieldFormat()))
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());
        Map<Long, String> optionTextMap = new HashMap<>();
        Map<Long, String> optionColorMap = new HashMap<>();
        if (!listFieldIds.isEmpty()) {
            List<CustomFieldOption> options = optionMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldOption>()
                            .in(CustomFieldOption::getCustomFieldId, listFieldIds));
            for (CustomFieldOption opt : options) {
                optionTextMap.put(opt.getId(), opt.getValue());
                if (opt.getColor() != null) {
                    optionColorMap.put(opt.getId(), opt.getColor());
                }
            }
        }

        // 6. 预加载 user 类型字段引用的用户名
        Set<Long> userFieldIds = fieldDefMap.values().stream()
                .filter(f -> "user".equals(f.getFieldFormat()))
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());
        Map<Long, String> userNameMap = new HashMap<>();
        if (!userFieldIds.isEmpty()) {
            Set<Long> userIds = applicableValues.stream()
                    .filter(v -> userFieldIds.contains(v.getCustomFieldId()) && v.getValue() != null)
                    .map(v -> {
                        try { return Long.parseLong(v.getValue()); }
                        catch (NumberFormatException e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!userIds.isEmpty()) {
                userMapper.selectBatchIds(userIds).forEach(u ->
                        userNameMap.put(u.getId(), u.getDisplayName()));
            }
        }

        // 7. 按 (issueId, fieldId) 分组，处理多值字段
        // 结构: Map<issueId, Map<fieldId, List<rawValue>>>
        Map<Long, Map<Long, List<String>>> groupedByIssueAndField = new HashMap<>();
        for (CustomFieldValue cfv : applicableValues) {
            groupedByIssueAndField
                    .computeIfAbsent(cfv.getIssueId(), k -> new HashMap<>())
                    .computeIfAbsent(cfv.getCustomFieldId(), k -> new ArrayList<>())
                    .add(cfv.getValue());
        }

        Map<Long, Map<String, String>> result = new HashMap<>();
        for (Map.Entry<Long, Map<Long, List<String>>> issueEntry : groupedByIssueAndField.entrySet()) {
            Long issueId = issueEntry.getKey();
            Map<String, String> fieldDisplayMap = new HashMap<>();
            Map<String, String> fieldColorMap = new HashMap<>();

            for (Map.Entry<Long, List<String>> fieldEntry : issueEntry.getValue().entrySet()) {
                Long fieldId = fieldEntry.getKey();
                List<String> values = fieldEntry.getValue();
                CustomFieldDefinition fieldDef = fieldDefMap.get(fieldId);
                if (fieldDef == null) continue;

                String cfKey = "cf_" + fieldId;
                if (Boolean.TRUE.equals(fieldDef.getIsMulti())) {
                    // 多值字段：每行一个值，聚合展示
                    List<String> labels = new ArrayList<>();
                    for (String v : values) {
                        String display = resolveDisplayValue(v, fieldDef, optionTextMap, userNameMap);
                        if (display != null) labels.add(display);
                    }
                    fieldDisplayMap.put(cfKey, String.join(", ", labels));
                    // 多值字段颜色：取第一个有颜色的（列表视图空间有限）
                    if ("list".equals(fieldDef.getFieldFormat())) {
                        for (String v : values) {
                            String color = resolveOptionColor(v, fieldDef, optionColorMap);
                            if (color != null) {
                                fieldColorMap.put(cfKey, color);
                                break;
                            }
                        }
                    }
                } else {
                    // 单值字段：取第一条
                    String displayValue = resolveDisplayValue(values.get(0), fieldDef, optionTextMap, userNameMap);
                    fieldDisplayMap.put(cfKey, displayValue);
                    // 颜色
                    if ("list".equals(fieldDef.getFieldFormat())) {
                        String color = resolveOptionColor(values.get(0), fieldDef, optionColorMap);
                        if (color != null) {
                            fieldColorMap.put(cfKey, color);
                        }
                    }
                }
            }

            result.put(issueId, fieldDisplayMap);
            if (colorOutMap != null && !fieldColorMap.isEmpty()) {
                colorOutMap.put(issueId, fieldColorMap);
            }
        }

        return result;
    }

    /**
     * 将原始值转换为用户可读的展示值（批量版，每个 rawValue 为单个值）
     */
    private String resolveDisplayValue(String rawValue, CustomFieldDefinition fieldDef,
                                       Map<Long, String> optionTextMap, Map<Long, String> userNameMap) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return switch (fieldDef.getFieldFormat()) {
            case "list" -> {
                // 每行存一个选项 ID（多值字段在外层已按行分组处理）
                try {
                    Long optionId = Long.parseLong(rawValue);
                    yield optionTextMap.getOrDefault(optionId, rawValue);
                } catch (NumberFormatException e) {
                    yield rawValue;
                }
            }
            case "user" -> {
                try {
                    Long userId = Long.parseLong(rawValue);
                    yield userNameMap.getOrDefault(userId, rawValue);
                } catch (NumberFormatException e) {
                    yield rawValue;
                }
            }
            case "bool" -> "true".equals(rawValue) ? "是" : "否";
            case "string", "text", "int", "float", "date", "datetime" -> rawValue;
            default -> {
                log.warn("Unknown field_format '{}' for field '{}' (id={}), returning raw value",
                        fieldDef.getFieldFormat(), fieldDef.getName(), fieldDef.getId());
                yield rawValue;
            }
        };
    }

    /**
     * 解析选项颜色：仅 list 类型字段有效，返回选项配置的颜色值（HEX），无颜色时返回 null。
     */
    private String resolveOptionColor(String rawValue, CustomFieldDefinition fieldDef, Map<Long, String> optionColorMap) {
        if (rawValue == null || rawValue.isBlank() || !"list".equals(fieldDef.getFieldFormat())) {
            return null;
        }
        try {
            Long optionId = Long.parseLong(rawValue);
            return optionColorMap.get(optionId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取 Issue 的自定义字段值（含字段名称、类型信息，用于前端展示）
     */
    public List<CustomFieldValueVO> getValuesForDisplay(Long issueId, Long projectId, String issueType) {
        // 加载所有 custom_field_value 记录
        List<CustomFieldValue> allValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getIssueId, issueId));
        if (allValues.isEmpty()) return List.of();

        List<CustomFieldDefinition> applicableFields = listByProject(projectId, issueType);
        Map<Long, CustomFieldDefinition> fieldMap = applicableFields.stream()
                .collect(Collectors.toMap(CustomFieldDefinition::getId, f -> f));

        // 按 fieldId 分组
        Map<Long, List<String>> grouped = allValues.stream()
                .collect(Collectors.groupingBy(
                        CustomFieldValue::getCustomFieldId,
                        Collectors.mapping(CustomFieldValue::getValue, Collectors.toList())));

        // 批量预加载 list 类型字段的选项映射 (optionId → optionValue) 和 (optionId → color)
        Set<Long> listFieldIds = fieldMap.values().stream()
                .filter(f -> "list".equals(f.getFieldFormat()))
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());
        Map<Long, String> optionTextMap = new HashMap<>();
        Map<Long, String> optionColorMap = new HashMap<>();
        if (!listFieldIds.isEmpty()) {
            List<CustomFieldOption> options = optionMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldOption>()
                            .in(CustomFieldOption::getCustomFieldId, listFieldIds));
            for (CustomFieldOption opt : options) {
                optionTextMap.put(opt.getId(), opt.getValue());
                if (opt.getColor() != null) {
                    optionColorMap.put(opt.getId(), opt.getColor());
                }
            }
        }

        // 批量预加载 user 类型字段引用的用户名
        Set<Long> userFieldIds = fieldMap.values().stream()
                .filter(f -> "user".equals(f.getFieldFormat()))
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());
        Map<Long, String> userNameMap = new HashMap<>();
        if (!userFieldIds.isEmpty()) {
            Set<Long> userIds = grouped.entrySet().stream()
                    .filter(e -> userFieldIds.contains(e.getKey()))
                    .flatMap(e -> e.getValue().stream())
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> {
                        try { return Long.parseLong(v); }
                        catch (NumberFormatException e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!userIds.isEmpty()) {
                userMapper.selectBatchIds(userIds).forEach(u ->
                        userNameMap.put(u.getId(), u.getDisplayName()));
            }
        }

        // 组装结果，使用预加载的 Map 解析展示值
        List<CustomFieldValueVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<String>> entry : grouped.entrySet()) {
            CustomFieldDefinition field = fieldMap.get(entry.getKey());
            if (field == null) continue; // 字段已删除或不再适用

            boolean isMulti = Boolean.TRUE.equals(field.getIsMulti());
            List<String> values = entry.getValue();

            CustomFieldValueVO vo = new CustomFieldValueVO();
            vo.setCustomFieldId(String.valueOf(entry.getKey()));
            vo.setFieldName(field.getName());
            vo.setFieldFormat(field.getFieldFormat());
            vo.setIsMulti(isMulti);

            if (isMulti) {
                // 多值字段：返回 values 数组 + displayValues 数组
                vo.setValues(values);
                List<String> displayValues = new ArrayList<>();
                List<String> colors = new ArrayList<>();
                boolean hasAnyColor = false;
                for (String v : values) {
                    String display = resolveDisplayValue(v, field, optionTextMap, userNameMap);
                    displayValues.add(display != null ? display : "");
                    // 解析颜色（仅 list 类型）
                    String color = resolveOptionColor(v, field, optionColorMap);
                    colors.add(color);
                    if (color != null) hasAnyColor = true;
                }
                vo.setDisplayValues(displayValues);
                if (hasAnyColor) {
                    vo.setColors(colors);
                }
                // 兼容：value/displayValue 用逗号分隔聚合
                vo.setValue(String.join(",", values));
                vo.setDisplayValue(String.join(", ", displayValues));
            } else {
                // 单值字段
                String singleValue = values.get(0);
                vo.setValue(singleValue);
                String display = resolveDisplayValue(singleValue, field, optionTextMap, userNameMap);
                vo.setDisplayValue(display != null ? display : "");
                // 解析颜色（仅 list 类型）
                String color = resolveOptionColor(singleValue, field, optionColorMap);
                vo.setColor(color);
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 将存储值转为前端可读展示值。
     * 每个 rawValue 为单个值（多值字段的聚合展示在调用方处理）。
     */
    private String resolveDisplayValue(CustomFieldDefinition field, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return "";
        switch (field.getFieldFormat()) {
            case "list" -> {
                // 单个选项 ID → 选项文本
                try {
                    Long optionId = Long.parseLong(rawValue);
                    CustomFieldOption option = optionMapper.selectById(optionId);
                    return option != null ? option.getValue() : rawValue;
                } catch (NumberFormatException e) {
                    return rawValue;
                }
            }
            case "user" -> {
                try {
                    Long userId = Long.parseLong(rawValue);
                    var user = userMapper.selectById(userId);
                    return user != null ? user.getDisplayName() : rawValue;
                } catch (NumberFormatException e) {
                    return rawValue;
                }
            }
            case "bool" -> { return "true".equals(rawValue) ? "是" : "否"; }
            case "string", "text", "int", "float", "date", "datetime" -> { return rawValue; }
            default -> {
                log.warn("Unknown field_format '{}' for field '{}' (id={}), returning raw value",
                        field.getFieldFormat(), field.getName(), field.getId());
                return rawValue;
            }
        }
    }

    /**
     * 保存单个自定义字段值（用于侧边栏内联编辑）。
     * 支持单值字段（value 为单个值）和多值字段（value 为逗号分隔的选项 ID）。
     */
    @Transactional
    public void saveSingleValue(Long issueId, Long customFieldId, String value, String issueType, Long projectId) {
        // 验证字段属于该项目/类型
        List<CustomFieldDefinition> applicableFields = listByProject(projectId, issueType);
        CustomFieldDefinition field = applicableFields.stream()
                .filter(f -> f.getId().equals(customFieldId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "字段不适用于当前工单"));

        // 加载项目级覆盖（用于必填性检查）
        Map<Long, CustomFieldProject> projectOverrides = getProjectFieldConditions(projectId);
        CustomFieldProject override = projectOverrides.get(customFieldId);
        boolean effectiveRequired = isFieldRequired(field, override);

        // 验证值（传入项目级必填覆盖）
        Boolean requiredOverride = (override != null && override.getIsRequired() != null)
                ? override.getIsRequired() : null;
        List<CustomFieldValidationEngine.FieldValidationError> errors = validationEngine.validate(field, value, projectId, requiredOverride);
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    errors.stream().map(e -> e.getField() + ": " + e.getMessage())
                            .collect(Collectors.joining("; ")));
        }

        boolean isMulti = Boolean.TRUE.equals(field.getIsMulti());

        if (isMulti) {
            // 多值字段：多行存储
            List<String> oldValues = getMultiValues(issueId, customFieldId);
            List<String> newValues = parseMultiValueInput(value);

            // 清空值检查（考虑项目级必填覆盖）
            if (newValues.isEmpty() && effectiveRequired) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, field.getName() + " 为必填项，不能清空");
            }

            // 删除旧值
            valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                    .eq(CustomFieldValue::getIssueId, issueId)
                    .eq(CustomFieldValue::getCustomFieldId, customFieldId));

            // 插入新值（每个选项一行）
            LocalDateTime now = LocalDateTime.now();
            for (String singleValue : newValues) {
                CustomFieldValue cfv = new CustomFieldValue();
                cfv.setIssueId(issueId);
                cfv.setCustomFieldId(customFieldId);
                cfv.setValue(singleValue);
                cfv.setIsMulti(true);
                cfv.setCreatedAt(now);
                cfv.setUpdatedAt(now);
                valueMapper.insert(cfv);
            }

            // 记录活动日志
            if (!oldValues.equals(newValues)) {
                String displayOld = resolveMultiDisplayValue(field, oldValues);
                String displayNew = resolveMultiDisplayValue(field, newValues);
                recordCustomFieldActivity(issueId, field.getName(), displayOld, displayNew);
            }
        } else {
            // 单值字段：使用 advisory lock 防止并发产生重复记录
            valueMapper.acquireSingleValueLock(issueId, customFieldId);

            CustomFieldValue existing = valueMapper.selectOne(
                    new LambdaQueryWrapper<CustomFieldValue>()
                            .eq(CustomFieldValue::getIssueId, issueId)
                            .eq(CustomFieldValue::getCustomFieldId, customFieldId));
            String oldValue = existing != null ? existing.getValue() : null;

            if (value == null || value.isBlank()) {
                // 清空值：如果非必填，允许删除（考虑项目级必填覆盖）
                if (effectiveRequired) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, field.getName() + " 为必填项，不能清空");
                }
                if (existing != null) {
                    valueMapper.deleteById(existing.getId());
                }
            } else if (existing != null) {
                existing.setValue(value);
                existing.setUpdatedAt(LocalDateTime.now());
                valueMapper.updateById(existing);
            } else {
                CustomFieldValue cfv = new CustomFieldValue();
                cfv.setIssueId(issueId);
                cfv.setCustomFieldId(customFieldId);
                cfv.setValue(value);
                cfv.setIsMulti(false);
                cfv.setCreatedAt(LocalDateTime.now());
                cfv.setUpdatedAt(LocalDateTime.now());
                valueMapper.insert(cfv);
            }

            // 记录活动日志（值有变化时）
            String newValue = (value == null || value.isBlank()) ? null : value;
            if (!Objects.equals(oldValue, newValue)) {
                String displayOldValue = resolveDisplayValue(field, oldValue);
                String displayNewValue = resolveDisplayValue(field, newValue);
                recordCustomFieldActivity(issueId, field.getName(), displayOldValue, displayNewValue);
            }
        }
    }

    public void deleteValuesByIssue(Long issueId) {
        valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getIssueId, issueId));
    }

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

    /**
     * 获取所有枚举类型（list）字段定义列表。
     * 用于"从已有字段复制选项"功能的下拉数据源。
     */
    public List<CustomFieldDefinition> listEnumFields() {
        return definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getFieldFormat, "list")
                        .orderByAsc(CustomFieldDefinition::getPosition));
    }

    /**
     * 内联添加枚举字段选项值（工单详情页/创建表单快捷入口）。
     * 参考 YouTrack: 在工单编辑时直接添加新值到枚举字段。
     *
     * 逻辑：
     * 1. 校验字段存在且为 list 类型
     * 2. 校验字段在该项目中可用
     * 3. 检查是否有同名选项（活跃或归档）
     * 4. 如有同名归档选项 → 恢复；如有同名活跃选项 → 报错
     * 5. 否则新建选项（position = max + 1）
     */
    @Transactional
    public CustomFieldOption addOptionInline(Long projectId, Long fieldId, String value, String color) {
        // 1. 校验字段存在且为 list 类型
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }
        if (!"list".equals(field.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有枚举类型字段支持添加选项值");
        }

        // 2. 校验字段在该项目中可用（全局字段或已关联到项目）
        if (!field.getIsForAll()) {
            CustomFieldProject mapping = projectMapper.selectOne(
                    new LambdaQueryWrapper<CustomFieldProject>()
                            .eq(CustomFieldProject::getCustomFieldId, fieldId)
                            .eq(CustomFieldProject::getProjectId, projectId));
            if (mapping == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该字段未关联到此项目");
            }
        }

        // 3. 检查是否有同名选项
        List<CustomFieldOption> existingOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId)
                        .eq(CustomFieldOption::getValue, value.trim()));

        for (CustomFieldOption existing : existingOptions) {
            if (!Boolean.TRUE.equals(existing.getIsArchived())) {
                // 同名活跃选项已存在
                throw new BusinessException(ErrorCode.BAD_REQUEST, "选项值\"" + value.trim() + "\"已存在");
            }
        }

        // 4. 如有同名归档选项 → 恢复
        CustomFieldOption archivedSameName = existingOptions.stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsArchived()))
                .findFirst()
                .orElse(null);

        if (archivedSameName != null) {
            // 计算新 position（append 到末尾）
            int maxPosition = getMaxOptionPosition(fieldId);
            archivedSameName.setPosition(maxPosition + 1);
            archivedSameName.setIsArchived(false);
            archivedSameName.setIsDefault(false);
            if (color != null) {
                archivedSameName.setColor(color);
            }
            archivedSameName.setUpdatedAt(LocalDateTime.now());
            optionMapper.updateById(archivedSameName);
            log.info("Inline add option: reactivated archived option {} (value='{}'), fieldId={}, projectId={}",
                    archivedSameName.getId(), value.trim(), fieldId, projectId);
            return archivedSameName;
        }

        // 5. 新建选项
        int maxPosition = getMaxOptionPosition(fieldId);
        CustomFieldOption option = new CustomFieldOption();
        option.setCustomFieldId(fieldId);
        option.setValue(value.trim());
        option.setPosition(maxPosition + 1);
        option.setIsDefault(false);
        option.setColor(color);
        option.setIsArchived(false);
        option.setCreatedAt(LocalDateTime.now());
        option.setUpdatedAt(LocalDateTime.now());
        optionMapper.insert(option);
        log.info("Inline add option: created new option {} (value='{}'), fieldId={}, projectId={}",
                option.getId(), value.trim(), fieldId, projectId);
        return option;
    }

    /**
     * 获取字段选项列表中的最大 position 值
     */
    private int getMaxOptionPosition(Long fieldId) {
        List<CustomFieldOption> allOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId)
                        .eq(CustomFieldOption::getIsArchived, false)
                        .orderByDesc(CustomFieldOption::getPosition)
                        .last("LIMIT 1"));
        return allOptions.isEmpty() ? -1 : allOptions.get(0).getPosition();
    }

    public List<CustomFieldOption> getOptions(Long fieldId) {
        List<CustomFieldOption> allOptions = optionMapper.selectList(new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, fieldId)
                .orderByAsc(CustomFieldOption::getPosition));
        return filterSupersededArchivedOptions(allOptions);
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

    // ========== 批量加载方法（消除 N+1 查询）==========

    /**
     * 批量获取多个字段的选项列表。
     * @return Map<fieldId, List<CustomFieldOption>>（按 position 排序）
     */
    public Map<Long, List<CustomFieldOption>> getBatchOptions(List<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) return Map.of();
        List<CustomFieldOption> allOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .in(CustomFieldOption::getCustomFieldId, fieldIds)
                        .orderByAsc(CustomFieldOption::getPosition));
        // 按字段分组后，过滤每组中被活跃选项替代的归档残留
        return allOptions.stream()
                .collect(Collectors.groupingBy(CustomFieldOption::getCustomFieldId))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> filterSupersededArchivedOptions(e.getValue())));
    }

    /**
     * 过滤被同名活跃选项替代的归档残留条目。
     * 规则：如果一个归档选项的 value 与同字段内某个活跃选项相同，则视为"被替代"，从返回列表中移除。
     * 没有同名活跃选项的归档条目（即被引用但该值已不再活跃列表中）仍然保留。
     */
    private List<CustomFieldOption> filterSupersededArchivedOptions(List<CustomFieldOption> options) {
        Set<String> activeValues = options.stream()
                .filter(o -> !o.getIsArchived())
                .map(CustomFieldOption::getValue)
                .collect(Collectors.toSet());

        return options.stream()
                .filter(o -> !o.getIsArchived() || !activeValues.contains(o.getValue()))
                .toList();
    }

    /**
     * 批量获取多个字段的项目关联 ID 列表。
     * @return Map<fieldId, List<projectId>>
     */
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

    /**
     * 批量获取多个字段的适用工单类型列表。
     * @return Map<fieldId, List<issueType>>
     */
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

    // ========== 项目级自定义字段管理 ==========

    /**
     * 获取项目已附加的自定义字段列表（含全局字段）
     * 按 position 排序
     */
    public List<CustomFieldDefinition> listProjectFields(Long projectId) {
        // 获取全局字段（is_for_all = true）
        List<CustomFieldDefinition> globalFields = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getIsForAll, true)
                        .orderByAsc(CustomFieldDefinition::getPosition));

        // 获取项目专属字段
        List<CustomFieldProject> projectMappings = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .orderByAsc(CustomFieldProject::getPosition));

        List<Long> projectFieldIds = projectMappings.stream()
                .map(CustomFieldProject::getCustomFieldId)
                .toList();

        List<CustomFieldDefinition> projectFields = projectFieldIds.isEmpty()
                ? List.of()
                : definitionMapper.selectBatchIds(projectFieldIds);

        // 按 projectMappings 的 position 排序
        Map<Long, Integer> positionMap = projectMappings.stream()
                .collect(Collectors.toMap(CustomFieldProject::getCustomFieldId, CustomFieldProject::getPosition));
        projectFields = projectFields.stream()
                .sorted(Comparator.comparingInt(f -> positionMap.getOrDefault(f.getId(), 0)))
                .toList();

        // 合并：全局字段在前，项目字段在后
        List<CustomFieldDefinition> result = new ArrayList<>(globalFields);
        for (CustomFieldDefinition pf : projectFields) {
            if (result.stream().noneMatch(f -> f.getId().equals(pf.getId()))) {
                result.add(pf);
            }
        }
        return result;
    }

    /**
     * 获取可以附加到项目的字段列表（排除已附加的和全局字段）
     */
    public List<CustomFieldDefinition> listAvailableFieldsForProject(Long projectId) {
        // 已关联的字段 ID
        Set<Long> attachedIds = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId))
                .stream()
                .map(CustomFieldProject::getCustomFieldId)
                .collect(Collectors.toSet());

        // 获取所有非全局字段
        List<CustomFieldDefinition> allNonGlobal = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getIsForAll, false)
                        .orderByAsc(CustomFieldDefinition::getPosition));

        // 排除已附加的
        return allNonGlobal.stream()
                .filter(f -> !attachedIds.contains(f.getId()))
                .toList();
    }

    /**
     * 获取"Fields in Projects"矩阵数据。
     * 返回每个项目及其关联的自定义字段列表（含全局字段）。
     */
    public List<ProjectFieldsVO> getFieldsInProjects() {
        // 1. 获取所有活跃项目
        List<Project> projects = projectEntityMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getStatus, ProjectStatus.ACTIVE)
                        .orderByAsc(Project::getName));

        // 2. 获取所有全局字段
        List<CustomFieldDefinition> globalFields = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getIsForAll, true)
                        .orderByAsc(CustomFieldDefinition::getPosition));

        // 3. 获取所有项目-字段关联
        List<CustomFieldProject> allMappings = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .orderByAsc(CustomFieldProject::getPosition));
        Map<Long, List<Long>> projectFieldIdsMap = allMappings.stream()
                .collect(Collectors.groupingBy(
                        CustomFieldProject::getProjectId,
                        Collectors.mapping(CustomFieldProject::getCustomFieldId, Collectors.toList())));

        // 4. 收集所有需要加载的非全局字段 ID
        Set<Long> allProjectFieldIds = allMappings.stream()
                .map(CustomFieldProject::getCustomFieldId)
                .collect(Collectors.toSet());
        Map<Long, CustomFieldDefinition> fieldMap = new HashMap<>();
        globalFields.forEach(f -> fieldMap.put(f.getId(), f));
        if (!allProjectFieldIds.isEmpty()) {
            definitionMapper.selectBatchIds(allProjectFieldIds)
                    .forEach(f -> fieldMap.put(f.getId(), f));
        }

        // 5. 组装结果
        List<ProjectFieldsVO> result = new ArrayList<>();
        for (Project project : projects) {
            ProjectFieldsVO pvo = new ProjectFieldsVO();
            pvo.setProjectId(String.valueOf(project.getId()));
            pvo.setProjectName(project.getName());
            pvo.setProjectKey(project.getKey());

            List<ProjectFieldsVO.FieldSummaryVO> fields = new ArrayList<>();

            // 全局字段
            for (CustomFieldDefinition gf : globalFields) {
                fields.add(toFieldSummary(gf));
            }

            // 项目专属字段
            List<Long> projectFieldIds = projectFieldIdsMap.getOrDefault(project.getId(), List.of());
            for (Long fieldId : projectFieldIds) {
                CustomFieldDefinition fd = fieldMap.get(fieldId);
                if (fd != null && !Boolean.TRUE.equals(fd.getIsForAll())) {
                    fields.add(toFieldSummary(fd));
                }
            }

            pvo.setFields(fields);
            result.add(pvo);
        }
        return result;
    }

    private ProjectFieldsVO.FieldSummaryVO toFieldSummary(CustomFieldDefinition field) {
        ProjectFieldsVO.FieldSummaryVO vo = new ProjectFieldsVO.FieldSummaryVO();
        vo.setId(String.valueOf(field.getId()));
        vo.setName(field.getName());
        vo.setFieldFormat(field.getFieldFormat());
        vo.setIsForAll(field.getIsForAll());
        vo.setIsRequired(field.getIsRequired());
        vo.setIsMulti(field.getIsMulti());
        return vo;
    }

    /**
     * 附加自定义字段到项目
     */
    @Transactional
    public void attachFieldToProject(Long projectId, Long customFieldId) {
        // 验证字段存在
        CustomFieldDefinition field = definitionMapper.selectById(customFieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "自定义字段不存在");
        }

        // 全局字段无需附加
        if (Boolean.TRUE.equals(field.getIsForAll())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "全局字段无需附加到项目，它已对所有项目可用");
        }

        // 检查是否已附加
        boolean exists = projectMapper.exists(new LambdaQueryWrapper<CustomFieldProject>()
                .eq(CustomFieldProject::getCustomFieldId, customFieldId)
                .eq(CustomFieldProject::getProjectId, projectId));
        if (exists) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该字段已附加到本项目");
        }

        // 计算 position（追加到末尾）
        Long count = projectMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId));
        int position = count != null ? count.intValue() : 0;

        CustomFieldProject cfp = new CustomFieldProject();
        cfp.setCustomFieldId(customFieldId);
        cfp.setProjectId(projectId);
        cfp.setPosition(position);
        projectMapper.insert(cfp);
    }

    /**
     * 从项目移除自定义字段
     * 注意：移除后该字段在本项目所有工单中的值将被清除
     */
    @Transactional
    public void detachFieldFromProject(Long projectId, Long customFieldId) {
        // 验证字段存在
        CustomFieldDefinition field = definitionMapper.selectById(customFieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "自定义字段不存在");
        }

        // 全局字段不能从项目移除
        if (Boolean.TRUE.equals(field.getIsForAll())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "全局字段不能从项目中移除");
        }

        // 验证关联存在
        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, customFieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));
        if (mapping == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该字段未附加到本项目");
        }

        // 删除关联
        projectMapper.deleteById(mapping.getId());

        // 清除该项目所有工单中该字段的值（参数化子查询，避免 SQL 拼接）
        valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getCustomFieldId, customFieldId)
                .apply("issue_id IN (SELECT id FROM issue WHERE project_id = {0})", projectId));
    }

    /**
     * 调整字段在项目中的显示顺序
     */
    @Transactional
    public void reorderProjectFields(Long projectId, List<Long> fieldIds) {
        // 批量查出所有关联记录
        List<CustomFieldProject> mappings = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .in(CustomFieldProject::getCustomFieldId, fieldIds));

        // 构建 fieldId → mapping 索引
        Map<Long, CustomFieldProject> mappingMap = mappings.stream()
                .collect(Collectors.toMap(CustomFieldProject::getCustomFieldId, m -> m));

        // 更新 position 并批量提交
        for (int i = 0; i < fieldIds.size(); i++) {
            CustomFieldProject mapping = mappingMap.get(fieldIds.get(i));
            if (mapping != null) {
                mapping.setPosition(i);
                projectMapper.updateById(mapping);
            }
        }
    }

    // ========== 活动记录辅助方法 ==========

    /**
     * 记录自定义字段变更的活动日志
     */
    private void recordCustomFieldActivity(Long issueId, String fieldName, String oldValue, String newValue) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        IssueActivity activity = new IssueActivity();
        activity.setIssueId(issueId);
        activity.setUserId(currentUserId);
        activity.setAction("updated");
        activity.setFieldName(fieldName);
        activity.setOldValue(oldValue != null && oldValue.isEmpty() ? null : oldValue);
        activity.setNewValue(newValue != null && newValue.isEmpty() ? null : newValue);
        activity.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
    }

    // ========== 条件显示（Conditional Custom Fields） ==========

    /**
     * 获取项目中字段的条件配置 map: fieldId → CustomFieldProject（含条件数据）
     */
    public Map<Long, CustomFieldProject> getProjectFieldConditions(Long projectId) {
        List<CustomFieldProject> mappings = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId));
        return mappings.stream()
                .collect(Collectors.toMap(CustomFieldProject::getCustomFieldId, m -> m, (a, b) -> a));
    }

    /**
     * 设置字段条件显示规则（项目级）
     * @param projectId 项目 ID
     * @param fieldId 要设置条件的字段 ID
     * @param conditionFieldId 条件源字段 ID（null 表示清除条件）
     * @param conditionValues 触发显示的选项 ID 列表
     */
    @Transactional
    public void setFieldCondition(Long projectId, Long fieldId, Long conditionFieldId, List<String> conditionValues) {
        // 1. 验证目标字段存在且已附加到项目（或为全局字段）
        CustomFieldDefinition targetField = definitionMapper.selectById(fieldId);
        if (targetField == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标字段不存在");
        }

        // 查找或创建项目关联记录
        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));

        // 全局字段可能没有 custom_field_project 记录，需要创建一条用于存储条件
        if (mapping == null) {
            if (!Boolean.TRUE.equals(targetField.getIsForAll())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该字段未附加到本项目");
            }
            // 全局字段：创建一条 mapping 记录用于存放条件
            mapping = new CustomFieldProject();
            mapping.setCustomFieldId(fieldId);
            mapping.setProjectId(projectId);
            mapping.setPosition(0);
        }

        // 2. 清除条件
        if (conditionFieldId == null) {
            mapping.setConditionFieldId(null);
            mapping.setConditionValues(null);
            if (mapping.getId() != null) {
                projectMapper.updateById(mapping);
            } else {
                // 全局字段无需创建无条件的 mapping
                // 不插入
            }
            return;
        }

        // 3. 验证条件源字段
        CustomFieldDefinition condField = definitionMapper.selectById(conditionFieldId);
        if (condField == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "条件源字段不存在");
        }

        // 条件源字段必须是枚举类型（list）且为单值
        if (!"list".equals(condField.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条件源字段必须是列表(枚举)类型");
        }
        if (Boolean.TRUE.equals(condField.getIsMulti())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条件源字段必须是单值选择（不支持多值字段作为条件源）");
        }

        // 条件源字段不能是自身
        if (conditionFieldId.equals(fieldId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段不能以自身作为条件源");
        }

        // 验证条件源字段也在同一项目中可用
        List<CustomFieldDefinition> projectFields = listByProject(projectId, null);
        boolean condFieldInProject = projectFields.stream()
                .anyMatch(f -> f.getId().equals(conditionFieldId));
        if (!condFieldInProject) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条件源字段未在本项目中启用");
        }

        // 禁止链式依赖：条件源字段本身不能也有条件
        CustomFieldProject condMapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, conditionFieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));
        if (condMapping != null && condMapping.getConditionFieldId() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持链式条件依赖（条件源字段本身已有条件）");
        }

        // 4. 验证 conditionValues 中的选项 ID 确实属于条件源字段
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

        // 5. 保存条件
        mapping.setConditionFieldId(conditionFieldId);
        mapping.setConditionValues(toJsonArray(conditionValues));
        if (mapping.getId() != null) {
            projectMapper.updateById(mapping);
        } else {
            projectMapper.insert(mapping);
        }
    }

    /**
     * 清除项目中某字段被条件隐藏的 issue 上的值
     * 即：找到该字段有值、但条件不满足的 issue，清空其值
     */
    @Transactional
    public int clearHiddenValues(Long projectId, Long fieldId) {
        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));

        if (mapping == null || mapping.getConditionFieldId() == null) {
            return 0; // 无条件，无需清除
        }

        Long condFieldId = mapping.getConditionFieldId();
        List<String> condValues = parseJsonArray(mapping.getConditionValues());

        // 查找项目中所有 issue 的条件字段值
        // 只需要关注目标字段有值的 issue
        List<CustomFieldValue> targetValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getCustomFieldId, fieldId)
                        .apply("issue_id IN (SELECT id FROM issue WHERE project_id = {0})", projectId));

        if (targetValues.isEmpty()) return 0;

        Set<Long> targetIssueIds = targetValues.stream()
                .map(CustomFieldValue::getIssueId)
                .collect(Collectors.toSet());

        // 获取这些 issue 对应的条件字段当前值
        Map<Long, String> condFieldValuesByIssue = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getCustomFieldId, condFieldId)
                        .in(CustomFieldValue::getIssueId, targetIssueIds))
                .stream()
                .collect(Collectors.toMap(CustomFieldValue::getIssueId, CustomFieldValue::getValue, (a, b) -> a));

        // 筛选条件不满足的 issue（条件字段值不在 condValues 列表中）
        int cleared = 0;
        for (CustomFieldValue tv : targetValues) {
            String currentCondValue = condFieldValuesByIssue.get(tv.getIssueId());
            if (currentCondValue == null || !condValues.contains(currentCondValue)) {
                // 条件不满足 → 清除值
                valueMapper.deleteById(tv.getId());
                cleared++;
            }
        }
        return cleared;
    }

    // ===== JSON 数组辅助 =====

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
     * 解析 JSON 数组字符串为 List<String>
     */
    public List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        // 简单解析 ["a","b","c"] 格式
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

    // ===== Group-based Field Visibility (Role-based) =====

    /**
     * 解析 JSONB 角色 ID 数组 (格式: [2,7] 或 ["2","7"])
     */
    public List<Long> parseRoleIds(String json) {
        if (json == null || json.isBlank()) return null;
        List<String> raw = parseJsonArray(json);
        if (raw.isEmpty()) return null;
        return raw.stream().map(Long::parseLong).toList();
    }

    /**
     * 角色 ID 列表转 JSON 数组字符串
     */
    private String roleIdsToJson(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return null;
        return "[" + roleIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    /**
     * 设置字段的可见性和可编辑性角色限制（项目级）
     */
    @Transactional
    public void setFieldVisibility(Long projectId, Long fieldId, List<Long> visibleToRoles, List<Long> updatableByRoles) {
        // 验证字段存在
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }

        // 查找或创建项目关联记录
        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));

        if (mapping == null) {
            // 全局字段可能没有 mapping 记录，为其创建一个
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

    /**
     * 设置字段的项目级覆盖（必填性 + 默认值）。
     * @param projectId    项目 ID
     * @param fieldId      字段 ID
     * @param isRequired   项目级必填性覆盖（null = 继承全局）
     * @param defaultValue 项目级默认值覆盖（null = 继承全局）
     */
    @Transactional
    public void setFieldProjectOverride(Long projectId, Long fieldId, Boolean isRequired, String defaultValue) {
        // 验证字段存在
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }

        // 查找或创建项目关联记录
        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));

        if (mapping == null) {
            // 全局字段可能没有 mapping 记录，为其创建一个
            if (!Boolean.TRUE.equals(field.getIsForAll())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "字段未附加到此项目");
            }
            mapping = new CustomFieldProject();
            mapping.setCustomFieldId(fieldId);
            mapping.setProjectId(projectId);
            mapping.setPosition(0);
            mapping.setIsRequired(isRequired);
            mapping.setDefaultValue(defaultValue);
            projectMapper.insert(mapping);
        } else {
            // Use LambdaUpdateWrapper to explicitly SET null values (updateById skips nulls)
            projectMapper.update(null, new LambdaUpdateWrapper<CustomFieldProject>()
                    .eq(CustomFieldProject::getId, mapping.getId())
                    .set(CustomFieldProject::getIsRequired, isRequired)
                    .set(CustomFieldProject::getDefaultValue, defaultValue));
        }

        log.info("Updated field project override: project={}, field={}, isRequired={}, defaultValue={}",
                projectId, fieldId, isRequired, defaultValue);
    }

    /**
     * 获取当前用户在项目中的角色 ID 列表。
     * 系统管理员返回 null（表示不受限制）。
     */
    public List<Long> getCurrentUserRoleIds(Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return List.of();

        // 系统管理员不受字段可见性限制
        if (permissionService.hasGlobalPermission(userId, "system:admin")) {
            return null; // null 表示"全部可见"
        }

        return projectMemberMapper.selectRoleIdsByUserAndProject(userId, projectId);
    }

    /**
     * 判断用户角色是否满足可见性要求
     * @param restrictedRoles 限制的角色列表（null=所有人可见）
     * @param userRoleIds 用户角色列表（null=系统管理员，无限制）
     */
    public boolean isVisibleToUser(List<Long> restrictedRoles, List<Long> userRoleIds) {
        // 系统管理员（userRoleIds == null）始终可见
        if (userRoleIds == null) return true;
        // 无限制（restrictedRoles == null 或空）所有人可见
        if (restrictedRoles == null || restrictedRoles.isEmpty()) return true;
        // 检查用户角色是否与限制角色有交集
        for (Long roleId : userRoleIds) {
            if (restrictedRoles.contains(roleId)) return true;
        }
        return false;
    }

    /**
     * 判断用户角色是否满足编辑权限要求
     */
    public boolean isUpdatableByUser(List<Long> updatableRoles, List<Long> userRoleIds) {
        return isVisibleToUser(updatableRoles, userRoleIds);
    }

    /**
     * 检查当前用户是否有权编辑指定字段。
     * 如果不可编辑，抛出 403 异常。
     */
    public void checkFieldEditable(Long projectId, Long fieldId) {
        List<Long> userRoleIds = getCurrentUserRoleIds(projectId);
        if (userRoleIds == null) return; // 系统管理员

        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));
        if (mapping == null) return; // 无限制

        List<Long> updatableRoles = parseRoleIds(mapping.getUpdatableByRoles());
        if (!isUpdatableByUser(updatableRoles, userRoleIds)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "您没有编辑此字段的权限");
        }
    }

    /**
     * 根据用户角色过滤字段列表（移除不可见字段），并计算 editable 标记。
     * 返回可见字段及其 mapping 信息。
     */
    public List<CustomFieldDefinition> filterFieldsByVisibility(
            List<CustomFieldDefinition> fields,
            Map<Long, CustomFieldProject> conditionsMap,
            List<Long> userRoleIds) {
        if (userRoleIds == null) {
            // 系统管理员：不过滤
            return fields;
        }

        List<CustomFieldDefinition> visible = new ArrayList<>();
        for (CustomFieldDefinition field : fields) {
            CustomFieldProject mapping = conditionsMap.get(field.getId());
            List<Long> visibleRoles = mapping != null ? parseRoleIds(mapping.getVisibleToRoles()) : null;
            if (isVisibleToUser(visibleRoles, userRoleIds)) {
                visible.add(field);
            }
        }
        return visible;
    }
}
