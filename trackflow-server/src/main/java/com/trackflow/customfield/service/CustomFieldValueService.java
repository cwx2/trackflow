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

    private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

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
     * 仅应用默认值，不校验必填字段。
     * 用于子工单创建等场景，允许快速创建而不被必填字段阻塞。
     */
    public Map<Long, String> applyDefaultsOnly(Map<Long, String> userProvided, String issueType,
                                               Long projectId, List<CustomFieldDefinition> applicableFields) {
        if (applicableFields.isEmpty()) {
            return userProvided != null ? userProvided : new HashMap<>();
        }

        Map<Long, CustomFieldProject> projectOverrides = getProjectFieldConditions(projectId);

        Map<Long, String> merged = new HashMap<>();
        if (userProvided != null) {
            merged.putAll(userProvided);
        }

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
            }
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

        // 构建条件评估上下文（使用 MultiMap 避免多值字段逗号拼接歧义）
        Map<Long, List<String>> conditionContextMulti;
        if (mode == CustomFieldValidateMode.PARTIAL) {
            conditionContextMulti = new HashMap<>(getValuesAsMultiMap(issueId));
            // 合并用户本次提交的值（覆盖已有值）
            for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
                String val = entry.getValue();
                if (val == null || val.isBlank()) continue;
                CustomFieldDefinition field = fieldMap.get(entry.getKey());
                if (field != null && Boolean.TRUE.equals(field.getIsMulti())) {
                    conditionContextMulti.put(entry.getKey(), parseMultiValueInput(val));
                } else {
                    conditionContextMulti.put(entry.getKey(), List.of(val));
                }
            }
        } else {
            conditionContextMulti = new HashMap<>();
            for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
                String val = entry.getValue();
                if (val == null || val.isBlank()) continue;
                CustomFieldDefinition field = fieldMap.get(entry.getKey());
                if (field != null && Boolean.TRUE.equals(field.getIsMulti())) {
                    conditionContextMulti.put(entry.getKey(), parseMultiValueInput(val));
                } else {
                    conditionContextMulti.put(entry.getKey(), List.of(val));
                }
            }
        }

        for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
            CustomFieldDefinition field = fieldMap.get(entry.getKey());
            if (field == null) continue;
            CustomFieldProject override = projectOverrides.get(entry.getKey());

            Boolean effectiveRequired = (override != null && override.getIsRequired() != null)
                    ? override.getIsRequired() : null;
            if (!isFieldConditionMetMulti(override, conditionContextMulti)) {
                effectiveRequired = false;
            }
            allErrors.addAll(validationEngine.validate(field, entry.getValue(), projectId, effectiveRequired));
        }

        // 必填字段检查
        for (CustomFieldDefinition field : applicableFields) {
            CustomFieldProject mapping = projectOverrides.get(field.getId());
            if (!isFieldConditionMetMulti(mapping, conditionContextMulti)) {
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
     * 级联清除依赖字段的失效值。
     * <p>
     * 当源字段（filterFieldId）的值变更后，检查同项目中所有依赖该字段进行值过滤的字段，
     * 如果依赖字段当前存储的值不在新过滤规则允许的选项集合中，则自动清除并记录活动日志。
     * <p>
     * 对标 YouTrack 行为："If a user selects a value in the dependent field that does not match
     * the current filtering conditions, the system automatically clears the value for the dependent field."
     *
     * @param issueId      工单 ID
     * @param sourceFieldId 刚被修改的源字段 ID
     * @param newSourceValue 源字段的新值
     * @param projectId    项目 ID
     * @return 被级联清除的字段名称列表（用于前端提示）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> cascadeClearDependentValues(Long issueId, Long sourceFieldId, String newSourceValue, Long projectId) {
        // 查找所有依赖此源字段的 custom_field_project 记录
        List<CustomFieldProject> dependents = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .eq(CustomFieldProject::getFilterFieldId, sourceFieldId)
                        .isNotNull(CustomFieldProject::getFilterRules));

        if (dependents.isEmpty()) {
            return List.of();
        }

        List<String> clearedFieldNames = new ArrayList<>();

        for (CustomFieldProject dep : dependents) {
            Long depFieldId = dep.getCustomFieldId();
            String filterRulesJson = dep.getFilterRules();

            // 解析 filterRules 确定允许的选项 ID 集
            Set<String> allowedOptionIds = resolveAllowedOptions(filterRulesJson, newSourceValue);

            // 如果 allowedOptionIds 为 null，表示无限制（不清除）
            if (allowedOptionIds == null) {
                continue;
            }

            // 获取该依赖字段当前的存储值
            List<CustomFieldValue> storedValues = valueMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldValue>()
                            .eq(CustomFieldValue::getIssueId, issueId)
                            .eq(CustomFieldValue::getCustomFieldId, depFieldId));

            if (storedValues.isEmpty()) {
                continue;
            }

            // 检查存储值是否在允许集合中
            List<CustomFieldValue> invalidValues = storedValues.stream()
                    .filter(v -> v.getValue() != null && !allowedOptionIds.contains(v.getValue()))
                    .toList();

            if (invalidValues.isEmpty()) {
                continue;
            }

            // 清除无效值
            CustomFieldDefinition fieldDef = definitionMapper.selectById(depFieldId);
            String fieldName = fieldDef != null ? fieldDef.getName() : "ID:" + depFieldId;

            // 记录活动日志
            if (fieldDef != null) {
                boolean isMulti = Boolean.TRUE.equals(fieldDef.getIsMulti());
                if (isMulti) {
                    List<String> oldVals = storedValues.stream().map(CustomFieldValue::getValue).toList();
                    List<String> remainingVals = storedValues.stream()
                            .filter(v -> v.getValue() != null && allowedOptionIds.contains(v.getValue()))
                            .map(CustomFieldValue::getValue)
                            .toList();
                    String displayOld = displayService.resolveMultiDisplayValue(fieldDef, oldVals);
                    String displayNew = remainingVals.isEmpty() ? null : displayService.resolveMultiDisplayValue(fieldDef, remainingVals);
                    recordCustomFieldActivity(issueId, fieldName, displayOld, displayNew);

                    // 删除无效值记录
                    List<Long> invalidIds = invalidValues.stream().map(CustomFieldValue::getId).toList();
                    valueMapper.deleteBatchIds(invalidIds);
                } else {
                    String oldVal = storedValues.get(0).getValue();
                    String displayOld = displayService.resolveDisplayValue(fieldDef, oldVal);
                    recordCustomFieldActivity(issueId, fieldName, displayOld, null);

                    // 删除值记录
                    valueMapper.deleteById(storedValues.get(0).getId());
                }
            } else {
                // 字段定义不存在（理论上不会发生），直接删除
                List<Long> invalidIds = invalidValues.stream().map(CustomFieldValue::getId).toList();
                valueMapper.deleteBatchIds(invalidIds);
            }

            clearedFieldNames.add(fieldName);
            log.info("Issue {}: cascade cleared field '{}' (value no longer valid after source field {} changed to '{}')",
                    issueId, fieldName, sourceFieldId, newSourceValue);
        }

        return clearedFieldNames;
    }

    /**
     * 解析 filterRules JSON，根据源字段新值确定允许的选项 ID 集合。
     *
     * @param filterRulesJson filterRules JSON 字符串，格式: [{"whenValue":"optId1","showOnly":["optId3","optId4"]}]
     * @param sourceValue     源字段的当前值
     * @return 允许的选项 ID 集合；null 表示无限制（不需要清除）
     */
    private Set<String> resolveAllowedOptions(String filterRulesJson, String sourceValue) {
        if (filterRulesJson == null || filterRulesJson.isBlank()) {
            return null; // 无规则 → 无限制
        }

        List<Map<String, Object>> rules = parseFilterRules(filterRulesJson);
        if (rules.isEmpty()) {
            return null;
        }

        // 如果源字段为空/null，则没有激活的规则 → 所有选项有效
        if (sourceValue == null || sourceValue.isBlank()) {
            return null;
        }

        // 查找匹配当前源值的规则
        for (Map<String, Object> rule : rules) {
            String whenValue = (String) rule.get("whenValue");
            if (sourceValue.equals(whenValue)) {
                @SuppressWarnings("unchecked")
                List<String> showOnly = (List<String>) rule.get("showOnly");
                if (showOnly != null && !showOnly.isEmpty()) {
                    return new HashSet<>(showOnly);
                }
                return null; // 规则匹配但 showOnly 为空 → 无限制
            }
        }

        // 没有匹配规则 → 无限制（所有选项有效）
        return null;
    }

    /**
     * 解析 filterRules JSON 为结构化的 rule 列表。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFilterRules(String json) {
        if (json == null || json.isBlank()) return List.of();
        String trimmed = json.trim();
        if (!trimmed.startsWith("[")) return List.of();

        try {
            List<Map<String, Object>> result = new ArrayList<>();
            com.fasterxml.jackson.databind.ObjectMapper mapper = JACKSON_MAPPER;
            List<Map<String, Object>> parsed = mapper.readValue(trimmed,
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            for (Map<String, Object> item : parsed) {
                if (item.containsKey("whenValue")) {
                    Map<String, Object> rule = new HashMap<>();
                    rule.put("whenValue", String.valueOf(item.get("whenValue")));
                    Object showOnlyRaw = item.get("showOnly");
                    if (showOnlyRaw instanceof List<?> list) {
                        rule.put("showOnly", list.stream().map(String::valueOf).toList());
                    }
                    result.add(rule);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse filterRules JSON: {}", json, e);
            return List.of();
        }
    }

    /**
     * 获取字段值 Map（扁平格式，向后兼容）。
     * 单值字段返回单个值字符串，多值字段以逗号分隔聚合返回。
     *
     * <p><b>注意：</b>多值字段返回逗号拼接字符串，消费者需要知道字段是否为多值才能正确解析。
     * 如需明确区分单值/多值，请使用 {@link #getValuesAsMultiMap(Long)}。
     */
    public Map<Long, String> getValues(Long issueId) {
        Map<Long, List<String>> multiMap = getValuesAsMultiMap(issueId);
        Map<Long, String> result = new HashMap<>();
        for (Map.Entry<Long, List<String>> entry : multiMap.entrySet()) {
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
     * 获取字段值 MultiMap（明确区分单值/多值）。
     * 所有字段统一返回 List&lt;String&gt;：单值字段为 1 元素列表，多值字段为 N 元素列表。
     *
     * <p>此方法是条件判定、活动记录等场景的首选，避免逗号分隔的歧义。
     */
    public Map<Long, List<String>> getValuesAsMultiMap(Long issueId) {
        List<CustomFieldValue> allValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getIssueId, issueId));

        return allValues.stream()
                .collect(Collectors.groupingBy(
                        CustomFieldValue::getCustomFieldId,
                        Collectors.mapping(CustomFieldValue::getValue, Collectors.toList())));
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
        Map<Long, List<String>> currentValuesMulti = getValuesAsMultiMap(issueId);
        if (currentValuesMulti.isEmpty()) return List.of();

        Set<Long> applicableFieldIds = applicableFields.stream()
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());

        List<Long> orphanFieldIds = currentValuesMulti.keySet().stream()
                .filter(fieldId -> !applicableFieldIds.contains(fieldId))
                .toList();

        if (orphanFieldIds.isEmpty()) return List.of();

        Map<Long, CustomFieldDefinition> fieldMap = definitionMapper.selectBatchIds(orphanFieldIds)
                .stream()
                .collect(Collectors.toMap(CustomFieldDefinition::getId, f -> f));

        for (Long fieldId : orphanFieldIds) {
            CustomFieldDefinition field = fieldMap.get(fieldId);
            List<String> values = currentValuesMulti.get(fieldId);
            if (field != null && values != null && !values.isEmpty()) {
                // 使用多值感知的显示值解析，避免逗号拼接字符串传入 resolveDisplayValue
                String displayValue;
                if (values.size() > 1 || Boolean.TRUE.equals(field.getIsMulti())) {
                    displayValue = displayService.resolveMultiDisplayValue(field, values);
                } else {
                    displayValue = displayService.resolveDisplayValue(field, values.get(0));
                }
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
        if (CustomFieldOptionService.isEnumLikeFormat(field.getFieldFormat())) {
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
        
        // 检查"无默认值但必填"模式（canBeEmpty=false 且无项目级默认值）
        // 此模式下不自动应用选项表的 isDefault=true 选项
        if (projectMapping != null && Boolean.FALSE.equals(projectMapping.getCanBeEmpty())) {
            // 如果项目配置了 canBeEmpty=false 但没有设置 defaultValue，
            // 说明是"无默认值但必填"模式，返回 null 让用户主动选择
            return null;
        }
        
        return resolveDefaultValue(field);
    }

    private boolean isFieldRequired(CustomFieldDefinition field, CustomFieldProject projectMapping) {
        if (projectMapping != null && projectMapping.getIsRequired() != null) {
            return projectMapping.getIsRequired();
        }
        // 如果项目配置了 canBeEmpty=false，则该字段也是必填的
        if (projectMapping != null && Boolean.FALSE.equals(projectMapping.getCanBeEmpty())) {
            return true;
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
        // 处理多值场景：conditionFieldValue 可能是逗号分隔的多个值
        // ANY 语义：只要有一个值匹配 conditionValues 就满足条件
        if (conditionFieldValue.contains(",")) {
            List<String> fieldValues = Arrays.stream(conditionFieldValue.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            return fieldValues.stream().anyMatch(conditionValues::contains);
        }
        return conditionValues.contains(conditionFieldValue);
    }

    /**
     * 条件判定（基于 MultiMap，无歧义版本）。
     * 多值字段使用 ANY 语义：只要该字段的任一值匹配条件列表中的某个值，即视为满足条件。
     */
    private boolean isFieldConditionMetMulti(CustomFieldProject mapping, Map<Long, List<String>> currentValues) {
        if (mapping == null || mapping.getConditionFieldId() == null) {
            return true;
        }
        List<String> fieldValues = currentValues.get(mapping.getConditionFieldId());
        if (fieldValues == null || fieldValues.isEmpty()) {
            return false;
        }
        List<String> conditionValues = parseJsonArray(mapping.getConditionValues());
        if (conditionValues.isEmpty()) {
            return true;
        }
        // ANY 语义：只要有一个值匹配条件列表即满足
        return fieldValues.stream().anyMatch(conditionValues::contains);
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
