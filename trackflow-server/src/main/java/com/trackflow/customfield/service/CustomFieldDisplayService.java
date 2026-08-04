package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.entity.CustomFieldProject;
import com.trackflow.customfield.entity.CustomFieldValue;
import com.trackflow.customfield.mapper.CustomFieldDefinitionMapper;
import com.trackflow.customfield.mapper.CustomFieldOptionMapper;
import com.trackflow.customfield.mapper.CustomFieldProjectMapper;
import com.trackflow.customfield.mapper.CustomFieldValueMapper;
import com.trackflow.customfield.vo.CustomFieldValueVO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义字段展示值解析服务 — 将存储值转换为用户可读的展示值（批量/单条）
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldDisplayService {

    private final CustomFieldDefinitionMapper definitionMapper;
    private final CustomFieldValueMapper valueMapper;
    private final CustomFieldOptionMapper optionMapper;
    private final CustomFieldProjectMapper projectMapper;
    private final SysUserMapper userMapper;
    private final IssueMapper issueMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final PermissionService permissionService;

    /**
     * 批量获取多个 Issue 的自定义字段展示值（不含颜色）
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public Map<Long, Map<String, String>> getBatchDisplayValues(
            List<Long> issueIds,
            Map<Long, Map<String, String>> colorOutMap) {

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

        // 3. 构建字段适用性过滤
        Set<Long> nonGlobalFieldIds = fieldDefMap.values().stream()
                .filter(f -> !Boolean.TRUE.equals(f.getIsForAll()))
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());

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

        // 查询 issue→projectId 映射
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getId, Issue::getProjectId)
                        .in(Issue::getId, issueIds));
        Map<Long, Long> issueProjectMap = issues.stream()
                .collect(Collectors.toMap(Issue::getId, Issue::getProjectId));

        // 4. 过滤不适用的字段值
        List<CustomFieldValue> applicableValues = allValues.stream()
                .filter(v -> {
                    CustomFieldDefinition fieldDef = fieldDefMap.get(v.getCustomFieldId());
                    if (fieldDef == null) return false;
                    if (Boolean.TRUE.equals(fieldDef.getIsForAll())) return true;
                    Long projectId = issueProjectMap.get(v.getIssueId());
                    if (projectId == null) return false;
                    Set<Long> allowedProjects = fieldToProjectIds.get(v.getCustomFieldId());
                    return allowedProjects != null && allowedProjects.contains(projectId);
                })
                .toList();

        if (applicableValues.isEmpty()) {
            return new HashMap<>();
        }

        // 4.5 应用 visibleToRoles 过滤
        applicableValues = applyVisibilityFilter(applicableValues, issueProjectMap);
        if (applicableValues.isEmpty()) {
            return new HashMap<>();
        }

        // 5. 预加载 list 类型字段的选项映射
        Set<Long> listFieldIds = fieldDefMap.values().stream()
                .filter(f -> CustomFieldOptionService.isEnumLikeFormat(f.getFieldFormat()))
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

        // 7. 按 (issueId, fieldId) 分组处理
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
                    List<String> labels = new ArrayList<>();
                    for (String v : values) {
                        String display = resolveDisplayValue(v, fieldDef, optionTextMap, userNameMap);
                        if (display != null) labels.add(display);
                    }
                    fieldDisplayMap.put(cfKey, String.join(", ", labels));
                    if (CustomFieldOptionService.isEnumLikeFormat(fieldDef.getFieldFormat())) {
                        for (String v : values) {
                            String color = resolveOptionColor(v, optionColorMap);
                            if (color != null) {
                                fieldColorMap.put(cfKey, color);
                                break;
                            }
                        }
                    }
                } else {
                    String displayValue = resolveDisplayValue(values.get(0), fieldDef, optionTextMap, userNameMap);
                    fieldDisplayMap.put(cfKey, displayValue);
                    if (CustomFieldOptionService.isEnumLikeFormat(fieldDef.getFieldFormat())) {
                        String color = resolveOptionColor(values.get(0), optionColorMap);
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
     * 批量获取多个 Issue 的自定义字段结构化详情（含 values/displayValues/colors 数组）。
     * 每个 Issue 返回 List<CustomFieldValueVO>，前端可直接用于独立渲染多值标签。
     *
     * @param issueIds 工单 ID 列表
     * @return Map<issueId, List<CustomFieldValueVO>>
     */
    @Transactional(readOnly = true)
    public Map<Long, List<CustomFieldValueVO>> getBatchCustomFieldDetails(List<Long> issueIds) {
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

        // 3. 构建字段适用性过滤
        Set<Long> nonGlobalFieldIds = fieldDefMap.values().stream()
                .filter(f -> !Boolean.TRUE.equals(f.getIsForAll()))
                .map(CustomFieldDefinition::getId)
                .collect(Collectors.toSet());

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

        // 查询 issue→projectId 映射
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getId, Issue::getProjectId)
                        .in(Issue::getId, issueIds));
        Map<Long, Long> issueProjectMap = issues.stream()
                .collect(Collectors.toMap(Issue::getId, Issue::getProjectId));

        // 4. 过滤不适用的字段值
        List<CustomFieldValue> applicableValues = allValues.stream()
                .filter(v -> {
                    CustomFieldDefinition fieldDef = fieldDefMap.get(v.getCustomFieldId());
                    if (fieldDef == null) return false;
                    if (Boolean.TRUE.equals(fieldDef.getIsForAll())) return true;
                    Long projectId = issueProjectMap.get(v.getIssueId());
                    if (projectId == null) return false;
                    Set<Long> allowedProjects = fieldToProjectIds.get(v.getCustomFieldId());
                    return allowedProjects != null && allowedProjects.contains(projectId);
                })
                .toList();

        if (applicableValues.isEmpty()) {
            return new HashMap<>();
        }

        // 4.5 应用 visibleToRoles 过滤
        applicableValues = applyVisibilityFilter(applicableValues, issueProjectMap);
        if (applicableValues.isEmpty()) {
            return new HashMap<>();
        }

        // 5. 预加载 list 类型字段的选项映射
        Set<Long> listFieldIds = fieldDefMap.values().stream()
                .filter(f -> CustomFieldOptionService.isEnumLikeFormat(f.getFieldFormat()))
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

        // 7. 按 (issueId, fieldId) 分组
        Map<Long, Map<Long, List<String>>> groupedByIssueAndField = new HashMap<>();
        for (CustomFieldValue cfv : applicableValues) {
            groupedByIssueAndField
                    .computeIfAbsent(cfv.getIssueId(), k -> new HashMap<>())
                    .computeIfAbsent(cfv.getCustomFieldId(), k -> new ArrayList<>())
                    .add(cfv.getValue());
        }

        // 8. 组装 CustomFieldValueVO 结构
        Map<Long, List<CustomFieldValueVO>> result = new HashMap<>();
        for (Map.Entry<Long, Map<Long, List<String>>> issueEntry : groupedByIssueAndField.entrySet()) {
            Long issueId = issueEntry.getKey();
            List<CustomFieldValueVO> voList = new ArrayList<>();

            for (Map.Entry<Long, List<String>> fieldEntry : issueEntry.getValue().entrySet()) {
                Long fieldId = fieldEntry.getKey();
                List<String> values = fieldEntry.getValue();
                CustomFieldDefinition fieldDef = fieldDefMap.get(fieldId);
                if (fieldDef == null) continue;

                boolean isMulti = Boolean.TRUE.equals(fieldDef.getIsMulti());

                CustomFieldValueVO vo = new CustomFieldValueVO();
                vo.setCustomFieldId(String.valueOf(fieldId));
                vo.setFieldName(fieldDef.getName());
                vo.setFieldFormat(fieldDef.getFieldFormat());
                vo.setIsMulti(isMulti);

                if (isMulti) {
                    vo.setValues(values);
                    List<String> displayValues = new ArrayList<>();
                    List<String> colors = new ArrayList<>();
                    boolean hasAnyColor = false;
                    for (String v : values) {
                        String display = resolveDisplayValue(v, fieldDef, optionTextMap, userNameMap);
                        displayValues.add(display != null ? display : "");
                        String color = resolveOptionColor(v, optionColorMap);
                        colors.add(color);
                        if (color != null) hasAnyColor = true;
                    }
                    vo.setDisplayValues(displayValues);
                    if (hasAnyColor) {
                        vo.setColors(colors);
                    }
                    vo.setValue(String.join(",", values));
                    vo.setDisplayValue(String.join(", ", displayValues));
                } else {
                    String singleValue = values.get(0);
                    vo.setValue(singleValue);
                    String display = resolveDisplayValue(singleValue, fieldDef, optionTextMap, userNameMap);
                    vo.setDisplayValue(display != null ? display : "");
                    String color = resolveOptionColor(singleValue, optionColorMap);
                    vo.setColor(color);
                }
                voList.add(vo);
            }

            result.put(issueId, voList);
        }

        return result;
    }

    /**
     * 获取 Issue 的自定义字段值（含字段名称、类型信息，用于前端展示）
     */
    @Transactional(readOnly = true)
    public List<CustomFieldValueVO> getValuesForDisplay(Long issueId, Long projectId, String issueType,
                                                         List<CustomFieldDefinition> applicableFields) {
        List<CustomFieldValue> allValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getIssueId, issueId));
        if (allValues.isEmpty()) return List.of();

        Map<Long, CustomFieldDefinition> fieldMap = applicableFields.stream()
                .collect(Collectors.toMap(CustomFieldDefinition::getId, f -> f));

        // 按 fieldId 分组
        Map<Long, List<String>> grouped = allValues.stream()
                .collect(Collectors.groupingBy(
                        CustomFieldValue::getCustomFieldId,
                        Collectors.mapping(CustomFieldValue::getValue, Collectors.toList())));

        // 批量预加载 list 类型字段的选项映射
        Set<Long> listFieldIds = fieldMap.values().stream()
                .filter(f -> CustomFieldOptionService.isEnumLikeFormat(f.getFieldFormat()))
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

        // 组装结果
        List<CustomFieldValueVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<String>> entry : grouped.entrySet()) {
            CustomFieldDefinition field = fieldMap.get(entry.getKey());
            if (field == null) continue;

            boolean isMulti = Boolean.TRUE.equals(field.getIsMulti());
            List<String> values = entry.getValue();

            CustomFieldValueVO vo = new CustomFieldValueVO();
            vo.setCustomFieldId(String.valueOf(entry.getKey()));
            vo.setFieldName(field.getName());
            vo.setFieldFormat(field.getFieldFormat());
            vo.setIsMulti(isMulti);

            if (isMulti) {
                vo.setValues(values);
                List<String> displayValues = new ArrayList<>();
                List<String> colors = new ArrayList<>();
                boolean hasAnyColor = false;
                for (String v : values) {
                    String display = resolveDisplayValue(v, field, optionTextMap, userNameMap);
                    displayValues.add(display != null ? display : "");
                    String color = resolveOptionColor(v, optionColorMap);
                    colors.add(color);
                    if (color != null) hasAnyColor = true;
                }
                vo.setDisplayValues(displayValues);
                if (hasAnyColor) {
                    vo.setColors(colors);
                }
                vo.setValue(String.join(",", values));
                vo.setDisplayValue(String.join(", ", displayValues));
            } else {
                String singleValue = values.get(0);
                vo.setValue(singleValue);
                String display = resolveDisplayValue(singleValue, field, optionTextMap, userNameMap);
                vo.setDisplayValue(display != null ? display : "");
                String color = resolveOptionColor(singleValue, optionColorMap);
                vo.setColor(color);
            }
            result.add(vo);
        }
        return result;
    }

    // ========== 展示值解析方法 ==========

    /**
     * 将原始值转换为用户可读的展示值（批量版，使用预加载 Map）
     */
    public String resolveDisplayValue(String rawValue, CustomFieldDefinition fieldDef,
                                       Map<Long, String> optionTextMap, Map<Long, String> userNameMap) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return switch (fieldDef.getFieldFormat()) {
            case "list", "state", "ownedField" -> {
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
            case "period" -> {
                try {
                    long minutes = Long.parseLong(rawValue);
                    yield CustomFieldValidationEngine.formatMinutesToPeriod(minutes);
                } catch (NumberFormatException e) {
                    yield rawValue;
                }
            }
            case "string", "text", "int", "float", "date", "datetime" -> rawValue;
            default -> {
                log.warn("Unknown field_format '{}' for field '{}' (id={}), returning raw value",
                        fieldDef.getFieldFormat(), fieldDef.getName(), fieldDef.getId());
                yield rawValue;
            }
        };
    }

    /**
     * 将存储值转为前端可读展示值（单次查询版，用于活动日志）
     */
    public String resolveDisplayValue(CustomFieldDefinition field, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return "";
        switch (field.getFieldFormat()) {
            case "list", "state", "ownedField" -> {
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
            case "period" -> {
                try {
                    long minutes = Long.parseLong(rawValue);
                    return CustomFieldValidationEngine.formatMinutesToPeriod(minutes);
                } catch (NumberFormatException e) {
                    return rawValue;
                }
            }
            case "string", "text", "int", "float", "date", "datetime" -> { return rawValue; }
            default -> {
                log.warn("Unknown field_format '{}' for field '{}' (id={}), returning raw value",
                        field.getFieldFormat(), field.getName(), field.getId());
                return rawValue;
            }
        }
    }

    /**
     * 解析多值展示值（用于活动日志）
     */
    public String resolveMultiDisplayValue(CustomFieldDefinition field, List<String> values) {
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
     * 解析选项颜色
     */
    public String resolveOptionColor(String rawValue, Map<Long, String> optionColorMap) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            Long optionId = Long.parseLong(rawValue);
            return optionColorMap.get(optionId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ========== 可见性过滤辅助方法 ==========

    /**
     * 应用角色可见性过滤（含私有字段权限检查）
     */
    private List<CustomFieldValue> applyVisibilityFilter(
            List<CustomFieldValue> applicableValues,
            Map<Long, Long> issueProjectMap) {

        Set<Long> projectIds = new HashSet<>(issueProjectMap.values());
        Map<Long, Map<Long, CustomFieldProject>> projectConditionsMap = new HashMap<>();
        if (!projectIds.isEmpty()) {
            List<CustomFieldProject> allMappings = projectMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldProject>()
                            .in(CustomFieldProject::getProjectId, projectIds));
            for (CustomFieldProject cfp : allMappings) {
                projectConditionsMap
                        .computeIfAbsent(cfp.getProjectId(), k -> new HashMap<>())
                        .put(cfp.getCustomFieldId(), cfp);
            }
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isSystemAdmin = currentUserId != null &&
                permissionService.hasGlobalPermission(currentUserId, "system:admin");

        if (isSystemAdmin) {
            return applicableValues;
        }

        // Check private field read permission per project (cache the result)
        Map<Long, Boolean> projectPrivateReadCache = new HashMap<>();

        // Collect field IDs that are private
        Set<Long> fieldIds = applicableValues.stream()
                .map(CustomFieldValue::getCustomFieldId)
                .collect(Collectors.toSet());
        Map<Long, CustomFieldDefinition> fieldDefMap = new HashMap<>();
        if (!fieldIds.isEmpty()) {
            List<CustomFieldDefinition> defs = definitionMapper.selectBatchIds(fieldIds);
            for (CustomFieldDefinition def : defs) {
                fieldDefMap.put(def.getId(), def);
            }
        }

        Map<Long, List<Long>> userRolesPerProject = new HashMap<>();
        if (currentUserId != null) {
            for (Long pid : projectIds) {
                userRolesPerProject.put(pid, projectMemberMapper.selectRoleIdsByUserAndProject(currentUserId, pid));
            }
        }

        return applicableValues.stream()
                .filter(v -> {
                    // Private field check
                    CustomFieldDefinition fieldDef = fieldDefMap.get(v.getCustomFieldId());
                    if (fieldDef != null && Boolean.TRUE.equals(fieldDef.getIsPrivate())) {
                        Long projectId = issueProjectMap.get(v.getIssueId());
                        boolean canReadPrivate = projectPrivateReadCache.computeIfAbsent(
                                projectId != null ? projectId : -1L,
                                pid -> currentUserId != null &&
                                        permissionService.hasPermission(currentUserId, pid > 0 ? pid : null, "issue:read_private_fields"));
                        if (!canReadPrivate) {
                            return false;
                        }
                    }

                    // visibleToRoles check
                    Long projectId = issueProjectMap.get(v.getIssueId());
                    if (projectId == null) return true;
                    Map<Long, CustomFieldProject> conditions = projectConditionsMap.get(projectId);
                    if (conditions == null) return true;
                    CustomFieldProject mapping = conditions.get(v.getCustomFieldId());
                    List<Long> visibleRoles = mapping != null ? parseRoleIds(mapping.getVisibleToRoles()) : null;
                    List<Long> userRoles = userRolesPerProject.getOrDefault(projectId, List.of());
                    return isVisibleToUser(visibleRoles, userRoles);
                })
                .toList();
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

    private boolean isVisibleToUser(List<Long> restrictedRoles, List<Long> userRoleIds) {
        if (userRoleIds == null) return true;
        if (restrictedRoles == null || restrictedRoles.isEmpty()) return true;
        for (Long roleId : userRoleIds) {
            if (restrictedRoles.contains(roleId)) return true;
        }
        return false;
    }
}
