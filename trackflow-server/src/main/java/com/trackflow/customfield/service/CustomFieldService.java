package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        definitionMapper.insert(entity);

        if ("list".equals(dto.getFieldFormat()) && dto.getOptions() != null) {
            for (int i = 0; i < dto.getOptions().size(); i++) {
                CreateCustomFieldDTO.OptionItem opt = dto.getOptions().get(i);
                CustomFieldOption option = new CustomFieldOption();
                option.setCustomFieldId(entity.getId());
                option.setValue(opt.getValue());
                option.setPosition(i);
                option.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
                option.setCreatedAt(LocalDateTime.now());
                option.setUpdatedAt(LocalDateTime.now());
                optionMapper.insert(option);
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
            entity.setIsMulti(dto.getIsMulti());
        }
        definitionMapper.updateById(entity);

        if ("list".equals(entity.getFieldFormat()) && dto.getOptions() != null) {
            updateListOptions(id, dto.getOptions());
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
        for (int i = 0; i < dtoOptions.size(); i++) {
            UpdateCustomFieldDTO.OptionItem opt = dtoOptions.get(i);
            if (opt.getId() != null) {
                // 已有选项 → UPDATE
                CustomFieldOption existing = existingMap.get(opt.getId());
                if (existing == null) {
                    // ID 无效，当作新增处理
                    insertNewOption(customFieldId, opt, i);
                } else {
                    existing.setValue(opt.getValue());
                    existing.setPosition(i);
                    existing.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
                    existing.setIsArchived(false); // 如果之前被归档，恢复
                    existing.setUpdatedAt(LocalDateTime.now());
                    optionMapper.updateById(existing);
                }
            } else {
                // 新选项 → INSERT
                insertNewOption(customFieldId, opt, i);
            }
        }

        // 4. 处理 DB 中存在但 DTO 中不存在的选项（被移除的）
        Set<Long> removedIds = existingMap.keySet().stream()
                .filter(id -> !dtoReferencedIds.contains(id))
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
    }

    /**
     * 插入新的选项记录
     */
    private void insertNewOption(Long customFieldId, UpdateCustomFieldDTO.OptionItem opt, int position) {
        CustomFieldOption option = new CustomFieldOption();
        option.setCustomFieldId(customFieldId);
        option.setValue(opt.getValue());
        option.setPosition(position);
        option.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
        option.setIsArchived(false);
        option.setCreatedAt(LocalDateTime.now());
        option.setUpdatedAt(LocalDateTime.now());
        optionMapper.insert(option);
    }

    /**
     * 检查指定选项 ID 是否被任何工单的 custom_field_value 引用。
     * list 字段值存储方式：单选存 option ID 字符串，多选存逗号分隔的 ID 列表。
     */
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

        // 所有自定义字段
        List<CustomFieldDefinition> allFields = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
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

    @Transactional
    public void saveValues(Long issueId, Map<Long, String> fieldValues, String issueType, Long projectId) {
        if (fieldValues == null || fieldValues.isEmpty()) return;

        List<CustomFieldDefinition> applicableFields = listByProject(projectId, issueType);
        Map<Long, CustomFieldDefinition> fieldMap = applicableFields.stream()
                .collect(Collectors.toMap(CustomFieldDefinition::getId, f -> f));

        List<CustomFieldValidationEngine.FieldValidationError> allErrors = new ArrayList<>();

        for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
            CustomFieldDefinition field = fieldMap.get(entry.getKey());
            if (field == null) continue;
            allErrors.addAll(validationEngine.validate(field, entry.getValue()));
        }

        for (CustomFieldDefinition field : applicableFields) {
            if (Boolean.TRUE.equals(field.getIsRequired()) && !fieldValues.containsKey(field.getId())) {
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
                // 单值字段：原有逻辑
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

        // 删除不再适用的字段值
        valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getIssueId, issueId)
                .in(CustomFieldValue::getCustomFieldId, orphanFieldIds));

        log.info("Issue {} type changed to {}: removed {} orphan custom field values (fieldIds: {})",
                issueId, newIssueType, orphanFieldIds.size(), orphanFieldIds);

        return orphanFieldIds;
    }

    /**
     * 批量获取多个 Issue 的自定义字段展示值
     * 返回 Map<issueId, Map<"cf_{fieldId}", displayValue>>
     * list 类型解析为选项文本，user 类型解析为用户名
     *
     * 仅返回对每个 issue 所在项目适用的字段值（过滤孤立数据）。
     */
    public Map<Long, Map<String, String>> getBatchDisplayValues(List<Long> issueIds) {
        if (issueIds == null || issueIds.isEmpty()) {
            return Map.of();
        }

        // 1. 批量加载所有相关 custom_field_value 记录
        List<CustomFieldValue> allValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .in(CustomFieldValue::getIssueId, issueIds));
        if (allValues.isEmpty()) {
            return Map.of();
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
            return Map.of();
        }

        // 5. 预加载 list 类型字段的选项映射 (optionId → optionValue)
        Set<Long> listFieldIds = fieldDefMap.values().stream()
                .filter(f -> "list".equals(f.getFieldFormat()))
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());
        Map<Long, String> optionTextMap = new HashMap<>();
        if (!listFieldIds.isEmpty()) {
            List<CustomFieldOption> options = optionMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldOption>()
                            .in(CustomFieldOption::getCustomFieldId, listFieldIds));
            for (CustomFieldOption opt : options) {
                optionTextMap.put(opt.getId(), opt.getValue());
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

            for (Map.Entry<Long, List<String>> fieldEntry : issueEntry.getValue().entrySet()) {
                Long fieldId = fieldEntry.getKey();
                List<String> values = fieldEntry.getValue();
                CustomFieldDefinition fieldDef = fieldDefMap.get(fieldId);
                if (fieldDef == null) continue;

                if (Boolean.TRUE.equals(fieldDef.getIsMulti())) {
                    // 多值字段：每行一个值，聚合展示
                    List<String> labels = new ArrayList<>();
                    for (String v : values) {
                        String display = resolveDisplayValue(v, fieldDef, optionTextMap, userNameMap);
                        if (display != null) labels.add(display);
                    }
                    fieldDisplayMap.put("cf_" + fieldId, String.join(", ", labels));
                } else {
                    // 单值字段：取第一条
                    String displayValue = resolveDisplayValue(values.get(0), fieldDef, optionTextMap, userNameMap);
                    fieldDisplayMap.put("cf_" + fieldId, displayValue);
                }
            }

            result.put(issueId, fieldDisplayMap);
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
            default -> rawValue;
        };
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
                for (String v : values) {
                    displayValues.add(resolveDisplayValue(field, v));
                }
                vo.setDisplayValues(displayValues);
                // 兼容：value/displayValue 用逗号分隔聚合
                vo.setValue(String.join(",", values));
                vo.setDisplayValue(String.join(", ", displayValues));
            } else {
                // 单值字段：原有逻辑
                String singleValue = values.get(0);
                vo.setValue(singleValue);
                vo.setDisplayValue(resolveDisplayValue(field, singleValue));
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
            default -> { return rawValue; }
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

        // 验证值
        List<CustomFieldValidationEngine.FieldValidationError> errors = validationEngine.validate(field, value);
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

            // 清空值检查
            if (newValues.isEmpty() && Boolean.TRUE.equals(field.getIsRequired())) {
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
            // 单值字段：原有逻辑
            CustomFieldValue existing = valueMapper.selectOne(
                    new LambdaQueryWrapper<CustomFieldValue>()
                            .eq(CustomFieldValue::getIssueId, issueId)
                            .eq(CustomFieldValue::getCustomFieldId, customFieldId));
            String oldValue = existing != null ? existing.getValue() : null;

            if (value == null || value.isBlank()) {
                // 清空值：如果非必填，允许删除
                if (Boolean.TRUE.equals(field.getIsRequired())) {
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

    public List<CustomFieldOption> getOptions(Long fieldId) {
        return optionMapper.selectList(new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, fieldId)
                .orderByAsc(CustomFieldOption::getPosition));
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
}
