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
}
