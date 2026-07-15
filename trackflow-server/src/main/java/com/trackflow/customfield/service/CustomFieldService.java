package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.customfield.dto.CreateCustomFieldDTO;
import com.trackflow.customfield.dto.UpdateCustomFieldDTO;
import com.trackflow.customfield.entity.*;
import com.trackflow.customfield.mapper.*;
import com.trackflow.customfield.vo.AvailableColumnVO;
import com.trackflow.customfield.vo.CustomFieldValueVO;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
        definitionMapper.updateById(entity);

        if ("list".equals(entity.getFieldFormat()) && dto.getOptions() != null) {
            optionMapper.delete(new LambdaQueryWrapper<CustomFieldOption>()
                    .eq(CustomFieldOption::getCustomFieldId, id));
            for (int i = 0; i < dto.getOptions().size(); i++) {
                UpdateCustomFieldDTO.OptionItem opt = dto.getOptions().get(i);
                CustomFieldOption option = new CustomFieldOption();
                option.setCustomFieldId(id);
                option.setValue(opt.getValue());
                option.setPosition(i);
                option.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
                option.setCreatedAt(LocalDateTime.now());
                option.setUpdatedAt(LocalDateTime.now());
                optionMapper.insert(option);
            }
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

        return entity;
    }

    @Transactional
    public void delete(Long id) {
        if (definitionMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "自定义字段不存在");
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
            if (!fieldMap.containsKey(entry.getKey())) continue;

            CustomFieldValue existing = valueMapper.selectOne(
                    new LambdaQueryWrapper<CustomFieldValue>()
                            .eq(CustomFieldValue::getIssueId, issueId)
                            .eq(CustomFieldValue::getCustomFieldId, entry.getKey()));

            if (existing != null) {
                existing.setValue(entry.getValue());
                existing.setUpdatedAt(LocalDateTime.now());
                valueMapper.updateById(existing);
            } else {
                CustomFieldValue cfv = new CustomFieldValue();
                cfv.setIssueId(issueId);
                cfv.setCustomFieldId(entry.getKey());
                cfv.setValue(entry.getValue());
                cfv.setCreatedAt(LocalDateTime.now());
                cfv.setUpdatedAt(LocalDateTime.now());
                valueMapper.insert(cfv);
            }
        }
    }

    public Map<Long, String> getValues(Long issueId) {
        return valueMapper.selectList(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getIssueId, issueId))
                .stream()
                .collect(Collectors.toMap(CustomFieldValue::getCustomFieldId, CustomFieldValue::getValue));
    }

    /**
     * 批量获取多个 Issue 的自定义字段展示值
     * 返回 Map<issueId, Map<"cf_{fieldId}", displayValue>>
     * list 类型解析为选项文本，user 类型解析为用户名
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

        // 3. 预加载 list 类型字段的选项映射 (optionId → optionValue)
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

        // 4. 预加载 user 类型字段引用的用户名
        Set<Long> userFieldIds = fieldDefMap.values().stream()
                .filter(f -> "user".equals(f.getFieldFormat()))
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());
        Map<Long, String> userNameMap = new HashMap<>();
        if (!userFieldIds.isEmpty()) {
            Set<Long> userIds = allValues.stream()
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

        // 5. 组装结果
        Map<Long, Map<String, String>> result = new HashMap<>();
        for (CustomFieldValue cfv : allValues) {
            CustomFieldDefinition fieldDef = fieldDefMap.get(cfv.getCustomFieldId());
            if (fieldDef == null) continue;

            String displayValue = resolveDisplayValue(cfv.getValue(), fieldDef, optionTextMap, userNameMap);
            result.computeIfAbsent(cfv.getIssueId(), k -> new HashMap<>())
                    .put("cf_" + cfv.getCustomFieldId(), displayValue);
        }

        return result;
    }

    /**
     * 将原始值转换为用户可读的展示值
     */
    private String resolveDisplayValue(String rawValue, CustomFieldDefinition fieldDef,
                                       Map<Long, String> optionTextMap, Map<Long, String> userNameMap) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return switch (fieldDef.getFieldFormat()) {
            case "list" -> {
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
        Map<Long, String> rawValues = getValues(issueId);
        if (rawValues.isEmpty()) return List.of();

        List<CustomFieldDefinition> applicableFields = listByProject(projectId, issueType);
        Map<Long, CustomFieldDefinition> fieldMap = applicableFields.stream()
                .collect(Collectors.toMap(CustomFieldDefinition::getId, f -> f));

        List<CustomFieldValueVO> result = new ArrayList<>();
        for (Map.Entry<Long, String> entry : rawValues.entrySet()) {
            CustomFieldDefinition field = fieldMap.get(entry.getKey());
            if (field == null) continue; // 字段已删除或不再适用

            CustomFieldValueVO vo = new CustomFieldValueVO();
            vo.setCustomFieldId(String.valueOf(entry.getKey()));
            vo.setFieldName(field.getName());
            vo.setFieldFormat(field.getFieldFormat());
            vo.setValue(entry.getValue());
            vo.setDisplayValue(resolveDisplayValue(field, entry.getValue()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 将存储值转为前端可读展示值
     */
    private String resolveDisplayValue(CustomFieldDefinition field, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return "";
        switch (field.getFieldFormat()) {
            case "list" -> {
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
     * 保存单个自定义字段值（用于侧边栏内联编辑）
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

        // Upsert
        CustomFieldValue existing = valueMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getIssueId, issueId)
                        .eq(CustomFieldValue::getCustomFieldId, customFieldId));

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

        // 清除该项目所有工单中该字段的值
        // 获取项目中所有 issue 的 ID
        // 注意：这里直接用 SQL 删除更高效
        valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getCustomFieldId, customFieldId)
                .inSql(CustomFieldValue::getIssueId,
                        "SELECT id FROM issue WHERE project_id = " + projectId));
    }

    /**
     * 调整字段在项目中的显示顺序
     */
    @Transactional
    public void reorderProjectFields(Long projectId, List<Long> fieldIds) {
        for (int i = 0; i < fieldIds.size(); i++) {
            CustomFieldProject mapping = projectMapper.selectOne(
                    new LambdaQueryWrapper<CustomFieldProject>()
                            .eq(CustomFieldProject::getProjectId, projectId)
                            .eq(CustomFieldProject::getCustomFieldId, fieldIds.get(i)));
            if (mapping != null) {
                mapping.setPosition(i);
                projectMapper.updateById(mapping);
            }
        }
    }
}
