package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldProject;
import com.trackflow.customfield.entity.CustomFieldValue;
import com.trackflow.customfield.mapper.CustomFieldDefinitionMapper;
import com.trackflow.customfield.mapper.CustomFieldOptionMapper;
import com.trackflow.customfield.mapper.CustomFieldProjectMapper;
import com.trackflow.customfield.mapper.CustomFieldValueMapper;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义字段值存储服务 — 处理 EAV 值读写、校验、默认值应用、删除
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldValueService {

    private final CustomFieldDefinitionMapper definitionMapper;
    private final CustomFieldValueMapper valueMapper;
    private final CustomFieldOptionMapper optionMapper;
    private final CustomFieldProjectMapper projectMapper;
    private final CustomFieldValidationEngine validationEngine;
    private final IssueActivityMapper activityMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final PermissionService permissionService;
    private final CustomFieldDisplayService displayService;

    /**
     * 为创建工单场景应用默认值并校验必填字段。
     *
     * @param userProvided 用户显式提供的字段值（可为 null 或空）
     * @param issueType    工单类型
     * @param projectId    项目ID
     * @param applicableFields 项目+类型下适用字段列表
     * @return 合并默认值后的字段值 Map（字段ID → 值）
     */
    public Map<Long, String> applyDefaultsAndValidate(Map<Long, String> userProvided, String issueType,
                                                       Long projectId, List<CustomFieldDefinition> applicableFields) {
        if (applicableFields.isEmpty()) {
            return userProvided != null ? userProvided : new HashMap<>();
        }

        Map<Long, CustomFieldProject> projectOverrides = getProjectFieldConditions(projectId);

        Map<Long, String> merged = new HashMap<>();
        if (userProvided != null) {
            merged.putAll(userProvided);
        }

        List<CustomFieldValidationEngine.FieldValidationError> errors = new ArrayList<>();

        for (CustomFieldDefinition field : applicableFields) {
            if (merged.containsKey(field.getId())) {
                continue;
            }

            CustomFieldProject mapping = projectOverrides.get(field.getId());
            if (!isFieldConditionMet(mapping, merged)) {
                continue;
            }

            String defaultVal = resolveDefaultValueWithOverride(field, mapping);
            if (defaultVal != null && !defaultVal.isBlank()) {
                merged.put(field.getId(), defaultVal);
            } else if (isFieldRequired(field, mapping)) {
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
     * 保存自定义字段值（默认 FULL 模式，向后兼容）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveValues(Long issueId, Map<Long, String> fieldValues, String issueType, Long projectId,
                           List<CustomFieldDefinition> applicableFields) {
        saveValues(issueId, fieldValues, issueType, projectId, applicableFields, CustomFieldValidateMode.FULL);
    }

    /**
     * 保存自定义字段值（指定验证模式）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveValues(Long issueId, Map<Long, String> fieldValues, String issueType, Long projectId,
                           List<CustomFieldDefinition> applicableFields, CustomFieldValidateMode mode) {
        if (fieldValues == null || fieldValues.isEmpty()) return;

        Map<Long, CustomFieldDefinition> fieldMap = applicableFields.stream()
                .collect(Collectors.toMap(CustomFieldDefinition::getId, f -> f));

        Map<Long, CustomFieldProject> projectOverrides = getProjectFieldConditions(projectId);

        // Field-level editability check
        List<Long> userRoleIds = getCurrentUserRoleIds(projectId);
        if (userRoleIds != null) {
            // Check private field edit permissions first
            Long currentUserId = SecurityUtils.getCurrentUserId();
            for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
                CustomFieldDefinition field = fieldMap.get(entry.getKey());
                if (field != null && Boolean.TRUE.equals(field.getIsPrivate())) {
                    if (currentUserId == null || !permissionService.hasPermission(currentUserId, projectId, "issue:update_private_fields")) {
                        String fieldName = field.getName();
                        throw new BusinessException(ErrorCode.ACCESS_DENIED,
                                "您没有编辑私有字段「" + fieldName + "」的权限");
                    }
                }
            }
            // Then check role-based updatableByRoles
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

        // 构建条件评估上下文
        Map<Long, String> conditionContext;
        if (mode == CustomFieldValidateMode.PARTIAL) {
            Map<Long, String> existingValues = getValues(issueId);
            conditionContext = new HashMap<>(existingValues);
            conditionContext.putAll(fieldValues);
        } else {
            conditionContext = fieldValues;
        }

        for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
            CustomFieldDefinition field = fieldMap.get(entry.getKey());
            if (field == null) continue;
            CustomFieldProject override = projectOverrides.get(entry.getKey());

            Boolean effectiveRequired = (override != null && override.getIsRequired() != null)
                    ? override.getIsRequired() : null;
            if (!isFieldConditionMet(override, conditionContext)) {
                effectiveRequired = false;
            }
            allErrors.addAll(validationEngine.validate(field, entry.getValue(), projectId, effectiveRequired));
        }

        // 必填字段检查
        for (CustomFieldDefinition field : applicableFields) {
            CustomFieldProject mapping = projectOverrides.get(field.getId());
            if (!isFieldConditionMet(mapping, conditionContext)) {
                continue;
            }
            if (isFieldRequired(field, mapping) && !fieldValues.containsKey(field.getId())) {
                if (mode == CustomFieldValidateMode.PARTIAL) {
                    continue;
                }
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
                List<String> oldValues = getMultiValues(issueId, entry.getKey());
                List<String> newValues = parseMultiValueInput(rawInput);

                valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getIssueId, issueId)
                        .eq(CustomFieldValue::getCustomFieldId, entry.getKey()));

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

                if (!oldValues.equals(newValues)) {
                    String displayOld = displayService.resolveMultiDisplayValue(field, oldValues);
                    String displayNew = displayService.resolveMultiDisplayValue(field, newValues);
                    recordCustomFieldActivity(issueId, field.getName(), displayOld, displayNew);
                }
            } else {
                valueMapper.acquireSingleValueLock(issueId, entry.getKey());

                CustomFieldValue existing = valueMapper.selectOne(
                        new LambdaQueryWrapper<CustomFieldValue>()
                                .eq(CustomFieldValue::getIssueId, issueId)
                                .eq(CustomFieldValue::getCustomFieldId, entry.getKey()));

                String oldValue = existing != null ? existing.getValue() : null;
                String newValue = rawInput;

                if (newValue == null || newValue.isBlank()) {
                    if (existing != null) {
                        valueMapper.deleteById(existing.getId());
                    }
                } else if (existing != null) {
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

                String effectiveNew = (newValue == null || newValue.isBlank()) ? null : newValue;
                if (!Objects.equals(oldValue, effectiveNew)) {
                    String displayOldValue = displayService.resolveDisplayValue(field, oldValue);
                    String displayNewValue = displayService.resolveDisplayValue(field, effectiveNew);
                    recordCustomFieldActivity(issueId, field.getName(), displayOldValue, displayNewValue);
                }
            }
        }
    }

    /**
     * 保存单个自定义字段值（用于侧边栏内联编辑）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveSingleValue(Long issueId, Long customFieldId, String value, String issueType, Long projectId,
                                 List<CustomFieldDefinition> applicableFields) {
        CustomFieldDefinition field = applicableFields.stream()
                .filter(f -> f.getId().equals(customFieldId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "字段不适用于当前工单"));

        Map<Long, CustomFieldProject> projectOverrides = getProjectFieldConditions(projectId);
        CustomFieldProject override = projectOverrides.get(customFieldId);
        boolean effectiveRequired = isFieldRequired(field, override);

        // 验证值
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
            List<String> oldValues = getMultiValues(issueId, customFieldId);
            List<String> newValues = parseMultiValueInput(value);

            if (newValues.isEmpty() && effectiveRequired) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, field.getName() + " 为必填项，不能清空");
            }

            valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                    .eq(CustomFieldValue::getIssueId, issueId)
                    .eq(CustomFieldValue::getCustomFieldId, customFieldId));

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

            if (!oldValues.equals(newValues)) {
                String displayOld = displayService.resolveMultiDisplayValue(field, oldValues);
                String displayNew = displayService.resolveMultiDisplayValue(field, newValues);
                recordCustomFieldActivity(issueId, field.getName(), displayOld, displayNew);
            }
        } else {
            valueMapper.acquireSingleValueLock(issueId, customFieldId);

            CustomFieldValue existing = valueMapper.selectOne(
                    new LambdaQueryWrapper<CustomFieldValue>()
                            .eq(CustomFieldValue::getIssueId, issueId)
                            .eq(CustomFieldValue::getCustomFieldId, customFieldId));
            String oldValue = existing != null ? existing.getValue() : null;

            if (value == null || value.isBlank()) {
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

            String newValue = (value == null || value.isBlank()) ? null : value;
            if (!Objects.equals(oldValue, newValue)) {
                String displayOldValue = displayService.resolveDisplayValue(field, oldValue);
                String displayNewValue = displayService.resolveDisplayValue(field, newValue);
                recordCustomFieldActivity(issueId, field.getName(), displayOldValue, displayNewValue);
            }
        }
    }

    /**
     * 获取单值字段值 Map。多值字段以逗号分隔聚合返回。
     */
    public Map<Long, String> getValues(Long issueId) {
        List<CustomFieldValue> allValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getIssueId, issueId));

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
     * 删除工单的所有自定义字段值
     */
    public void deleteValuesByIssue(Long issueId) {
        valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getIssueId, issueId));
    }

    /**
     * 删除工单中不再适用于新 issueType 的自定义字段值。
     *
     * @return 被删除的字段 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> removeOrphanValues(Long issueId, String newIssueType, Long projectId,
                                          List<CustomFieldDefinition> applicableFields) {
        Map<Long, String> currentValues = getValues(issueId);
        if (currentValues.isEmpty()) return List.of();

        Set<Long> applicableFieldIds = applicableFields.stream()
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());

        List<Long> orphanFieldIds = currentValues.keySet().stream()
                .filter(fieldId -> !applicableFieldIds.contains(fieldId))
                .toList();

        if (orphanFieldIds.isEmpty()) return List.of();

        Map<Long, CustomFieldDefinition> fieldMap = definitionMapper.selectBatchIds(orphanFieldIds)
                .stream()
                .collect(Collectors.toMap(CustomFieldDefinition::getId, f -> f));

        for (Long fieldId : orphanFieldIds) {
            CustomFieldDefinition field = fieldMap.get(fieldId);
            String rawValue = currentValues.get(fieldId);
            if (field != null && rawValue != null && !rawValue.isBlank()) {
                String displayValue = displayService.resolveDisplayValue(field, rawValue);
                recordCustomFieldActivity(issueId, field.getName(), displayValue, null);
            }
        }

        valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getIssueId, issueId)
                .in(CustomFieldValue::getCustomFieldId, orphanFieldIds));

        log.info("Issue {} type changed to {}: removed {} orphan custom field values (fieldIds: {})",
                issueId, newIssueType, orphanFieldIds.size(), orphanFieldIds);

        return orphanFieldIds;
    }

    /**
     * 检查当前用户是否有权编辑指定字段。
     */
    public void checkFieldEditable(Long projectId, Long fieldId) {
        List<Long> userRoleIds = getCurrentUserRoleIds(projectId);
        if (userRoleIds == null) return; // system admin — skip checks

        // Private field edit permission check
        CustomFieldDefinition fieldDef = definitionMapper.selectById(fieldId);
        if (fieldDef != null && Boolean.TRUE.equals(fieldDef.getIsPrivate())) {
            Long userId = SecurityUtils.getCurrentUserId();
            if (userId == null || !permissionService.hasPermission(userId, projectId, "issue:update_private_fields")) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED,
                        "您没有编辑私有字段「" + fieldDef.getName() + "」的权限");
            }
        }

        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));
        if (mapping == null) return;

        List<Long> updatableRoles = parseRoleIds(mapping.getUpdatableByRoles());
        if (!isUpdatableByUser(updatableRoles, userRoleIds)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "您没有编辑此字段的权限");
        }
    }

    // ========== 私有辅助方法 ==========

    private List<String> getMultiValues(Long issueId, Long customFieldId) {
        return valueMapper.selectList(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getIssueId, issueId)
                .eq(CustomFieldValue::getCustomFieldId, customFieldId)
                .orderByAsc(CustomFieldValue::getId))
                .stream()
                .map(CustomFieldValue::getValue)
                .toList();
    }

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

    private String resolveDefaultValue(CustomFieldDefinition field) {
        if ("list".equals(field.getFieldFormat())) {
            List<com.trackflow.customfield.entity.CustomFieldOption> defaultOptions = optionMapper.selectList(
                    new LambdaQueryWrapper<com.trackflow.customfield.entity.CustomFieldOption>()
                            .eq(com.trackflow.customfield.entity.CustomFieldOption::getCustomFieldId, field.getId())
                            .eq(com.trackflow.customfield.entity.CustomFieldOption::getIsDefault, true)
                            .eq(com.trackflow.customfield.entity.CustomFieldOption::getIsArchived, false));
            if (!defaultOptions.isEmpty()) {
                if (Boolean.TRUE.equals(field.getIsMulti())) {
                    return defaultOptions.stream()
                            .map(o -> String.valueOf(o.getId()))
                            .collect(Collectors.joining(","));
                } else {
                    return String.valueOf(defaultOptions.get(0).getId());
                }
            }
            return null;
        } else {
            return field.getDefaultValue();
        }
    }

    private String resolveDefaultValueWithOverride(CustomFieldDefinition field, CustomFieldProject projectMapping) {
        if (projectMapping != null && projectMapping.getDefaultValue() != null) {
            String projectDefault = projectMapping.getDefaultValue();
            return projectDefault.isEmpty() ? null : projectDefault;
        }
        return resolveDefaultValue(field);
    }

    private boolean isFieldRequired(CustomFieldDefinition field, CustomFieldProject projectMapping) {
        if (projectMapping != null && projectMapping.getIsRequired() != null) {
            return projectMapping.getIsRequired();
        }
        return Boolean.TRUE.equals(field.getIsRequired());
    }

    private boolean isFieldConditionMet(CustomFieldProject mapping, Map<Long, String> currentValues) {
        if (mapping == null || mapping.getConditionFieldId() == null) {
            return true;
        }
        String conditionFieldValue = currentValues.get(mapping.getConditionFieldId());
        if (conditionFieldValue == null || conditionFieldValue.isBlank()) {
            return false;
        }
        List<String> conditionValues = parseJsonArray(mapping.getConditionValues());
        if (conditionValues.isEmpty()) {
            return true;
        }
        return conditionValues.contains(conditionFieldValue);
    }

    Map<Long, CustomFieldProject> getProjectFieldConditions(Long projectId) {
        List<CustomFieldProject> mappings = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId));
        return mappings.stream()
                .collect(Collectors.toMap(CustomFieldProject::getCustomFieldId, m -> m, (a, b) -> a));
    }

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

    private List<Long> getCurrentUserRoleIds(Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return List.of();
        if (permissionService.hasGlobalPermission(userId, "system:admin")) {
            return null;
        }
        return projectMemberMapper.selectRoleIdsByUserAndProject(userId, projectId);
    }

    private boolean isUpdatableByUser(List<Long> updatableRoles, List<Long> userRoleIds) {
        if (userRoleIds == null) return true;
        if (updatableRoles == null || updatableRoles.isEmpty()) return true;
        for (Long roleId : userRoleIds) {
            if (updatableRoles.contains(roleId)) return true;
        }
        return false;
    }

    private List<Long> parseRoleIds(String json) {
        if (json == null || json.isBlank()) return null;
        List<String> raw = parseJsonArray(json);
        if (raw.isEmpty()) return null;
        return raw.stream().map(Long::parseLong).toList();
    }

    private List<String> parseJsonArray(String json) {
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
}
