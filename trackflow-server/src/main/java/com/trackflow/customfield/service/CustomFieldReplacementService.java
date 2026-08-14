package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.customfield.entity.*;
import com.trackflow.customfield.mapper.*;
import com.trackflow.customfield.vo.ReplaceResultVO;
import com.trackflow.customfield.vo.ReplaceableFieldsVO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义字段替换服务
 * <p>
 * 实现 YouTrack 的 "Replace Custom Field" 功能：用另一个相同类型的字段替换当前字段。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldReplacementService {

    private final CustomFieldDefinitionMapper definitionMapper;
    private final CustomFieldValueMapper valueMapper;
    private final CustomFieldOptionMapper optionMapper;
    private final CustomFieldProjectMapper projectFieldMapper;
    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper;

    /**
     * 获取可用于替换指定字段的字段列表
     *
     * @param projectId 项目 ID
     * @param fieldId   当前字段 ID
     * @return 可替换字段列表
     */
    public ReplaceableFieldsVO getAvailableReplacements(Long projectId, Long fieldId) {
        CustomFieldDefinition currentField = definitionMapper.selectById(fieldId);
        if (currentField == null) {
            throw BusinessException.notFound("自定义字段不存在");
        }

        ReplaceableFieldsVO vo = new ReplaceableFieldsVO();
        vo.setCurrentFieldId(String.valueOf(fieldId));
        vo.setCurrentFieldName(currentField.getName());
        vo.setCurrentFieldFormat(currentField.getFieldFormat());

        // 查找相同类型的其他字段
        List<CustomFieldDefinition> sameTypeFields = definitionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldDefinition>()
                        .eq(CustomFieldDefinition::getFieldFormat, currentField.getFieldFormat())
                        .ne(CustomFieldDefinition::getId, fieldId)
                        .orderByAsc(CustomFieldDefinition::getPosition));

        // 如果是 list 类型，还需要匹配 isMulti 属性
        if (CustomFieldOptionService.isEnumLikeFormat(currentField.getFieldFormat())) {
            boolean isMulti = Boolean.TRUE.equals(currentField.getIsMulti());
            sameTypeFields = sameTypeFields.stream()
                    .filter(f -> Boolean.TRUE.equals(f.getIsMulti()) == isMulti)
                    .collect(Collectors.toList());
        }

        List<ReplaceableFieldsVO.ReplaceableField> available = new ArrayList<>();
        for (CustomFieldDefinition field : sameTypeFields) {
            ReplaceableFieldsVO.ReplaceableField rf = new ReplaceableFieldsVO.ReplaceableField();
            rf.setId(String.valueOf(field.getId()));
            rf.setName(field.getName());
            rf.setFieldFormat(field.getFieldFormat());

            // 统计项目数量
            long projectCount;
            if (Boolean.TRUE.equals(field.getIsForAll())) {
                projectCount = projectMapper.selectCount(null);
            } else {
                projectCount = projectFieldMapper.selectCount(
                        new LambdaQueryWrapper<CustomFieldProject>()
                                .eq(CustomFieldProject::getCustomFieldId, field.getId()));
            }
            rf.setProjectCount((int) projectCount);

            // 统计选项数量
            if (CustomFieldOptionService.isEnumLikeFormat(field.getFieldFormat())) {
                long optionCount = optionMapper.selectCount(
                        new LambdaQueryWrapper<CustomFieldOption>()
                                .eq(CustomFieldOption::getCustomFieldId, field.getId()));
                rf.setOptionCount((int) optionCount);
            }

            available.add(rf);
        }

        vo.setAvailableFields(available);
        return vo;
    }

    /**
     * 在项目中用目标字段替换源字段
     *
     * @param projectId     项目 ID
     * @param sourceFieldId 要被替换的字段 ID
     * @param targetFieldId 替换后使用的字段 ID
     * @return 替换结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ReplaceResultVO replaceField(Long projectId, Long sourceFieldId, Long targetFieldId) {
        // 验证字段存在
        CustomFieldDefinition sourceField = definitionMapper.selectById(sourceFieldId);
        if (sourceField == null) {
            throw BusinessException.notFound("源字段不存在");
        }

        CustomFieldDefinition targetField = definitionMapper.selectById(targetFieldId);
        if (targetField == null) {
            throw BusinessException.notFound("目标字段不存在");
        }

        // 验证项目存在
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BusinessException.notFound("项目不存在");
        }

        // 验证字段类型相同
        if (!sourceField.getFieldFormat().equals(targetField.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    String.format("字段类型不匹配：源字段为 %s，目标字段为 %s",
                            sourceField.getFieldFormat(), targetField.getFieldFormat()));
        }

        // 如果是 list 类型，验证 isMulti 一致
        if (CustomFieldOptionService.isEnumLikeFormat(sourceField.getFieldFormat())) {
            boolean sourceIsMulti = Boolean.TRUE.equals(sourceField.getIsMulti());
            boolean targetIsMulti = Boolean.TRUE.equals(targetField.getIsMulti());
            if (sourceIsMulti != targetIsMulti) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        String.format("字段多选属性不匹配：源字段为 %s，目标字段为 %s",
                                sourceIsMulti ? "多选" : "单选",
                                targetIsMulti ? "多选" : "单选"));
            }
        }

        // 获取项目中的所有 issue ID
        List<Issue> projectIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .select(Issue::getId));
        Set<Long> issueIds = projectIssues.stream().map(Issue::getId).collect(Collectors.toSet());

        int mergedOptionCount = 0;

        // 如果是 list 类型，需要合并选项
        if (CustomFieldOptionService.isEnumLikeFormat(sourceField.getFieldFormat())) {
            mergedOptionCount = mergeOptions(sourceFieldId, targetFieldId, issueIds);
        }

        // 迁移值：将源字段的值复制到目标字段
        int migratedCount = migrateValues(sourceFieldId, targetFieldId, issueIds);

        // 更新项目-字段关联
        updateProjectFieldAssociation(projectId, sourceFieldId, targetFieldId);

        log.info("字段替换完成: projectId={}, sourceField={} -> targetField={}, 迁移 {} 条值, 合并 {} 个选项",
                projectId, sourceField.getName(), targetField.getName(), migratedCount, mergedOptionCount);

        ReplaceResultVO result = new ReplaceResultVO();
        result.setProjectId(String.valueOf(projectId));
        result.setProjectName(project.getName());
        result.setSourceFieldId(String.valueOf(sourceFieldId));
        result.setSourceFieldName(sourceField.getName());
        result.setTargetFieldId(String.valueOf(targetFieldId));
        result.setTargetFieldName(targetField.getName());
        result.setMigratedIssueCount(migratedCount);
        result.setMergedOptionCount(mergedOptionCount);
        result.setMessage(String.format("成功将「%s」替换为「%s」", sourceField.getName(), targetField.getName()));

        return result;
    }

    /**
     * 合并 list 类型字段的选项
     *
     * @return 合并的选项数量
     */
    private int mergeOptions(Long sourceFieldId, Long targetFieldId, Set<Long> issueIds) {
        // 获取源字段的选项
        List<CustomFieldOption> sourceOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, sourceFieldId));

        // 获取目标字段已有的选项值
        List<CustomFieldOption> targetOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, targetFieldId));
        Set<String> existingValues = targetOptions.stream()
                .map(o -> o.getValue().toLowerCase())
                .collect(Collectors.toSet());

        // 获取目标字段的最大 position
        int maxPosition = targetOptions.stream()
                .mapToInt(CustomFieldOption::getPosition)
                .max()
                .orElse(-1);

        // 构建源选项 ID 到目标选项 ID 的映射
        Map<Long, Long> optionIdMapping = new HashMap<>();

        // 先匹配同名选项
        Map<String, Long> targetOptionByValue = targetOptions.stream()
                .collect(Collectors.toMap(
                        o -> o.getValue().toLowerCase(),
                        CustomFieldOption::getId,
                        (a, b) -> a));

        int mergedCount = 0;
        for (CustomFieldOption sourceOpt : sourceOptions) {
            String valueLower = sourceOpt.getValue().toLowerCase();
            if (targetOptionByValue.containsKey(valueLower)) {
                // 同名选项已存在，直接映射
                optionIdMapping.put(sourceOpt.getId(), targetOptionByValue.get(valueLower));
            } else {
                // 创建新选项
                CustomFieldOption newOption = new CustomFieldOption();
                newOption.setCustomFieldId(targetFieldId);
                newOption.setValue(sourceOpt.getValue());
                newOption.setPosition(++maxPosition);
                newOption.setIsDefault(false);
                newOption.setColor(sourceOpt.getColor());
                newOption.setDescription(sourceOpt.getDescription());
                optionMapper.insert(newOption);

                optionIdMapping.put(sourceOpt.getId(), newOption.getId());
                mergedCount++;
            }
        }

        // 更新项目中 issue 的值：将源选项 ID 替换为目标选项 ID
        if (!optionIdMapping.isEmpty() && !issueIds.isEmpty()) {
            List<CustomFieldValue> values = valueMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldValue>()
                            .eq(CustomFieldValue::getCustomFieldId, sourceFieldId)
                            .in(CustomFieldValue::getIssueId, issueIds));

            for (CustomFieldValue cfv : values) {
                if (cfv.getValue() != null && !cfv.getValue().isBlank()) {
                    try {
                        Long oldOptionId = Long.parseLong(cfv.getValue());
                        Long newOptionId = optionIdMapping.get(oldOptionId);
                        if (newOptionId != null) {
                            cfv.setValue(String.valueOf(newOptionId));
                            valueMapper.updateById(cfv);
                        }
                    } catch (NumberFormatException e) {
                        // 值不是选项 ID，跳过
                    }
                }
            }
        }

        return mergedCount;
    }

    /**
     * 迁移值
     *
     * @return 迁移的值数量
     */
    private int migrateValues(Long sourceFieldId, Long targetFieldId, Set<Long> issueIds) {
        if (issueIds.isEmpty()) {
            return 0;
        }

        // 获取源字段在这些 issue 中的值
        List<CustomFieldValue> sourceValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getCustomFieldId, sourceFieldId)
                        .in(CustomFieldValue::getIssueId, issueIds));

        // 获取目标字段在这些 issue 中已有的值
        List<CustomFieldValue> targetValues = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getCustomFieldId, targetFieldId)
                        .in(CustomFieldValue::getIssueId, issueIds));
        Set<Long> issuesWithTargetValue = targetValues.stream()
                .map(CustomFieldValue::getIssueId)
                .collect(Collectors.toSet());

        int migrated = 0;
        for (CustomFieldValue srcValue : sourceValues) {
            if (issuesWithTargetValue.contains(srcValue.getIssueId())) {
                // 目标字段已有值，跳过（不覆盖）
                continue;
            }

            // 将源值改为目标字段的值
            srcValue.setCustomFieldId(targetFieldId);
            valueMapper.updateById(srcValue);
            migrated++;
        }

        return migrated;
    }

    /**
     * 更新项目-字段关联
     */
    private void updateProjectFieldAssociation(Long projectId, Long sourceFieldId, Long targetFieldId) {
        // 检查源字段是否关联到项目
        CustomFieldProject sourceAssoc = projectFieldMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .eq(CustomFieldProject::getCustomFieldId, sourceFieldId));

        // 检查目标字段是否已关联到项目
        CustomFieldProject targetAssoc = projectFieldMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .eq(CustomFieldProject::getCustomFieldId, targetFieldId));

        // 如果源字段关联存在，删除它
        if (sourceAssoc != null) {
            projectFieldMapper.deleteById(sourceAssoc.getId());
        }

        // 如果目标字段没有关联，创建关联
        if (targetAssoc == null) {
            CustomFieldDefinition targetField = definitionMapper.selectById(targetFieldId);
            if (targetField != null && !Boolean.TRUE.equals(targetField.getIsForAll())) {
                CustomFieldProject newAssoc = new CustomFieldProject();
                newAssoc.setProjectId(projectId);
                newAssoc.setCustomFieldId(targetFieldId);
                if (sourceAssoc != null) {
                    // 继承源字段的位置
                    newAssoc.setPosition(sourceAssoc.getPosition());
                }
                projectFieldMapper.insert(newAssoc);
            }
        }
    }
}
